package com.oms.common.dto;

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
    private String trackingNumber;
    private String shipperName;
    private String shipperPhone;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
