package com.oms.orderservice.client;

import com.oms.orderservice.dto.InventoryUpdateRequest;
import com.oms.orderservice.dto.InventoryUpdateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", url = "http://localhost:8083")
public interface InventoryClient {
    @PostMapping("/api/v1/inventory/update")
    InventoryUpdateResponse updateInventory(@RequestBody InventoryUpdateRequest inventoryUpdateRequest);

}
