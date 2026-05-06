package com.oms.paymentservice.gateway.impl;

import lombok.extern.slf4j.Slf4j;
import com.oms.paymentservice.gateway.PaymentGateway;
import com.oms.paymentservice.gateway.PaymentGatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component("vnPayGateway")
@Slf4j
public class VNPayGateway implements PaymentGateway {

    @Override
    public PaymentGatewayResponse processPayment(String orderId, String transactionId, BigDecimal amount) {
        log.info("Processing payment via VNPay - OrderId: {}, Amount: {}, TransactionId: {}", orderId, amount, transactionId);
        
        try {
            // Simulate 2s delay
            simulateNetworkDelay();
            
            // Mock VNPay gateway logic - 80% success rate
            boolean isSuccess = ThreadLocalRandom.current().nextInt(100) < 80;
            
            if (isSuccess) {
                String referenceCode = "VNP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                log.info("VNPay payment successful - ReferenceCode: {}", referenceCode);
                return new PaymentGatewayResponse(true, "Payment processed successfully", referenceCode);
            } else {
                log.warn("VNPay payment failed for order: {}", orderId);
                return new PaymentGatewayResponse(false, "Payment processing failed", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("VNPay gateway interrupted", e);
            return new PaymentGatewayResponse(false, "Payment processing interrupted", null);
        }
    }

    private void simulateNetworkDelay() throws InterruptedException {
        Thread.sleep(2000); // 2 second delay
    }
}
