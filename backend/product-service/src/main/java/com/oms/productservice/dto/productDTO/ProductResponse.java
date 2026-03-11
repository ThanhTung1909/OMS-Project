package com.oms.productservice.dto.productDTO;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProductResponse {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private List<String> imageUrl;
    private String categoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
