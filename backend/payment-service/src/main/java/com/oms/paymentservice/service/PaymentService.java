package com.oms.paymentservice.service;

import com.oms.paymentservice.config.RabbitMqConfig;
import com.oms.paymentservice.dto.PaymentEvent;
import com.oms.paymentservice.dto.PaymentRequest;
import com.oms.paymentservice.dto.PaymentResponse;
import com.oms.paymentservice.entity.Payment;
import com.oms.paymentservice.entity.PaymentStatus;
import com.oms.paymentservice.repository.PaymentRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        int randomPercent = ThreadLocalRandom.current().nextInt(100);
        boolean isSuccess = randomPercent < 80;
        PaymentStatus dbStatus = isSuccess ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

        String transactionId = UUID.randomUUID().toString();

        Payment payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount());
        payment.setStatus(dbStatus);
        payment.setTransactionId(transactionId);
        paymentRepository.save(payment);

        String eventStatus = dbStatus.name();
        PaymentEvent paymentEvent = new PaymentEvent(request.orderId(), eventStatus, transactionId);
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.PAYMENT_EXCHANGE,
                RabbitMqConfig.PAYMENT_ROUTING_KEY,
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
