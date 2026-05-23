package com.oms.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.dto.*;
import com.oms.common.enums.OrderStatus;
import com.oms.common.enums.SagaStatus;
import com.oms.orchestrator.config.RabbitMQConfig;
import com.oms.orchestrator.entity.SagaInstance;
import com.oms.orchestrator.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.oms.common.dto.PaymentUrlCreatedEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaStateMachineManager {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;


    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORCHESTRATOR_ORDER_CREATED)
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[ORCHESTRATOR] Bắt đầu luồng Saga cho đơn hàng: {}", event.getOrderId());

        if (sagaInstanceRepository.findByOrderId(event.getOrderId()).isPresent()) {
            log.warn("[ORCHESTRATOR] Saga cho đơn hàng {} đã tồn tại. Bỏ qua.", event.getOrderId());
            return;
        }

        String payloadJson = "";
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("[ORCHESTRATOR] Lỗi khi chuyển đổi dữ liệu event sang JSON", e);
        }

        SagaInstance saga = SagaInstance.builder()
                .orderId(event.getOrderId())
                .currentStep(SagaStatus.STARTED)
                .payload(payloadJson)
                .build();
        sagaInstanceRepository.save(saga);

        // Tạo FraudCheckCommand để gửi sang AI Service
        List<FraudCheckCommand.OrderItemDto> orderItems = event.getItems().stream()
                .map(item -> FraudCheckCommand.OrderItemDto.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        FraudCheckCommand fraudCmd = FraudCheckCommand.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .totalAmount(event.getTotalAmount())
                .receiverName(event.getReceiverName())
                .receiverPhone(event.getReceiverPhone())
                .address(event.getAddress())
                .paymentMethod(event.getPaymentMethod())
                .items(orderItems)
                .build();

        // Bước 1: Gửi lệnh kiểm tra gian lận bằng AI (AI Fraud Check)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.AI_COMMAND_CHECK_FRAUD, fraudCmd);
                log.info("[ORCHESTRATOR] Đã gửi lệnh kiểm tra gian lận bằng AI (AI_CHECK_FRAUD) cho đơn hàng: {}", event.getOrderId());
            }
        });
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORCHESTRATOR_AI_REPLY)
    @Transactional
    public void handleAiFraudReply(FraudCheckReply reply) {
        log.info("[ORCHESTRATOR] Nhận phản hồi kiểm tra gian lận từ AI cho đơn hàng: {}, Trạng thái: {}, Điểm rủi ro: {}", 
                reply.getOrderId(), reply.getStatus(), reply.getFraudScore());

        SagaInstance saga = sagaInstanceRepository.findByOrderId(reply.getOrderId())
                .orElse(null);

        if (saga == null) return;

        // Idempotency: Chỉ xử lý khi Saga đang ở trạng thái STARTED
        if (saga.getCurrentStep() != SagaStatus.STARTED) {
            log.warn("[ORCHESTRATOR] Đơn hàng {} đang ở bước {}. Bỏ qua phản hồi từ AI.", 
                    saga.getOrderId(), saga.getCurrentStep());
            return;
        }

        if ("SAFE".equalsIgnoreCase(reply.getStatus())) {
            log.info("[ORCHESTRATOR] Đơn hàng {} AN TOÀN. Tiến hành gửi lệnh giữ hàng (RESERVE) trong kho.", reply.getOrderId());
            
            try {
                OrderCreatedEvent originalEvent = objectMapper.readValue(saga.getPayload(), OrderCreatedEvent.class);
                
                // Gửi lệnh giữ hàng (RESERVE) cho Inventory Service
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        originalEvent.getItems().forEach(item -> {
                            InventoryCommand command = InventoryCommand.builder()
                                    .orderId(originalEvent.getOrderId())
                                    .productId(item.getProductId())
                                    .quantity(item.getQuantity())
                                    .type("RESERVE")
                                    .build();
                            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_RESERVE, command);
                        });
                        log.info("[ORCHESTRATOR] Đã gửi lệnh giữ hàng (RESERVE) cho đơn hàng: {}", originalEvent.getOrderId());
                    }
                });
            } catch (JsonProcessingException e) {
                log.error("[ORCHESTRATOR] Lỗi khi giải mã payload ban đầu cho đơn hàng: {}", saga.getOrderId(), e);
            }
        } else {
            log.error("[ORCHESTRATOR] Đơn hàng {} NGUY HIỂM (Gian lận). Lý do: {}. Tiến hành hủy đơn ngay lập tức.", 
                    reply.getOrderId(), reply.getReason());
            
            saga.setCurrentStep(SagaStatus.FAILED);
            sagaInstanceRepository.save(saga);

            // Gửi lệnh hủy đơn về Order Service với lý do chi tiết bằng ngôn ngữ tự nhiên
            OrderStatusUpdateCommand updateCmd = OrderStatusUpdateCommand.builder()
                    .orderId(saga.getOrderId())
                    .newStatus(OrderStatus.CANCELLED)
                    .message("Hủy tự động bởi hệ thống AI: " + reply.getReason())
                    .errorMessage("Hủy tự động bởi hệ thống AI: " + reply.getReason())
                    .build();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_COMMAND_UPDATE, updateCmd);
                    log.info("[ORCHESTRATOR] Đã gửi lệnh hủy đơn hàng (Gian lận) sang Order Service: {}", saga.getOrderId());
                }
            });
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORCHESTRATOR_INVENTORY_REPLY)
    @Transactional
    public void handleInventoryReply(InventoryResultPayload payload) {
        log.info("[ORCHESTRATOR] Nhận phản hồi từ Kho cho đơn hàng: {}, Trạng thái: {}", payload.getOrderId(), payload.getStatus());

        SagaInstance saga = sagaInstanceRepository.findByOrderId(payload.getOrderId())
                .orElse(null);

        if (saga == null) return;

        // Kiểm tra Idempotency: Nếu đã qua bước này rồi thì bỏ qua
        if (saga.getCurrentStep() != SagaStatus.STARTED) {
            log.warn("[ORCHESTRATOR] Đơn hàng {} đang ở bước {}. Bỏ qua phản hồi từ Kho.", 
                    saga.getOrderId(), saga.getCurrentStep());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(payload.getStatus())) {
            log.info("[ORCHESTRATOR] Giữ hàng THÀNH CÔNG cho đơn hàng: {}.", payload.getOrderId());
            
            try {
                OrderCreatedEvent originalEvent = objectMapper.readValue(saga.getPayload(), OrderCreatedEvent.class);
                String paymentMethod = originalEvent.getPaymentMethod();

                if ("COD".equalsIgnoreCase(paymentMethod)) {
                    log.info("[ORCHESTRATOR] Đơn hàng {} thanh toán COD. Bỏ qua bước Thanh toán, tiến hành Hoàn tất.", saga.getOrderId());
                    saga.setCurrentStep(SagaStatus.PAYMENT_PROCESSED);
                    sagaInstanceRepository.save(saga);
                    
                    // Kích hoạt luồng hoàn tất (Confirm Inventory, Delivery, etc.)
                    triggerSagaCompletion(saga, originalEvent, "COD_CONFIRMATION");
                } else {
                    log.info("[ORCHESTRATOR] Đơn hàng {} thanh toán VNPAY. Gửi lệnh khởi tạo thanh toán.", saga.getOrderId());
                    saga.setCurrentStep(SagaStatus.INVENTORY_RESERVED);
                    sagaInstanceRepository.save(saga);

                    PaymentCommand paymentCmd = PaymentCommand.builder()
                            .orderId(saga.getOrderId())
                            .userId(originalEvent.getUserId())
                            .amount(originalEvent.getTotalAmount())
                            .description("Thanh toán đơn hàng: " + saga.getOrderId())
                            .build();

                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.PAYMENT_COMMAND_CREATE, paymentCmd);
                            log.info("[ORCHESTRATOR] Đã gửi lệnh thanh toán cho đơn hàng: {}", saga.getOrderId());
                        }
                    });
                }
            } catch (JsonProcessingException e) {
                log.error("[ORCHESTRATOR] Lỗi khi xử lý payload Saga cho đơn hàng: {}", saga.getOrderId(), e);
            }
        } else {
            log.error("[ORCHESTRATOR] Giữ hàng THẤT BẠI cho đơn hàng: {}. Tiến hành hủy đơn.", payload.getOrderId());
            saga.setCurrentStep(SagaStatus.FAILED);
            sagaInstanceRepository.save(saga);

            // Thông báo cho Order Service để HỦY đơn
            OrderStatusUpdateCommand updateCmd = OrderStatusUpdateCommand.builder()
                    .orderId(saga.getOrderId())
                    .newStatus(OrderStatus.CANCELLED)
                    .message("Kho báo lỗi: " + payload.getMessage())
                    .build();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_COMMAND_UPDATE, updateCmd);
                    log.info("[ORCHESTRATOR] Đã gửi lệnh hủy đơn hàng sang Order Service: {}", saga.getOrderId());
                }
            });
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORCHESTRATOR_PAYMENT_REPLY)
    @Transactional
    public void handlePaymentReply(PaymentResultPayload payload) {
        log.info("[ORCHESTRATOR] Nhận phản hồi Thanh toán cho đơn hàng: {}, Trạng thái: {}", payload.getOrderId(), payload.getPaymentStatus());

        SagaInstance saga = sagaInstanceRepository.findByOrderId(payload.getOrderId())
                .orElse(null);

        if (saga == null) return;

        // Chấp nhận bước INVENTORY_RESERVED (VNPAY vừa gửi URL xong) hoặc WAITING_FOR_PAYMENT
        if (saga.getCurrentStep() != SagaStatus.INVENTORY_RESERVED && saga.getCurrentStep() != SagaStatus.WAITING_FOR_PAYMENT) {
            log.warn("[ORCHESTRATOR] Đơn hàng {} đang ở bước {}. Bỏ qua phản hồi Thanh toán.", 
                    saga.getOrderId(), saga.getCurrentStep());
            return;
        }

        if ("COMPLETED".equalsIgnoreCase(payload.getPaymentStatus())) {
            log.info("[ORCHESTRATOR] Thanh toán THÀNH CÔNG cho đơn hàng: {}. Tiến hành GIAO HÀNG & CHỐT KHO.", payload.getOrderId());
            saga.setCurrentStep(SagaStatus.PAYMENT_PROCESSED);
            sagaInstanceRepository.save(saga);

            try {
                OrderCreatedEvent originalEvent = objectMapper.readValue(saga.getPayload(), OrderCreatedEvent.class);
                triggerSagaCompletion(saga, originalEvent, payload.getTransactionId());
            } catch (JsonProcessingException e) {
                log.error("[ORCHESTRATOR] Lỗi khi giải mã dữ liệu payload", e);
            }
        } else {
            log.error("[ORCHESTRATOR] Thanh toán THẤT BẠI cho đơn hàng: {}. Tiến hành HOÀN KHO.", payload.getOrderId());
            saga.setCurrentStep(SagaStatus.ROLLED_BACK);
            sagaInstanceRepository.save(saga);

            try {
                OrderCreatedEvent originalEvent = objectMapper.readValue(saga.getPayload(), OrderCreatedEvent.class);
                
                // Hoàn kho (Rollback Inventory)
                List<InventoryCommand> rollbackCommands = originalEvent.getItems().stream()
                        .map(item -> InventoryCommand.builder()
                                .orderId(saga.getOrderId())
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .type("ROLLBACK")
                                .build())
                        .toList();

                // Cập nhật trạng thái đơn hàng thành CANCELLED
                OrderStatusUpdateCommand updateCmd = OrderStatusUpdateCommand.builder()
                        .orderId(saga.getOrderId())
                        .newStatus(OrderStatus.CANCELLED)
                        .paymentId(payload.getTransactionId())
                        .errorMessage("Thanh toán thất bại: " + payload.getMessage())
                        .message("Thanh toán thất bại.")
                        .build();

                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        rollbackCommands.forEach(cmd -> 
                            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_ROLLBACK, cmd));
                        
                        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_COMMAND_UPDATE, updateCmd);
                        
                        log.info("[ORCHESTRATOR] Đã gửi các lệnh rollback cho đơn hàng: {}", saga.getOrderId());
                    }
                });

            } catch (JsonProcessingException e) {
                log.error("[ORCHESTRATOR] Lỗi khi giải mã dữ liệu payload", e);
            }
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORCHESTRATOR_PAYMENT_URL)
    @Transactional
    public void handlePaymentUrlCreated(PaymentUrlCreatedEvent event) {
        log.info("[ORCHESTRATOR] Nhận URL thanh toán cho đơn hàng: {}", event.getOrderId());

        SagaInstance saga = sagaInstanceRepository.findByOrderId(event.getOrderId())
                .orElse(null);

        if (saga == null) return;

        // Chuyển trạng thái sang chờ thanh toán
        saga.setCurrentStep(SagaStatus.WAITING_FOR_PAYMENT);
        sagaInstanceRepository.save(saga);

        // Gửi lệnh cập nhật URL về cho Order Service
        OrderStatusUpdateCommand updateCmd = OrderStatusUpdateCommand.builder()
                .orderId(saga.getOrderId())
                .newStatus(OrderStatus.PAYMENT_PENDING)
                .paymentUrl(event.getPaymentUrl())
                .message("Đã tạo URL thanh toán VNPay.")
                .build();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_COMMAND_UPDATE, updateCmd);
                log.info("[ORCHESTRATOR] Đã gửi lệnh cập nhật paymentUrl sang Order Service cho đơn hàng: {}", saga.getOrderId());
            }
        });
    }

    /**
     * Helper logic to trigger inventory confirmation and order confirmation
     */
    private void triggerSagaCompletion(SagaInstance saga, OrderCreatedEvent originalEvent, String transactionId) {
        // Bước 3a: Chốt kho (Confirm Inventory)
        List<InventoryCommand> inventoryCommands = originalEvent.getItems().stream()
                .map(item -> InventoryCommand.builder()
                        .orderId(saga.getOrderId())
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .type("CONFIRM")
                        .build())
                .toList();

        // Cập nhật trạng thái đơn hàng sang CONFIRMED
        OrderStatusUpdateCommand updateCmd = OrderStatusUpdateCommand.builder()
                .orderId(saga.getOrderId())
                .newStatus(OrderStatus.CONFIRMED)
                .paymentId(transactionId)
                .message("Đơn hàng đã được xác nhận.")
                .build();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                inventoryCommands.forEach(cmd -> 
                    rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_CONFIRM, cmd));
                
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_COMMAND_UPDATE, updateCmd);
                
                log.info("[ORCHESTRATOR] Đã kích hoạt luồng hoàn tất Saga cho đơn hàng: {}", saga.getOrderId());
            }
        });
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORCHESTRATOR_DELIVERY_REPLY)
    @Transactional
    public void handleDeliveryReply(DeliveryUpdatePayload payload) {
        log.info("[ORCHESTRATOR] Nhận cập nhật Giao hàng cho đơn hàng: {}, Trạng thái: {}", payload.getOrderId(), payload.getStatus());

        SagaInstance saga = sagaInstanceRepository.findByOrderId(payload.getOrderId())
                .orElse(null);

        if (saga == null) return;

        if ("DELIVERED".equalsIgnoreCase(payload.getStatus())) {
            if (saga.getCurrentStep() == SagaStatus.COMPLETED) return;

            log.info("[ORCHESTRATOR] Giao hàng THÀNH CÔNG cho đơn hàng: {}. Hoàn tất Saga.", payload.getOrderId());
            saga.setCurrentStep(SagaStatus.COMPLETED);
            sagaInstanceRepository.save(saga);

            // Cập nhật trạng thái đơn hàng thành COMPLETED
            OrderStatusUpdateCommand updateCmd = OrderStatusUpdateCommand.builder()
                    .orderId(saga.getOrderId())
                    .newStatus(OrderStatus.COMPLETED)
                    .message("Giao hàng thành công.")
                    .build();

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_COMMAND_UPDATE, updateCmd);
                    log.info("[ORCHESTRATOR] Đã gửi lệnh hoàn tất đơn hàng sang Order Service: {}", saga.getOrderId());
                }
            });
        }
    }
}
