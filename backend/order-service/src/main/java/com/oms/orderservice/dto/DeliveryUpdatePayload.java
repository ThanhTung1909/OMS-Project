package com.oms.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryUpdatePayload {
    private String orderId;
    private String deliveryId;
    private String status;
    private String failReason;
}
