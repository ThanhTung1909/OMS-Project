package com.oms.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private String productId;
    private String productName;
    private BigDecimal price;
    private int quantity;
    /** Thumbnail hình ảnh sản phẩm (enrich từ Product Service) */
    private String imageUrl;
}
