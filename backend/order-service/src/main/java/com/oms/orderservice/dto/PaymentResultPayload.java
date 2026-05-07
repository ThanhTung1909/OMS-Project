package com.oms.orderservice.dto;

import lombok.Data;

@Data
public class PaymentResultPayload {
    private String orderId;
    private String paymentStatus; 
    private String transactionId;
}
