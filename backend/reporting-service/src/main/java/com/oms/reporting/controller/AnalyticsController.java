package com.oms.reporting.controller;

import com.oms.reporting.client.InventoryClient;
import com.oms.reporting.client.ProductClient;
import com.oms.reporting.entity.DailyRevenueStatistics;
import com.oms.reporting.entity.ProductSalesStatistics;
import com.oms.reporting.entity.ShipperPerformanceStatistics;
import com.oms.reporting.repository.DailyRevenueRepository;
import com.oms.reporting.repository.ProductSalesRepository;
import com.oms.reporting.repository.ShipperPerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/dashboard")
@RequiredArgsConstructor
public class AnalyticsController {

    private final DailyRevenueRepository dailyRevenueRepository;
    private final ProductSalesRepository productSalesRepository;
    private final ShipperPerformanceRepository shipperPerformanceRepository;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        LocalDate today = LocalDate.now();
        DailyRevenueStatistics todayStat = dailyRevenueRepository.findByStatDate(today)
                .orElse(new DailyRevenueStatistics());

        Map<String, Object> summary = new HashMap<>();
        summary.put("todayTotalRevenue", todayStat.getTotalRevenue());
        summary.put("todayCompletedOrders", todayStat.getCompletedOrders());
        
        int totalOrders = todayStat.getCompletedOrders() + todayStat.getCancelledOrders();
        double cancelRate = totalOrders == 0 ? 0 : (double) todayStat.getCancelledOrders() / totalOrders * 100;
        summary.put("todayCancelRate", cancelRate);
        
        // Count low stock items via inventory client
        try {
            var response = inventoryClient.getLowStockAlerts();
            if (response != null && response.isSuccess() && response.getResult() != null) {
                summary.put("lowStockItemsCount", response.getResult().size());
            } else {
                summary.put("lowStockItemsCount", 0);
            }
        } catch (Exception e) {
            summary.put("lowStockItemsCount", 0);
            summary.put("lowStockError", "Failed to fetch inventory alerts: " + e.getMessage());
        }

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/revenue-chart")
    public ResponseEntity<List<DailyRevenueStatistics>> getRevenueChart(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        List<DailyRevenueStatistics> stats = dailyRevenueRepository.findByStatDateBetweenOrderByStatDateAsc(startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<ProductSalesStatistics>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductSalesStatistics> topProducts = productSalesRepository.findTopSellingProducts(PageRequest.of(0, limit));
        return ResponseEntity.ok(topProducts);
    }

    @GetMapping("/shippers-kpi")
    public ResponseEntity<List<Map<String, Object>>> getShippersKpi() {
        List<ShipperPerformanceStatistics> stats = shipperPerformanceRepository.findAll();
        
        List<Map<String, Object>> result = stats.stream().map(stat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("shipperName", stat.getShipperName());
            map.put("shipperPhone", stat.getShipperPhone());
            
            double successRate = stat.getTotalDeliveries() == 0 ? 0 : 
                    (double) stat.getSuccessfulDeliveries() / stat.getTotalDeliveries() * 100;
            map.put("successRate", successRate);
            map.put("failedDeliveries", stat.getFailedDeliveries());
            
            double avgDeliveryTimeHrs = stat.getSuccessfulDeliveries() == 0 ? 0 :
                    (double) stat.getTotalDeliveryTimeSeconds() / stat.getSuccessfulDeliveries() / 3600;
            map.put("averageDeliveryTimeHours", avgDeliveryTimeHrs);
            
            return map;
        }).toList();
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/inventory-alerts")
    public ResponseEntity<List<Map<String, Object>>> getInventoryAlerts() {
        var response = inventoryClient.getLowStockAlerts();
        if (response != null && response.isSuccess() && response.getResult() != null) {
            List<Map<String, Object>> alerts = response.getResult();
            for (Map<String, Object> alert : alerts) {
                String productId = (String) alert.get("productId");
                if (productId != null) {
                    try {
                        var productResponse = productClient.getProductById(productId);
                        if (productResponse != null && productResponse.isSuccess() && productResponse.getResult() != null) {
                            alert.put("product", productResponse.getResult());
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to fetch product " + productId + " for inventory-alerts: " + e.getMessage());
                    }
                }
            }
            return ResponseEntity.ok(alerts);
        }
        return ResponseEntity.ok(List.of());
    }
}
