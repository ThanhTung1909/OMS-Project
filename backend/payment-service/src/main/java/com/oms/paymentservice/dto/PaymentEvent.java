package com.oms.paymentservice.dto;

public record PaymentEvent(
        String orderId,
        String status,
        String transactionId
) {
}
