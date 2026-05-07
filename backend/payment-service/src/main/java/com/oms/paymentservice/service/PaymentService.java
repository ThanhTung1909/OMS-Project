package com.oms.paymentservice.service;

import lombok.extern.slf4j.Slf4j;
import com.oms.common.constant.RabbitMQConstants;
import com.oms.paymentservice.dto.PaymentEvent;
import com.oms.paymentservice.dto.PaymentRequest;
import com.oms.paymentservice.dto.PaymentResponse;
import com.oms.paymentservice.entity.Payment;
import com.oms.paymentservice.entity.PaymentStatus;
import com.oms.paymentservice.gateway.PaymentGateway;
import com.oms.paymentservice.gateway.PaymentGatewayResponse;
import com.oms.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.ClassUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    @Qualifier("vnPayGateway")
    private PaymentGateway vnPayGateway;

    @Autowired
    @Qualifier("momoGateway")
    private PaymentGateway momoGateway;

    /**
     * API endpoint handler for payment requests
     */
    @Transactional
    public PaymentResponse pay(PaymentRequest request) {
        return processPayment(request.orderId(), request.amount());
    }

    /**
     * Message listener handler for payment command
     * Implements idempotency check to prevent duplicate payments
     */
    @Transactional
    public void processPaymentCommand(String orderId, BigDecimal amount) {
        log.info("Processing payment command for orderId: {}, amount: {}", orderId, amount);
        
        // Idempotency check: if order already has completed payment, skip processing
        var existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.COMPLETED) {
            log.warn("Payment already completed for orderId: {}. Skipping duplicate payment processing.", orderId);
            // Send reply with existing payment result
            PaymentEvent paymentEvent = new PaymentEvent(
                    existingPayment.get().getOrderId(),
                    existingPayment.get().getStatus().name(),
                    existingPayment.get().getTransactionId()
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.EXCHANGE_NAME,
                    RabbitMQConstants.PAYMENT_REPLY_RESULT,
                    paymentEvent
            );
            return;
        }

        processPayment(orderId, amount);
    }

    /**
     * Core payment processing logic with external gateway integration
     */
    private PaymentResponse processPayment(String orderId, BigDecimal amount) {
        String transactionId = UUID.randomUUID().toString();
        log.info("Starting payment processing for orderId: {}, transactionId: {}", orderId, transactionId);

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElse(new Payment());
        
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTransactionId(transactionId);
        payment.setRetryCount((payment.getRetryCount() == null ? 0 : payment.getRetryCount()) + 1);
        payment.setLastRetryAt(LocalDateTime.now());
        
        // Choose payment gateway (alternating between VNPay and Momo)
        PaymentGateway gateway = choosePaymentGateway();
        payment.setPaymentGateway(ClassUtils.getUserClass(gateway).getSimpleName());
        
        paymentRepository.save(payment);

        // Call external payment gateway
        PaymentGatewayResponse gatewayResponse = processWithExternalGateway(gateway, orderId, transactionId, amount);
        
        // Update payment status based on gateway response
        PaymentStatus finalStatus = gatewayResponse.success() ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
        payment.setStatus(finalStatus);
        paymentRepository.save(payment);

        log.info("Payment processing completed - OrderId: {}, Status: {}, Gateway: {}", orderId, finalStatus, payment.getPaymentGateway());

        // Send result back to Order Service via payment.reply.result routing key
        PaymentEvent paymentEvent = new PaymentEvent(orderId, finalStatus.name(), transactionId);
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

    /**
     * Process payment with external gateway
     */
    private PaymentGatewayResponse processWithExternalGateway(PaymentGateway gateway, String orderId, 
                                                               String transactionId, BigDecimal amount) {
        try {
            log.info("Calling {} gateway for orderId: {}", gateway.getClass().getSimpleName(), orderId);
            return gateway.processPayment(orderId, transactionId, amount);
        } catch (Exception e) {
            log.error("External gateway error for orderId: {}: {}", orderId, e.getMessage(), e);
            return new PaymentGatewayResponse(false, "Gateway error: " + e.getMessage(), null);
        }
    }

    /**
     * Choose payment gateway (simple alternation logic)
     */
    private PaymentGateway choosePaymentGateway() {
        long count = paymentRepository.count();
        return count % 2 == 0 ? vnPayGateway : momoGateway;
    }
}
