package com.oms.paymentservice.listener;

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

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAYMENT_COMMAND)
    public void handlePaymentCommand(Map<String, Object> payload) {
        log.info("Received payment command: {}", payload);
        if (payload == null || !payload.containsKey("orderId") || !payload.containsKey("amount")) {
            log.error("Invalid payment command payload");
            return;
        }

        String orderId = payload.get("orderId").toString();
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        try {
            paymentService.processPaymentCommand(orderId, amount);
        } catch (Exception e) {
            log.error("Error processing payment command for order {}: {}", orderId, e.getMessage());
        }
    }
}
