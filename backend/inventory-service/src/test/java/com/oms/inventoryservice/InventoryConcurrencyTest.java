package com.oms.inventoryservice;

import com.oms.inventoryservice.entity.Inventory;
import com.oms.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class InventoryConcurrencyTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    public void testOptimisticLocking() throws InterruptedException {
        // 1. Tạo một sản phẩm trong kho
        Inventory inventory = Inventory.builder()
                .productId("test-product-123")
                .availableQuantity(100)
                .reservedQuantity(0)
                .lowStockThreshold(10)
                .build();
        inventory = inventoryRepository.save(inventory);
        String inventoryId = inventory.getId();

        // 2. Tạo 2 luồng cùng cập nhật 1 bản ghi
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.execute(() -> {
                try {
                    // Đọc dữ liệu
                    Inventory inv = inventoryRepository.findById(inventoryId).get();
                    
                    // Giả lập xử lý chậm một chút để 2 luồng cùng đọc 1 version
                    Thread.sleep(100);
                    
                    // Cập nhật
                    inv.setAvailableQuantity(inv.getAvailableQuantity() - 10);
                    inventoryRepository.save(inv);
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // 3. Kiểm tra kết quả
        // Vì có khóa lạc quan, chỉ 1 luồng thành công, luồng kia phải lỗi version
        assertEquals(1, successCount.get(), "Chỉ nên có 1 luồng cập nhật thành công");
        assertEquals(1, failureCount.get(), "Nên có 1 luồng thất bại do xung đột version (Optimistic Locking)");
        
        Inventory finalInv = inventoryRepository.findById(inventoryId).get();
        assertEquals(90, finalInv.getAvailableQuantity(), "Số lượng cuối cùng phải là 90 (chỉ 1 lần giảm 10)");
    }
}
