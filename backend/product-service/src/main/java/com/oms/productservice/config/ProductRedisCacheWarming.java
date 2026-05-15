package com.oms.productservice.config;

import com.oms.common.constant.RedisConstants;
import com.oms.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductRedisCacheWarming implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[CACHE-WARMING] Đang nạp giá sản phẩm từ DB lên Redis...");
        
        try {
            productRepository.findAll().forEach(product -> {
                String priceKey = RedisConstants.PREFIX_PRODUCT_PRICE + product.getId();
                stringRedisTemplate.opsForValue().set(priceKey, product.getPrice().toString());
                
                String nameKey = RedisConstants.PREFIX_PRODUCT_NAME + product.getId();
                stringRedisTemplate.opsForValue().set(nameKey, product.getName());

                if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                    String imageKey = RedisConstants.PREFIX_PRODUCT_IMAGE + product.getId();
                    stringRedisTemplate.opsForValue().set(imageKey, product.getImageUrl().get(0));
                }
            });
            log.info("[CACHE-WARMING] Hoàn tất nạp giá sản phẩm lên Redis.");
        } catch (Exception e) {
            log.error("[CACHE-WARMING] Lỗi khi nạp giá lên Redis: {}", e.getMessage());
        }
    }
}
