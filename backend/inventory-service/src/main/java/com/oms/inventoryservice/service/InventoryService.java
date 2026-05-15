package com.oms.inventoryservice.service;

import com.oms.inventoryservice.dto.UpdateInventoryRequest;
import com.oms.inventoryservice.dto.UpdateInventoryResponse;
import com.oms.inventoryservice.entity.Inventory;
import com.oms.inventoryservice.repository.InventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.oms.common.constant.RedisConstants;

import com.oms.common.AppException;
import com.oms.inventoryservice.exception.InventoryErrorCode;
import com.oms.common.CommonErrorCode;
import com.oms.inventoryservice.client.ProductClient;
import com.oms.inventoryservice.entity.InventoryAuditLog;
import com.oms.inventoryservice.repository.InventoryAuditLogRepository;
import com.oms.common.ApiResponse;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductClient productClient;

    @Autowired
    private InventoryAuditLogRepository auditLogRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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
                    // Xác thực sản phẩm tồn tại qua Feign
                    try {
                        ApiResponse<Object> productResponse = productClient.getProductById(request.getProductId());
                        if (productResponse == null || !productResponse.isSuccess()) {
                            throw new AppException(CommonErrorCode.NOT_FOUND);
                        }
                    } catch (Exception e) {
                        log.error("Error verifying product with Product Service: {}", e.getMessage());
                        throw new AppException(CommonErrorCode.NOT_FOUND);
                    }

                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantity());
                    message = "Added " + request.getQuantity() + " units to available quantity";
                } else {
                    throw new AppException(CommonErrorCode.INVALID_INPUT);
                }
                break;

            case "REDUCE":
                // Giảm số lượng có sẵn (xuất kho)
                if (request.getQuantity() > 0 && inventory.getAvailableQuantity() >= request.getQuantity()) {
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
                    message = "Reduced " + request.getQuantity() + " units from available quantity";
                } else {
                    throw new AppException(InventoryErrorCode.INSUFFICIENT_STOCK);
                }
                break;

            case "RESERVE":
                // Đặt trước (chuyển từ available sang reserved)
                if (request.getQuantity() > 0 && inventory.getAvailableQuantity() >= request.getQuantity()) {
                    // Xác thực sản phẩm tồn tại qua Feign trước khi reserve
                    try {
                        ApiResponse<Object> productResponse = productClient.getProductById(request.getProductId());
                        if (productResponse == null || !productResponse.isSuccess()) {
                            throw new AppException(CommonErrorCode.NOT_FOUND);
                        }
                    } catch (Exception e) {
                        log.error("Error verifying product with Product Service for RESERVE: {}", e.getMessage());
                        throw new AppException(CommonErrorCode.NOT_FOUND);
                    }

                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());
                    message = "Reserved " + request.getQuantity() + " units";
                } else {
                    throw new AppException(InventoryErrorCode.INSUFFICIENT_STOCK);
                }
                break;

            case "RELEASE":
                // Giải phóng (chuyển từ reserved sang available)
                if (request.getQuantity() > 0 && inventory.getReservedQuantity() >= request.getQuantity()) {
                    inventory.setReservedQuantity(inventory.getReservedQuantity() - request.getQuantity());
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantity());
                    message = "Released " + request.getQuantity() + " reserved units";
                } else {
                    throw new AppException(InventoryErrorCode.INSUFFICIENT_STOCK);
                }
                break;

            default:
                throw new AppException(CommonErrorCode.INVALID_INPUT);
        }

        // Lưu vào database
        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Inventory updated successfully for product: {}", request.getProductId());

        // Cập nhật lên Redis (CQRS - Shared Redis)
        try {
            String redisKey = RedisConstants.PREFIX_INVENTORY_STOCK + updatedInventory.getProductId();
            stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(updatedInventory.getAvailableQuantity()));
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật tồn kho lên Redis cho sản phẩm {}: {}", updatedInventory.getProductId(), e.getMessage());
        }

        // Lưu Audit Log
        InventoryAuditLog auditLog = InventoryAuditLog.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .type(request.getType())
                .message(message)
                .build();
        auditLogRepository.save(auditLog);

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
    /**
     * Lấy danh sách sản phẩm sắp hết hàng
     */
    public List<UpdateInventoryResponse> getLowStockProducts() {
        return inventoryRepository.findAll().stream()
                .filter(inv -> inv.getAvailableQuantity() < inv.getLowStockThreshold())
                .map(inv -> UpdateInventoryResponse.builder()
                        .id(inv.getId())
                        .productId(inv.getProductId())
                        .availableQuantity(inv.getAvailableQuantity())
                        .reservedQuantity(inv.getReservedQuantity())
                        .totalQuantity(inv.getAvailableQuantity() + inv.getReservedQuantity())
                        .updatedAt(inv.getUpdatedAt())
                        .message("LOW STOCK ALERT: Below threshold of " + inv.getLowStockThreshold())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Giữ kho hàng loạt (dùng cho tạo đơn hàng - SAGA)
     */
    @Transactional
    public void reserveBulk(List<com.oms.inventoryservice.dto.InventoryReserveRequest> requests) {
        log.info("Processing bulk reservation for {} items", requests.size());
        for (com.oms.inventoryservice.dto.InventoryReserveRequest req : requests) {
            UpdateInventoryRequest updateReq = UpdateInventoryRequest.builder()
                    .productId(req.getProductId())
                    .quantity(req.getQuantity())
                    .type("RESERVE")
                    .build();
            updateInventory(updateReq);
        }
    }

    /**
     * Lấy số lượng tồn kho khả dụng cho nhiều sản phẩm trong một lần gọi.
     * Sử dụng SQL IN (...) thông qua findByProductIdIn.
     * @param productIds Danh sách productId cần kiểm tra
     * @return Map<productId, availableQuantity>
     */
    public Map<String, Integer> getBulkStock(List<String> productIds) {
        log.info("Fetching bulk stock for {} product(s)", productIds.size());
        List<Inventory> inventories = inventoryRepository.findByProductIdIn(productIds);
        return inventories.stream()
                .collect(Collectors.toMap(
                        Inventory::getProductId,
                        Inventory::getAvailableQuantity
                ));
    }
}
