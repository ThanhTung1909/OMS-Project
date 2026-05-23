package com.oms.reporting.client;

import com.oms.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@FeignClient(name = "inventory-service", path = "/api/v1/inventory")
public interface InventoryClient {

    @GetMapping("/low-stock")
    ApiResponse<List<Map<String, Object>>> getLowStockAlerts();
}
