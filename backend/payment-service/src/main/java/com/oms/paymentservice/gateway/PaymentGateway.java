package com.oms.paymentservice.gateway;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentGatewayResponse processPayment(String orderId, String transactionId, BigDecimal amount);
}
