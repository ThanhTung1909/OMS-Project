package com.oms.inventoryservice.listener;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.dto.InventoryCommand;
import com.oms.common.dto.InventoryResultPayload;
import com.oms.inventoryservice.config.RabbitMQConfig;
import com.oms.inventoryservice.dto.ConfirmOrderCommand;
import com.oms.inventoryservice.dto.RollbackOrderCommand;
import com.oms.inventoryservice.entity.Inventory;
import com.oms.inventoryservice.entity.ProcessedEvent;
import com.oms.inventoryservice.repository.InventoryRepository;
import com.oms.inventoryservice.repository.ProcessedEventRepository;
import com.oms.inventoryservice.entity.InventoryAuditLog;
import com.oms.inventoryservice.repository.InventoryAuditLogRepository;
import com.oms.inventoryservice.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.oms.common.constant.RedisConstants;

import java.util.Optional;

/**
 * Event Listener cho các command từ Orchestrator
 * Xử lý logic reserve, confirm và rollback inventory với cơ chế Idempotency
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

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private InventoryService inventoryService;

    /**
     * Xử lý lệnh giữ hàng (RESERVE) từ Orchestrator
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_INVENTORY_RESERVE)
    @Transactional
    public void handleReserveOrder(InventoryCommand command) {
        log.info("[INVENTORY] Nhận lệnh RESERVE cho đơn hàng: {}, Sản phẩm: {}, Số lượng: {}", 
                command.getOrderId(), command.getProductId(), command.getQuantity());

        try {
            // 1. Kiểm tra Idempotency
            if (processedEventRepository.existsByOrderId(command.getOrderId()) 
                && processedEventRepository.findByOrderId(command.getOrderId()).get().getEventType() == ProcessedEvent.EventType.RESERVE) {
                log.warn("[INVENTORY] Đơn hàng {} đã được xử lý RESERVE. Bỏ qua.", command.getOrderId());
                return;
            }

            // 2. Tìm kho và kiểm tra tồn kho
            Inventory inventory = inventoryRepository.findByProductId(command.getProductId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm trong kho: " + command.getProductId()));

            if (inventory.getAvailableQuantity() < command.getQuantity()) {
                throw new RuntimeException("Không đủ hàng trong kho. Còn lại: " + inventory.getAvailableQuantity());
            }

            // 3. Thực hiện giữ hàng (Reserve)
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - command.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + command.getQuantity());
            Inventory savedInventory = inventoryRepository.save(inventory);

            // Check and publish low stock alert
            inventoryService.checkAndPublishLowStockAlert(savedInventory);

            // Cập nhật lên Redis (CQRS)
            updateRedisStock(inventory.getProductId(), inventory.getAvailableQuantity());

            // 4. Lưu Audit Log
            auditLogRepository.save(InventoryAuditLog.builder()
                    .productId(command.getProductId())
                    .quantity(command.getQuantity())
                    .type("RESERVE")
                    .message("Giữ hàng cho đơn hàng: " + command.getOrderId())
                    .build());

            // 5. Đánh dấu đã xử lý (Idempotency)
            processedEventRepository.save(ProcessedEvent.builder()
                    .orderId(command.getOrderId())
                    .eventType(ProcessedEvent.EventType.RESERVE)
                    .productId(command.getProductId())
                    .quantity(command.getQuantity())
                    .build());

            // 6. Gửi phản hồi THÀNH CÔNG về Orchestrator
            InventoryResultPayload reply = InventoryResultPayload.builder()
                    .orderId(command.getOrderId())
                    .status("SUCCESS")
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_REPLY_RESULT, reply);
            log.info("[INVENTORY] Đã bắn phản hồi SUCCESS cho đơn hàng: {}", command.getOrderId());

        } catch (Exception e) {
            log.error("[INVENTORY] Lỗi xử lý RESERVE cho đơn hàng: {}: {}", command.getOrderId(), e.getMessage());
            
            // Gửi phản hồi THẤT BẠI về Orchestrator
            InventoryResultPayload reply = InventoryResultPayload.builder()
                    .orderId(command.getOrderId())
                    .status("FAILURE")
                    .message(e.getMessage())
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_REPLY_RESULT, reply);
        }
    }

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
            // BƯỚC 1: Check Idempotency - xem orderId + CONFIRM này đã được xử lý chưa?
            if (processedEventRepository.existsByOrderIdAndEventType(command.getOrderId(), ProcessedEvent.EventType.CONFIRM)) {
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

            // Cập nhật lên Redis (CQRS)
            updateRedisStock(inventory.getProductId(), inventory.getAvailableQuantity());

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
            // BƯỚC 1: Check Idempotency - xem orderId + ROLLBACK này đã được xử lý chưa?
            if (processedEventRepository.existsByOrderIdAndEventType(command.getOrderId(), ProcessedEvent.EventType.ROLLBACK)) {
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

            // BƯỚC 3 & 4: Thực hiện rollback - kiểm tra xem đơn đã CONFIRM chưa để trả lại hàng lên kệ đúng cơ chế
            if (processedEventRepository.existsByOrderIdAndEventType(command.getOrderId(), ProcessedEvent.EventType.CONFIRM)) {
                log.info("[INVENTORY] Đơn hàng {} đã CONFIRM trước đó. Thực hiện hoàn kho khả dụng trực tiếp cho sản phẩm {} (availableQuantity + {}).", 
                        command.getOrderId(), command.getProductId(), command.getQuantity());
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + command.getQuantity());
            } else {
                if (inventory.getReservedQuantity() < command.getQuantity()) {
                    log.error("Insufficient reserved quantity to rollback. Reserved: {}, Required: {}",
                            inventory.getReservedQuantity(), command.getQuantity());
                    return;
                }
                inventory.setReservedQuantity(inventory.getReservedQuantity() - command.getQuantity());
                inventory.setAvailableQuantity(inventory.getAvailableQuantity() + command.getQuantity());
            }

            // Lưu Inventory cập nhật
            inventoryRepository.save(inventory);

            // Cập nhật lên Redis (CQRS)
            updateRedisStock(inventory.getProductId(), inventory.getAvailableQuantity());

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

    private void updateRedisStock(String productId, int quantity) {
        try {
            String redisKey = RedisConstants.PREFIX_INVENTORY_STOCK + productId;
            stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(quantity));
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật tồn kho lên Redis cho sản phẩm {}: {}", productId, e.getMessage());
        }
    }
}
