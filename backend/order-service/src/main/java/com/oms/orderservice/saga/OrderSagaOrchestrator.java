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

        log.info("🔔 [SAGA] Received payment result for Order: {}, Status: {}", payload.getOrderId(), payload.getPaymentStatus());

        // Cơ chế retry tìm Order (đề phòng race condition khi DB chưa kịp commit xong transaction tạo đơn)
        Order order = null;
        int retryCount = 0;
        while (retryCount < 3) {
            var orderOpt = orderRepository.findById(payload.getOrderId());
            if (orderOpt.isPresent()) {
                order = orderOpt.get();
                break;
            }
            try {
                log.warn("⚠️ [SAGA] Order {} not found in DB. Retrying... ({}/3)", payload.getOrderId(), retryCount + 1);
                Thread.sleep(1000); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            retryCount++;
        }

        if (order == null) {
            log.error("❌ [SAGA] CRITICAL: Order not found after retries: {}. Dropping message to avoid infinite loop.", payload.getOrderId());
            return; // Kết thúc xử lý, không ném Exception để tránh Spring AMQP re-queue tin nhắn lỗi này.
        }

        final Order finalOrder = order;

        // Idempotency check
        if (finalOrder.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.info("[SAGA] Order {} already processed (Status: {}). Skipping.", 
                     finalOrder.getId(), finalOrder.getStatus());
            return;
        }

        if ("COMPLETED".equalsIgnoreCase(payload.getPaymentStatus())) {
            log.info("[SAGA] Payment COMPLETED for order {}. Initiating inventory CONFIRM.", finalOrder.getId());
            finalOrder.setStatus(OrderStatus.CONFIRMED);
            finalOrder.setPaymentId(payload.getTransactionId());
            
            finalOrder.getOrderItems().forEach(item -> {
                InventoryCommand confirmCmd = new InventoryCommand(finalOrder.getId(), item.getProductId(), item.getQuantity(), "CONFIRM");
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_CONFIRM, confirmCmd);
            });
            
        } else {
            log.info("[SAGA] Payment FAILED for order {}. Initiating inventory ROLLBACK.", finalOrder.getId());
            finalOrder.setStatus(OrderStatus.CANCELLED);
            
            finalOrder.getOrderItems().forEach(item -> {
                InventoryCommand rollbackCmd = new InventoryCommand(finalOrder.getId(), item.getProductId(), item.getQuantity(), "ROLLBACK");
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_ROLLBACK, rollbackCmd);
            });
        }
        
        finalOrder.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(finalOrder);
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
