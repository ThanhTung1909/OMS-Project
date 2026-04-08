package com.oms.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event phát đi khi khách đã trả tiền
 * Trừ thẳng vào reservedQuantity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmOrderCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderId;
    private String productId;
    private int quantity;
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return "ConfirmOrderCommand{" +
                "orderId='" + orderId + '\'' +
                ", productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", createdAt=" + createdAt +
                '}';
    }
}
