package com.oms.orderservice.service;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.dto.DeliveryUpdatePayload;
import com.oms.common.dto.InventoryCommand;
import com.oms.common.dto.NotificationEvent;
import com.oms.common.dto.OrderStatusUpdateCommand;
import com.oms.common.enums.OrderStatus;
import com.oms.orderservice.config.RabbitMQConfig;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCommandHandler {

    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_COMMAND_UPDATE)
    @Transactional
    public void handleOrderStatusUpdate(OrderStatusUpdateCommand command) {
        if (command == null || command.getOrderId() == null) return;

        log.info("[ORDER-SERVICE] Nhận lệnh OrderStatusUpdateCommand cho đơn hàng: {}, Trạng thái mới: {}", 
                command.getOrderId(), command.getNewStatus());

        Order order = orderRepository.findById(command.getOrderId())
                .orElse(null);

        if (order == null) {
            log.error("[ORDER-SERVICE] Không tìm thấy đơn hàng: {}", command.getOrderId());
            return;
        }

        // Cập nhật trạng thái và các thông tin liên quan
        if (command.getNewStatus() != null) {
            order.setStatus(command.getNewStatus());
        }
        order.setUpdatedAt(LocalDateTime.now());
        
        if (command.getPaymentId() != null) {
            order.setPaymentId(command.getPaymentId());
        }

        if (command.getPaymentUrl() != null) {
            order.setPaymentUrl(command.getPaymentUrl());
        }

        if (command.getErrorMessage() != null) {
            order.setErrorMessage(command.getErrorMessage());
        } else {
            // Nếu không có lỗi, đảm bảo errorMessage là null (tránh gán message thành công vào error)
            order.setErrorMessage(null);
        }

        orderRepository.save(order);
        log.info("[ORDER-SERVICE] Đã cập nhật đơn hàng {} sang trạng thái {}", order.getId(), order.getStatus());

        // Phát sự kiện thông báo trạng thái đơn hàng sang RabbitMQ
        try {
            NotificationEvent notifyEvent = NotificationEvent.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .status(order.getStatus().name())
                    .message("Đơn hàng của bạn đã chuyển sang trạng thái: " + order.getStatus().name())
                    .build();
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.EXCHANGE_NAME, 
                    RabbitMQConstants.NOTIFICATION_ORDER_STATUS, 
                    notifyEvent
            );
            log.info("[ORDER-SERVICE] Đã gửi thông báo trạng thái mới {} cho đơn hàng {}", order.getStatus(), order.getId());
        } catch (Exception e) {
            log.error("[ORDER-SERVICE] Lỗi khi gửi thông báo trạng thái đơn hàng lên RabbitMQ: {}", e.getMessage());
        }
        publishStatusEvent(order);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELIVERY_STATUS)
    @Transactional
    public void handleDeliveryStatusUpdate(DeliveryUpdatePayload payload) {
        if (payload == null || payload.getOrderId() == null) return;

        log.info("[ORDER-SERVICE] Nhận cập nhật trạng thái vận đơn: {}, DeliveryId: {}, Status: {}", 
                payload.getOrderId(), payload.getDeliveryId(), payload.getStatus());

        Order order = orderRepository.findById(payload.getOrderId()).orElse(null);
        if (order == null) {
            log.error("[ORDER-SERVICE] Không tìm thấy đơn hàng cho vận đơn: {}", payload.getOrderId());
            return;
        }

        // 1. Cập nhật deliveryId ngay khi nhận được (bất kể trạng thái nào)
        if (payload.getDeliveryId() != null) {
            order.setDeliveryId(payload.getDeliveryId());
        }

        // 2. Cập nhật trạng thái đơn hàng dựa trên vận đơn
        String status = payload.getStatus();
        if ("COMPLETED".equals(status) || "DELIVERED".equals(status)) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setErrorMessage(null);
        } else if ("RETURNED".equals(status)) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setErrorMessage("Giao hàng thất bại: " + payload.getFailReason());

            // Hoàn lại kho cho đơn giao hàng không thành công (RETURNED)
            try {
                if (order.getOrderItems() != null) {
                    order.getOrderItems().forEach(item -> {
                        InventoryCommand command = InventoryCommand.builder()
                                .orderId(order.getId())
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .type("ROLLBACK")
                                .build();
                        rabbitTemplate.convertAndSend(
                                RabbitMQConstants.EXCHANGE_NAME, 
                                RabbitMQConstants.INVENTORY_COMMAND_ROLLBACK, 
                                command
                        );
                        log.info("[ORDER-SERVICE] Đã gửi lệnh hoàn kho ROLLBACK cho đơn giao hàng thất bại. Đơn: {}, Sản phẩm: {}, Số lượng: {}", 
                                order.getId(), item.getProductId(), item.getQuantity());
                    });
                }
            } catch (Exception e) {
                log.error("[ORDER-SERVICE] Lỗi khi gửi lệnh hoàn kho khi giao hàng thất bại: {}", e.getMessage());
            }
        }

        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        publishStatusEvent(order);
    }

    private void publishStatusEvent(Order order) {
        try {
            if (order.getStatus() == OrderStatus.COMPLETED) {
                java.util.List<com.oms.common.dto.OrderCompletedEvent.OrderItem> eventItems = order.getOrderItems().stream()
                        .map(item -> com.oms.common.dto.OrderCompletedEvent.OrderItem.builder()
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .build())
                        .collect(java.util.stream.Collectors.toList());

                com.oms.common.dto.OrderCompletedEvent completedEvent = com.oms.common.dto.OrderCompletedEvent.builder()
                        .orderId(order.getId())
                        .totalAmount(order.getTotalAmount())
                        .paymentMethod(order.getPaymentMethod())
                        .items(eventItems)
                        .build();

                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, "order.status.completed", completedEvent);
                log.info("[ORDER-SERVICE] Đã phát sự kiện OrderCompletedEvent cho đơn hàng {}", order.getId());
            } else if (order.getStatus() == OrderStatus.CANCELLED) {
                com.oms.common.dto.OrderCancelledEvent cancelledEvent = com.oms.common.dto.OrderCancelledEvent.builder()
                        .orderId(order.getId())
                        .cancelledAt(order.getUpdatedAt() != null ? order.getUpdatedAt() : LocalDateTime.now())
                        .build();

                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, "order.status.cancelled", cancelledEvent);
                log.info("[ORDER-SERVICE] Đã phát sự kiện OrderCancelledEvent cho đơn hàng {}", order.getId());
            }
        } catch (Exception e) {
            log.error("[ORDER-SERVICE] Lỗi khi phát sự kiện báo cáo (hoàn tất/hủy) đơn hàng: {}", e.getMessage(), e);
        }
    }
}
