package com.oms.inventoryservice.service;

import com.oms.inventoryservice.dto.UpdateInventoryRequest;
import com.oms.inventoryservice.dto.UpdateInventoryResponse;
import com.oms.inventoryservice.entity.Inventory;
import com.oms.inventoryservice.repository.InventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    /**
     * Cập nhật số lượng tồn kho theo loại
     * @param request Yêu cầu cập nhật
     * @return Thông tin tồn kho sau khi cập nhật
     */
    @Transactional
    public UpdateInventoryResponse updateInventory(UpdateInventoryRequest request) {
        log.info("Updating inventory for product: {}, type: {}, quantity: {}", 
                 request.getProductId(), request.getType(), request.getQuantity());

        // Tìm inventory theo productId hoặc tạo mới
        Optional<Inventory> existingInventory = inventoryRepository.findByProductId(request.getProductId());
        Inventory inventory;

        if (existingInventory.isEmpty()) {
            // Tạo mới nếu chưa tồn tại
            inventory = Inventory.builder()
                    .productId(request.getProductId())
                    .availableQuantity(0)
                    .reservedQuantity(0)
                    .build();
        } else {
            inventory = existingInventory.get();
        }

        // Cập nhật theo loại
        String message = "";
        switch (request.getType()) {
            case "ADD":
                // Thêm số lượng có sẵn (nhập kho)
                if (request.getQuantity() > 0) {
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantity());
                    message = "Added " + request.getQuantity() + " units to available quantity";
                } else {
                    throw new IllegalArgumentException("Add quantity must be positive");
                }
                break;

            case "REDUCE":
                // Giảm số lượng có sẵn (xuất kho)
                if (request.getQuantity() > 0 && inventory.getAvailableQuantity() >= request.getQuantity()) {
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
                    message = "Reduced " + request.getQuantity() + " units from available quantity";
                } else {
                    throw new IllegalArgumentException("Invalid reduce quantity or insufficient stock");
                }
                break;

            case "RESERVE":
                // Đặt trước (chuyển từ available sang reserved)
                if (request.getQuantity() > 0 && inventory.getAvailableQuantity() >= request.getQuantity()) {
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());
                    message = "Reserved " + request.getQuantity() + " units";
                } else {
                    throw new IllegalArgumentException("Insufficient available quantity to reserve");
                }
                break;

            case "RELEASE":
                // Giải phóng (chuyển từ reserved sang available)
                if (request.getQuantity() > 0 && inventory.getReservedQuantity() >= request.getQuantity()) {
                    inventory.setReservedQuantity(inventory.getReservedQuantity() - request.getQuantity());
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantity());
                    message = "Released " + request.getQuantity() + " reserved units";
                } else {
                    throw new IllegalArgumentException("Insufficient reserved quantity to release");
                }
                break;

            default:
                throw new IllegalArgumentException("Invalid update type: " + request.getType());
        }

        // Lưu vào database
        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Inventory updated successfully for product: {}", request.getProductId());

        // Trả về response
        return UpdateInventoryResponse.builder()
                .id(updatedInventory.getId())
                .productId(updatedInventory.getProductId())
                .availableQuantity(updatedInventory.getAvailableQuantity())
                .reservedQuantity(updatedInventory.getReservedQuantity())
                .totalQuantity(updatedInventory.getAvailableQuantity() + updatedInventory.getReservedQuantity())
                .updatedAt(updatedInventory.getUpdatedAt())
                .message(message)
                .build();
    }

    /**
     * Lấy thông tin tồn kho theo productId
     */
    public UpdateInventoryResponse getInventoryByProductId(String productId) {
        Optional<Inventory> inventory = inventoryRepository.findByProductId(productId);

        if (inventory.isEmpty()) {
            return UpdateInventoryResponse.builder()
                    .productId(productId)
                    .availableQuantity(0)
                    .reservedQuantity(0)
                    .totalQuantity(0)
                    .message("Product not found in inventory")
                    .build();
        }

        Inventory inv = inventory.get();
        return UpdateInventoryResponse.builder()
                .id(inv.getId())
                .productId(inv.getProductId())
                .availableQuantity(inv.getAvailableQuantity())
                .reservedQuantity(inv.getReservedQuantity())
                .totalQuantity(inv.getAvailableQuantity() + inv.getReservedQuantity())
                .updatedAt(inv.getUpdatedAt())
                .message("Inventory retrieved successfully")
                .build();
    }
}
