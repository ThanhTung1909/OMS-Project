package com.oms.orderservice.client;

import com.oms.orderservice.dto.InventoryUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "inventory-service") // Load balancer thông qua Eureka
public interface InventoryClient {
    
    @PostMapping("/api/v1/inventory/bulk-update")
    void updateInventory(@RequestBody List<InventoryUpdateRequest> requests);
}
