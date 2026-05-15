package com.oms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResultPayload {
    private String orderId;
    private String status; // "SUCCESS" or "FAILURE"
    private String message;
}
