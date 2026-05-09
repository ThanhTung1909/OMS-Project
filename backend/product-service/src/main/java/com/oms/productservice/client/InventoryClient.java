package com.oms.productservice.client;

import com.oms.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "inventory-service", url = "${app.services.inventory-service.url:http://inventory-service:8082}")
public interface InventoryClient {

    /**
     * Lấy số lượng tồn kho cho nhiều sản phẩm trong một lần gọi.
     * @param productIds Danh sách productId cần kiểm tra tồn kho.
     * @return Map<productId, availableQuantity>
     */
    @PostMapping("/api/v1/inventory/bulk-stock")
    ApiResponse<Map<String, Integer>> getBulkStock(@RequestBody List<String> productIds);
}
