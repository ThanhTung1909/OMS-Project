package com.oms.orderservice.saga;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.orderservice.config.RabbitMQConfig;
import com.oms.common.enums.OrderStatus;
import com.oms.orderservice.dto.DeliveryUpdatePayload;
import com.oms.orderservice.dto.InventoryCommand;
import com.oms.orderservice.dto.PaymentResultPayload;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAYMENT_REPLY)
    @Transactional
    public void handlePaymentResult(PaymentResultPayload payload) {
        if (payload == null || payload.getOrderId() == null) return;

        Order order = orderRepository.findById(payload.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + payload.getOrderId()));

        // Idempotency check
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.info("[SAGA] Order {} already processed (Status: {}). Skipping.", 
                     order.getId(), order.getStatus());
            return;
        }

        if ("COMPLETED".equalsIgnoreCase(payload.getStatus())) {
            log.info("[SAGA] Payment COMPLETED for order {}. Initiating inventory CONFIRM.", order.getId());
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentId(payload.getTransactionId());
            
            order.getOrderItems().forEach(item -> {
                InventoryCommand confirmCmd = new InventoryCommand(order.getId(), item.getProductId(), item.getQuantity(), "CONFIRM");
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_CONFIRM, confirmCmd);
            });
            
        } else {
            log.info("[SAGA] Payment FAILED for order {}. Initiating inventory ROLLBACK.", order.getId());
            order.setStatus(OrderStatus.CANCELLED);
            
            order.getOrderItems().forEach(item -> {
                InventoryCommand rollbackCmd = new InventoryCommand(order.getId(), item.getProductId(), item.getQuantity(), "ROLLBACK");
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_ROLLBACK, rollbackCmd);
            });
        }
        
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELIVERY_STATUS)
    @Transactional
    public void handleDeliveryStatusUpdate(DeliveryUpdatePayload payload) {
        if (payload == null || payload.getOrderId() == null || payload.getStatus() == null) return;

        String orderId = payload.getOrderId();
        String status = payload.getStatus();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if ("COMPLETED".equalsIgnoreCase(status)) {
            log.info("[SAGA] Delivery COMPLETED for order {}. Order is now COMPLETED.", orderId);
            order.setStatus(OrderStatus.COMPLETED);
            order.setDeliveryId(payload.getDeliveryId());
        } else if ("FAILED".equalsIgnoreCase(status)) {
            String failReason = payload.getFailReason() != null ? payload.getFailReason() : "Unknown delivery error";
            log.info("[SAGA] Delivery FAILED for order {}. Reason: {}. Order is now CANCELLED.", orderId, failReason);
            order.setStatus(OrderStatus.CANCELLED);
            order.setErrorMessage("Giao hàng thất bại: " + failReason);
        }
        
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}
