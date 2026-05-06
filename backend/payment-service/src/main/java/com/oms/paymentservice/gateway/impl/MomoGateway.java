package com.oms.paymentservice.gateway.impl;

import lombok.extern.slf4j.Slf4j;
import com.oms.paymentservice.gateway.PaymentGateway;
import com.oms.paymentservice.gateway.PaymentGatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component("momoGateway")
@Slf4j
public class MomoGateway implements PaymentGateway {

    @Override
    public PaymentGatewayResponse processPayment(String orderId, String transactionId, BigDecimal amount) {
        log.info("Processing payment via Momo - OrderId: {}, Amount: {}, TransactionId: {}", orderId, amount, transactionId);
        
        try {
            // Simulate 2s delay
            simulateNetworkDelay();
            
            // Mock Momo gateway logic - 85% success rate
            boolean isSuccess = ThreadLocalRandom.current().nextInt(100) < 85;
            
            if (isSuccess) {
                String referenceCode = "MOMO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                log.info("Momo payment successful - ReferenceCode: {}", referenceCode);
                return new PaymentGatewayResponse(true, "Payment processed successfully", referenceCode);
            } else {
                log.warn("Momo payment failed for order: {}", orderId);
                return new PaymentGatewayResponse(false, "Payment processing failed", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Momo gateway interrupted", e);
            return new PaymentGatewayResponse(false, "Payment processing interrupted", null);
        }
    }

    private void simulateNetworkDelay() throws InterruptedException {
        Thread.sleep(2000); // 2 second delay
    }
}
