package com.oms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String orderId;
    private String productId;
    private int quantity;
    private String type; // "RESERVE", "CONFIRM", "ROLLBACK"
}
