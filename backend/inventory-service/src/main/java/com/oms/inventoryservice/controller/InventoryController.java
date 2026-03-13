package com.oms.inventoryservice.controller;

import com.oms.inventoryservice.dto.UpdateInventoryRequest;
import com.oms.inventoryservice.dto.UpdateInventoryResponse;
import com.oms.inventoryservice.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<UpdateInventoryResponse> updateInventory(@RequestBody UpdateInventoryRequest request) {
        try {
            log.info("Received update inventory request: {}", request);
            UpdateInventoryResponse response = inventoryService.updateInventory(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating inventory: {}", e.getMessage());
            UpdateInventoryResponse errorResponse = UpdateInventoryResponse.builder()
                    .message("Error: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    /**
     * Lấy thông tin tồn kho theo productId
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<UpdateInventoryResponse> getInventory(@PathVariable String productId) {
        try {
            log.info("Retrieving inventory for product: {}", productId);
            UpdateInventoryResponse response = inventoryService.getInventoryByProductId(productId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving inventory: {}", e.getMessage());
            UpdateInventoryResponse errorResponse = UpdateInventoryResponse.builder()
                    .message("Error: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Inventory Service is running");
    }
}
