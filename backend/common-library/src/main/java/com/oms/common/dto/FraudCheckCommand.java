package com.oms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckCommand {
    private String orderId;
    private String userId;
    private BigDecimal totalAmount;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String paymentMethod;
    private List<OrderItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private String productId;
        private String productName;
        private BigDecimal price;
        private int quantity;
    }
}
