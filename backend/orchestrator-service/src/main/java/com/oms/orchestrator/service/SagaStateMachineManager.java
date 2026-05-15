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

        // Bước 1: Giữ hàng trong kho (Reserve Inventory)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                event.getItems().forEach(item -> {
                    InventoryCommand command = InventoryCommand.builder()
                            .orderId(event.getOrderId())
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .type("RESERVE")
                            .build();
                    rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_RESERVE, command);
                });
                log.info("[ORCHESTRATOR] Đã gửi lệnh giữ hàng (RESERVE) cho đơn hàng: {}", event.getOrderId());
            }
        });
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
            log.info("[ORCHESTRATOR] Giữ hàng THÀNH CÔNG cho đơn hàng: {}. Tiếp tục sang bước THANH TOÁN.", payload.getOrderId());
            saga.setCurrentStep(SagaStatus.INVENTORY_RESERVED);
            sagaInstanceRepository.save(saga);

            // Bước 2: Tạo yêu cầu thanh toán (Create Payment)
            try {
                OrderCreatedEvent originalEvent = objectMapper.readValue(saga.getPayload(), OrderCreatedEvent.class);
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
            } catch (JsonProcessingException e) {
                log.error("[ORCHESTRATOR] Lỗi khi đọc dữ liệu payload gốc", e);
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

        // Kiểm tra Idempotency
        if (saga.getCurrentStep() != SagaStatus.INVENTORY_RESERVED) {
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
                
                // Bước 3a: Chốt kho (Confirm Inventory)
                List<InventoryCommand> inventoryCommands = originalEvent.getItems().stream()
                        .map(item -> InventoryCommand.builder()
                                .orderId(saga.getOrderId())
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .type("CONFIRM")
                                .build())
                        .toList();

                // Bước 3b: Tạo vận đơn giao hàng (Create Delivery)
                DeliveryCommand deliveryCmd = DeliveryCommand.builder()
                        .orderId(saga.getOrderId())
                        .receiverName(originalEvent.getReceiverName())
                        .receiverPhone(originalEvent.getReceiverPhone())
                        .address(originalEvent.getAddress())
                        .codAmount(java.math.BigDecimal.ZERO)
                        .build();

                // Cập nhật trạng thái đơn hàng sang CONFIRMED
                OrderStatusUpdateCommand updateCmd = OrderStatusUpdateCommand.builder()
                        .orderId(saga.getOrderId())
                        .newStatus(OrderStatus.CONFIRMED)
                        .paymentId(payload.getTransactionId())
                        .message("Thanh toán thành công.")
                        .build();

                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        inventoryCommands.forEach(cmd -> 
                            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_CONFIRM, cmd));
                        
                        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.DELIVERY_COMMAND_CREATE, deliveryCmd);
                        
                        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_COMMAND_UPDATE, updateCmd);
                        
                        log.info("[ORCHESTRATOR] Đã gửi các lệnh hoàn tất (Confirm, Delivery, OrderUpdate) cho đơn hàng: {}", saga.getOrderId());
                    }
                });

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
