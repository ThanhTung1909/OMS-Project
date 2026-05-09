package com.oms.deliveryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRequest {
    private String orderId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private java.math.BigDecimal codAmount;
}
