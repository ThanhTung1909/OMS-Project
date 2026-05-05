package com.oms.orderservice.saga;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.enums.OrderStatus;
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

    @RabbitListener(queues = RabbitMQConstants.PAYMENT_REPLY_RESULT)
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
}
