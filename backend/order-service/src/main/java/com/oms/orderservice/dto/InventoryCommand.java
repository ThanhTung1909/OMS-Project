package com.oms.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCommand {
    private String orderId;
    private String type; // "CONFIRM" hoặc "ROLLBACK"
}
