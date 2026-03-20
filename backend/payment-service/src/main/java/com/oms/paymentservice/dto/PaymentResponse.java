package com.oms.paymentservice.dto;

import java.math.BigDecimal;

public record PaymentResponse(
        String orderId,
        BigDecimal amount,
        String status,
        String transactionId
) {
}
