package com.oms.paymentservice.dto;

public record PaymentEvent(
        String orderId,
        String paymentStatus,
        String transactionId
) {
}
