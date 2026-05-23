package com.oms.reporting.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipper_performance_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipperPerformanceStatistics {
    @Id
    private String id;
    
    private String shipperName;
    
    private String shipperPhone;
    
    @Builder.Default
    private int totalDeliveries = 0;
    
    @Builder.Default
    private int successfulDeliveries = 0;
    
    @Builder.Default
    private int failedDeliveries = 0;
    
    @Builder.Default
    private long totalDeliveryTimeSeconds = 0;
}
