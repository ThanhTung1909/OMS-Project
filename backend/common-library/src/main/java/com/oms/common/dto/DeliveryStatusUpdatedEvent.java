package com.oms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatusUpdatedEvent {
    private String deliveryId;
    private String shipperName;
    private String shipperPhone;
    private String status; // DELIVERED, FAILED, RETURNED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
