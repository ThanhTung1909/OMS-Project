package com.oms.paymentservice.listener;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.paymentservice.config.RabbitMQConfig;
import com.oms.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandListener {

    private final PaymentService paymentService;

    /**
     * Listen for payment command from Order Service
     * Routing Key: payment.command.create
     * Exchange: oms.exchange
     * 
     * Implements idempotency to prevent duplicate payments
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAYMENT_COMMAND)
    public void handlePaymentCommand(Map<String, Object> payload) {
        log.info("🔔 [PAYMENT] Received payment command from Order Service: {}", payload);
        
        // Validate payload
        if (payload == null || !payload.containsKey("orderId") || !payload.containsKey("amount")) {
            log.error("❌ Invalid payment command payload - missing orderId or amount: {}", payload);
            return;
        }

        String orderId = null;
        BigDecimal amount = null;
        
        try {
            orderId = payload.get("orderId").toString();
            amount = new BigDecimal(payload.get("amount").toString());
            
            if (orderId.trim().isEmpty()) {
                log.error("❌ OrderId cannot be empty");
                return;
            }
            
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("❌ Payment amount must be greater than 0");
                return;
            }

            log.info("✅ Payment command validated - OrderId: {}, Amount: {}", orderId, amount);
            
            // Process payment with idempotency check
            paymentService.processPaymentCommand(orderId, amount);
            
            log.info("✅ Payment command processed successfully for OrderId: {}", orderId);
            
        } catch (NumberFormatException e) {
            log.error("❌ Invalid amount format for OrderId {}: {}", orderId, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error processing payment command for OrderId {}: {}", orderId, e.getMessage(), e);
        }
    }
}

