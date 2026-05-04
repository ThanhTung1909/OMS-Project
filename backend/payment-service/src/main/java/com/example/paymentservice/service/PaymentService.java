package com.example.paymentservice.service;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentService {
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void processPayment(Long orderId, Double amount) throws InterruptedException {
        // Idempotency: kiểm tra đã thanh toán chưa
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent() && existing.get().getStatus() == PaymentStatus.COMPLETED) {
            // Đã thanh toán, không xử lý lại
            return;
        }
        // Giả lập độ trễ 2s
        Thread.sleep(2000);
        // Mock thanh toán (luôn thành công)
        PaymentStatus status = PaymentStatus.COMPLETED;
        Payment payment = existing.orElse(new Payment(orderId, amount, status));
        payment.setStatus(status);
        paymentRepository.save(payment);
        // Gửi event kết quả
        PaymentResultEvent result = new PaymentResultEvent();
        result.setOrderId(orderId);
        result.setStatus(status.name());
        rabbitTemplate.convertAndSend("oms.exchange", "payment.reply.result", result);
    }

    // Inner class cho event trả về
    public static class PaymentResultEvent {
        private Long orderId;
        private String status;
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
