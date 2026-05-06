package com.oms.paymentservice.gateway;

public record PaymentGatewayResponse(
        boolean success,
        String message,
        String referenceCode
) {
}
