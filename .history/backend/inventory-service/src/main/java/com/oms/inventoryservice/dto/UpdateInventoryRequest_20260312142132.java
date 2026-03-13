package com.oms.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateInventoryRequest {
    private String productId;
    private int quantity; // số lượng muốn cập nhật (có thể dương hoặc âm)
    private String type; // "ADD", "REDUCE", "RESERVE", "RELEASE"
}
