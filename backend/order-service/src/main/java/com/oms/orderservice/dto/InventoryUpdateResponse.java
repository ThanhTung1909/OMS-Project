package com.oms.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryUpdateResponse {
    private String id;
    private String productId;
    private int availableQuantity;
    private int reservedQuantity;
    private int totalQuantity;
    private LocalDateTime updatedAt;
    private String message;
}
