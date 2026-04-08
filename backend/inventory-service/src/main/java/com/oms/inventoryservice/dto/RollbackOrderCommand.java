package com.oms.inventoryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event phát đi khi đơn huỷ
 * Trả lại hàng lên kệ: availableQuantity tăng lại, reservedQuantity giảm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RollbackOrderCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private String orderId;
    private String productId;
    private int quantity;
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return "RollbackOrderCommand{" +
                "orderId='" + orderId + '\'' +
                ", productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", createdAt=" + createdAt +
                '}';
    }
}
