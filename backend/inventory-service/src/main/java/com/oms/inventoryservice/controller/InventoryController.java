package com.oms.inventoryservice.controller;

import com.oms.common.ApiResponse;
import com.oms.inventoryservice.dto.UpdateInventoryRequest;
import com.oms.inventoryservice.dto.UpdateInventoryResponse;
import com.oms.inventoryservice.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    /**
     * Cập nhật số lượng tồn kho
     */
    @PostMapping("/update")
    public ResponseEntity<ApiResponse<UpdateInventoryResponse>> updateInventory(@RequestBody UpdateInventoryRequest request) {
        log.info("Received update inventory request: {}", request);
        UpdateInventoryResponse response = inventoryService.updateInventory(request);
        return ResponseEntity.ok(
            ApiResponse.<UpdateInventoryResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(response)
                .build()
        );
    }

    /**
     * Lấy thông tin tồn kho theo productId
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<UpdateInventoryResponse>> getInventory(@PathVariable String productId) {
        log.info("Retrieving inventory for product: {}", productId);
        UpdateInventoryResponse response = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(
            ApiResponse.<UpdateInventoryResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(response)
                .build()
        );
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<java.util.List<UpdateInventoryResponse>>> getLowStockAlerts() {
        return ResponseEntity.ok(ApiResponse.<java.util.List<UpdateInventoryResponse>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(inventoryService.getLowStockProducts())
                .build());
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Inventory Service is running");
    }

    /**
     * Giữ kho hàng loạt (dùng cho tạo đơn hàng)
     */
    @PostMapping("/reserve-bulk")
    public ResponseEntity<ApiResponse<Void>> reserveBulk(@RequestBody java.util.List<com.oms.inventoryservice.dto.InventoryReserveRequest> requests) {
        log.info("Received bulk reservation request for {} items", requests.size());
        inventoryService.reserveBulk(requests);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Giữ kho thành công")
                .build()
        );
    }

    /**
     * Lấy số lượng tồn kho khả dụng cho nhiều sản phẩm trong một lần gọi.
     * Được gọi nội bộ bởi Product Service
     */
    @PostMapping("/bulk-stock")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getBulkStock(@RequestBody List<String> productIds) {
        log.info("Received bulk-stock request for {} product(s)", productIds.size());
        Map<String, Integer> stockMap = inventoryService.getBulkStock(productIds);
        return ResponseEntity.ok(
            ApiResponse.<Map<String, Integer>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(stockMap)
                .build()
        );
    }
}
