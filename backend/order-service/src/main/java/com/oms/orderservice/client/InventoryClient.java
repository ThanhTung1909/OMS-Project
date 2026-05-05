package com.oms.orderservice.client;

import com.oms.orderservice.dto.InventoryReserveRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "inventory-service")
public interface InventoryClient {
    
    @PostMapping("/api/v1/inventory/reserve-bulk")
    void reserveBulk(@RequestBody List<InventoryReserveRequest> requests);
}
