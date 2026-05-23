package com.oms.reporting.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_revenue_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueStatistics {
    @Id
    private String id;
    
    private LocalDate statDate;
    
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal codRevenue = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal onlineRevenue = BigDecimal.ZERO;
    
    @Builder.Default
    private int completedOrders = 0;
    
    @Builder.Default
    private int cancelledOrders = 0;
}
