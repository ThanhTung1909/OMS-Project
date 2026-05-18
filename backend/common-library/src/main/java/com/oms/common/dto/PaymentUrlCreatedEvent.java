package com.oms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentUrlCreatedEvent {
    private String orderId;
    private String transactionId;
    private String paymentUrl;
}
