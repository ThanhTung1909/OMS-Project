package com.oms.reporting.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_sales_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesStatistics {
    @Id
    private String id;
    
    private String productId;
    
    private String productName;
    
    @Builder.Default
    private int totalSoldQuantity = 0;
    
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    
    private LocalDateTime lastUpdatedAt;
}
