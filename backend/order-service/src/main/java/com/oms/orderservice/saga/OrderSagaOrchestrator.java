package com.oms.orderservice.saga;

import java.time.LocalDateTime;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.oms.orderservice.dto.PaymentResultPayload;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.entity.OrderStatus;
import com.oms.orderservice.repository.OrderRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = "payment.reply.result.queue")
    @Transactional
    public void handlePaymentResult(PaymentResultPayload payload){
        if (payload == null || payload.getOrderId() == null) {
            log.error("Nhận được payload thanh toán trống hoặc thiếu OrderId!");
            return;
        }

        log.info("Saga Nhạc trưởng nhận kết quả thanh toán cho đơn {}: {}", payload.getOrderId(), payload.getStatus());

        Order order = orderRepository.findById(payload.getOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        String exchangeName = "order.exchange"; 

        if ("COMPLETED".equalsIgnoreCase(payload.getStatus())) {
            // TH1: Thành công (CONFIRMED)
            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(LocalDateTime.now());
            order.setPaymentId(payload.getTransactionId());
            orderRepository.save(order);

            // Bắn lệnh sang Inventory chốt trừ kho vĩnh viễn
            rabbitTemplate.convertAndSend(exchangeName, "inventory.command.confirm", payload.getOrderId());
            log.info("Thanh toán thành công -> Đã gửi lệnh CHỐT KHO cho đơn {}", order.getId());

        } else if ("FAILED".equalsIgnoreCase(payload.getStatus())) {
            // TH2: Thất bại (CANCELLED)
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            // Bắn lệnh sang Inventory nhả kho (Rollback) cho người khác mua
            rabbitTemplate.convertAndSend(exchangeName, "inventory.command.rollback", payload.getOrderId());
            log.info("Thanh toán thất bại -> Đã gửi lệnh NHẢ KHO cho đơn {}", order.getId());
        }
    }


}
