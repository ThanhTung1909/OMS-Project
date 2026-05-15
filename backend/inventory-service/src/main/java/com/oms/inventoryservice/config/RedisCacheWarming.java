package com.oms.inventoryservice.config;

import com.oms.common.constant.RedisConstants;
import com.oms.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheWarming implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) {
        log.info("[CACHE-WARMING] Đang bắt đầu nạp dữ liệu tồn kho từ DB lên Redis...");
        
        try {
            inventoryRepository.findAll().forEach(inventory -> {
                String redisKey = RedisConstants.PREFIX_INVENTORY_STOCK + inventory.getProductId();
                stringRedisTemplate.opsForValue().set(redisKey, String.valueOf(inventory.getAvailableQuantity()));
            });
            log.info("[CACHE-WARMING] Hoàn tất nạp dữ liệu tồn kho lên Redis.");
        } catch (Exception e) {
            log.error("[CACHE-WARMING] Lỗi khi nạp dữ liệu lên Redis: {}", e.getMessage());
        }
    }
}
