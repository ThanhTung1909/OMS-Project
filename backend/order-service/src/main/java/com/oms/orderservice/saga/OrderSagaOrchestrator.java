package com.oms.orderservice.saga;

import java.time.LocalDateTime;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.oms.orderservice.dto.InventoryCommand;
import com.oms.orderservice.dto.PaymentResultPayload;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.entity.OrderStatus;
import com.oms.orderservice.repository.OrderRepository;
import com.oms.orderservice.config.RabbitMQConfig;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_REPLY_QUEUE)
    @Transactional
    public void handlePaymentResult(PaymentResultPayload payload) {
        if (payload == null || payload.getOrderId() == null) return;

        Order order = orderRepository.findById(payload.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + payload.getOrderId()));

        // Idempotency check: Chỉ xử lý nếu đơn hàng đang chờ thanh toán
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.info("Saga: Đơn hàng {} đã được xử lý trước đó (Status: {}). Bỏ qua.", 
                     order.getId(), order.getStatus());
            return;
        }

        if ("COMPLETED".equalsIgnoreCase(payload.getStatus())) {
            log.info("Saga: Thanh toán thành công cho đơn {}. Bắt đầu CONFIRM kho.", order.getId());
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentId(payload.getTransactionId());
            
            InventoryCommand confirmCmd = new InventoryCommand(order.getId(), "CONFIRM");
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "inventory.command.confirm", confirmCmd);
        } else {
            log.info("Saga: Thanh toán thất bại cho đơn {}. Bắt đầu ROLLBACK kho.", order.getId());
            order.setStatus(OrderStatus.CANCELLED);
            
            InventoryCommand rollbackCmd = new InventoryCommand(order.getId(), "ROLLBACK");
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "inventory.command.rollback", rollbackCmd);
        }
        
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}
