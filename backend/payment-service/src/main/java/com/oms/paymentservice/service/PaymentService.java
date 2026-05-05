package com.oms.paymentservice.service;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.paymentservice.dto.PaymentEvent;
import com.oms.paymentservice.dto.PaymentRequest;
import com.oms.paymentservice.dto.PaymentResponse;
import com.oms.paymentservice.entity.Payment;
import com.oms.paymentservice.entity.PaymentStatus;
import com.oms.paymentservice.repository.PaymentRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;

    public PaymentService(PaymentRepository paymentRepository, RabbitTemplate rabbitTemplate) {
        this.paymentRepository = paymentRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        return processPayment(request.orderId(), request.amount());
    }

    @Transactional
    public void processPaymentCommand(String orderId, BigDecimal amount) {
        processPayment(orderId, amount);
    }

    private PaymentResponse processPayment(String orderId, BigDecimal amount) {
        int randomPercent = ThreadLocalRandom.current().nextInt(100);
        boolean isSuccess = randomPercent < 80;
        PaymentStatus dbStatus = isSuccess ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

        String transactionId = UUID.randomUUID().toString();

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElse(new Payment());
        
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setStatus(dbStatus);
        payment.setTransactionId(transactionId);
        paymentRepository.save(payment);

        String eventStatus = dbStatus.name();
        PaymentEvent paymentEvent = new PaymentEvent(orderId, eventStatus, transactionId);
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_NAME,
                RabbitMQConstants.PAYMENT_REPLY_RESULT,
                paymentEvent
        );

        return new PaymentResponse(
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getTransactionId()
        );
    }
}
