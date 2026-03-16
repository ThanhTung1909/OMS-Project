package com.oms.productservice.dto.productDTO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private List<String> imageUrl;
    private String categoryId;
}
