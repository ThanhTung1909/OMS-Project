package com.oms.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {
    @NotBlank(message = "Mã sản phẩm không được để trống")
    private String productId;

    private String productName;

    private BigDecimal price;

    @Min(value = 1, message = "Số lượng sản phẩm phải ít nhất là 1")
    private int quantity;
}
