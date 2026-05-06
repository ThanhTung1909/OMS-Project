package com.oms.inventoryservice.listener;

import com.oms.inventoryservice.config.RabbitMQConfig;
import com.oms.inventoryservice.dto.ConfirmOrderCommand;
import com.oms.inventoryservice.dto.RollbackOrderCommand;
import com.oms.inventoryservice.entity.Inventory;
import com.oms.inventoryservice.entity.ProcessedEvent;
import com.oms.inventoryservice.repository.InventoryRepository;
import com.oms.inventoryservice.repository.ProcessedEventRepository;
import com.oms.inventoryservice.entity.InventoryAuditLog;
import com.oms.inventoryservice.repository.InventoryAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Event Listener cho các command từ Order Service
 * Xử lý logic confirm và rollback inventory với cơ chế Idempotency
 */
@Slf4j
@Service
public class InventoryEventListener {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private InventoryAuditLogRepository auditLogRepository;

    /**
     * Task 3.9: Xử lý Confirm Order Command
     * Logic: Khách đã trả tiền. Trừ thẳng vào reservedQuantity
     * Bắt buộc: Idempotency check
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_INVENTORY_CONFIRM)
    @Transactional
    public void handleConfirmOrder(ConfirmOrderCommand command) {
        log.info("Received CONFIRM command: {}", command);

        try {
            // BƯỚC 1: Check Idempotency - xem orderId này đã được xử lý chưa?
            if (processedEventRepository.existsByOrderId(command.getOrderId())) {
                log.warn("Order {} already processed (CONFIRM). Skipping to prevent duplicate processing",
                        command.getOrderId());
                return;
            }

            // BƯỚC 2: Tìm Inventory theo productId
            Optional<Inventory> existingInventory = inventoryRepository.findByProductId(command.getProductId());

            if (existingInventory.isEmpty()) {
                log.error("Product {} not found in inventory", command.getProductId());
                return;
            }

            Inventory inventory = existingInventory.get();

            // BƯỚC 3: Validate - check xem reservedQuantity có đủ không
            if (inventory.getReservedQuantity() < command.getQuantity()) {
                log.error("Insufficient reserved quantity. Reserved: {}, Required: {}",
                        inventory.getReservedQuantity(), command.getQuantity());
                return;
            }

            // BƯỚC 4: Thực hiện confirm - trừ reservedQuantity
            // Logic: Khách đã trả tiền - hàng không còn "giữ" nữa, trừ từ reserved thẳng luôn
            inventory.setReservedQuantity(inventory.getReservedQuantity() - command.getQuantity());

            // Lưu Inventory cập nhật
            inventoryRepository.save(inventory);
            log.info("Inventory confirmed: productId={}, quantity reduced={}, remainingReserved={}",
                    command.getProductId(), command.getQuantity(), inventory.getReservedQuantity());

            // Lưu Audit Log
            auditLogRepository.save(InventoryAuditLog.builder()
                    .productId(command.getProductId())
                    .quantity(command.getQuantity())
                    .type("CONFIRM")
                    .message("Order confirmed: " + command.getOrderId())
                    .build());

            // BƯỚC 5: Lưu event đã xử lý (Idempotency tracking)
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .orderId(command.getOrderId())
                    .eventType(ProcessedEvent.EventType.CONFIRM)
                    .productId(command.getProductId())
                    .quantity(command.getQuantity())
                    .build();

            processedEventRepository.save(processedEvent);
            log.info("Processed event recorded for orderId: {}", command.getOrderId());

        } catch (Exception e) {
            log.error("Error processing CONFIRM command for orderId: {}", command.getOrderId(), e);
            // Không throw exception để tránh re-queue message quá sâu
            // Có thể log vào database hoặc alerting system
        }
    }

    /**
     * Task 3.10: Xử lý Rollback Order Command
     * Logic: Đơn huỷ. Trả lại hàng lên kệ: availableQuantity tăng lại, reservedQuantity giảm
     * Bắt buộc: Idempotency check
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_INVENTORY_ROLLBACK)
    @Transactional
    public void handleRollbackOrder(RollbackOrderCommand command) {
        log.info("Received ROLLBACK command: {}", command);

        try {
            // BƯỚC 1: Check Idempotency - xem orderId này đã được xử lý chưa?
            if (processedEventRepository.existsByOrderId(command.getOrderId())) {
                log.warn("Order {} already processed (ROLLBACK). Skipping to prevent duplicate processing",
                        command.getOrderId());
                return;
            }

            // BƯỚC 2: Tìm Inventory theo productId
            Optional<Inventory> existingInventory = inventoryRepository.findByProductId(command.getProductId());

            if (existingInventory.isEmpty()) {
                log.error("Product {} not found in inventory", command.getProductId());
                return;
            }

            Inventory inventory = existingInventory.get();

            // BƯỚC 3: Validate - check xem reservedQuantity có đủ không
            if (inventory.getReservedQuantity() < command.getQuantity()) {
                log.error("Insufficient reserved quantity to rollback. Reserved: {}, Required: {}",
                        inventory.getReservedQuantity(), command.getQuantity());
                return;
            }

            // BƯỚC 4: Thực hiện rollback - trả lại hàng lên kệ
            // Logic: reservedQuantity giảm, availableQuantity tăng
            inventory.setReservedQuantity(inventory.getReservedQuantity() - command.getQuantity());
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + command.getQuantity());

            // Lưu Inventory cập nhật
            inventoryRepository.save(inventory);
            log.info("Inventory rollback: productId={}, quantity restored={}, available now={}, reserved now={}",
                    command.getProductId(), command.getQuantity(),
                    inventory.getAvailableQuantity(), inventory.getReservedQuantity());

            // Lưu Audit Log
            auditLogRepository.save(InventoryAuditLog.builder()
                    .productId(command.getProductId())
                    .quantity(command.getQuantity())
                    .type("ROLLBACK")
                    .message("Order rollback: " + command.getOrderId())
                    .build());

            // BƯỚC 5: Lưu event đã xử lý (Idempotency tracking)
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                    .orderId(command.getOrderId())
                    .eventType(ProcessedEvent.EventType.ROLLBACK)
                    .productId(command.getProductId())
                    .quantity(command.getQuantity())
                    .build();

            processedEventRepository.save(processedEvent);
            log.info("Processed event recorded for orderId: {}", command.getOrderId());

        } catch (Exception e) {
            log.error("Error processing ROLLBACK command for orderId: {}", command.getOrderId(), e);
            // Không throw exception để tránh re-queue message quá sâu
            // Có thể log vào database hoặc alerting system
        }
    }
}
