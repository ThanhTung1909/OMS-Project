package com.example.paymentservice.messaging;

import com.example.paymentservice.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandListener {
    @Autowired
    private PaymentService paymentService;

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "q.payment.command", durable = "true"),
        exchange = @Exchange(value = "oms.exchange", type = "topic"),
        key = "payment.command.create"
    ))
    public void handlePaymentCommand(PaymentCommandEvent event) throws InterruptedException {
        paymentService.processPayment(event.getOrderId(), event.getAmount());
    }

    // Inner class for event payload (có thể thay bằng DTO chung)
    public static class PaymentCommandEvent {
        private Long orderId;
        private Double amount;
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
    }
}
