package com.oms.ai.listener;

import com.oms.ai.service.VectorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisKeyspaceNotificationListener implements MessageListener {

    private final VectorSyncService syncService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();
        log.info("[REDIS KEYSPACE] Phát hiện thay đổi trên key: {}", key);

        try {
            // Các key chúng ta quan tâm: product:name:<id>, product:price:<id>, inventory:stock:<id>
            String productId = null;
            if (key.startsWith("product:name:")) {
                productId = key.substring("product:name:".length());
            } else if (key.startsWith("product:price:")) {
                productId = key.substring("product:price:".length());
            } else if (key.startsWith("inventory:stock:")) {
                productId = key.substring("inventory:stock:".length());
            }

            if (productId != null && !productId.isEmpty()) {
                log.info("[REDIS KEYSPACE] Đồng bộ hóa sản phẩm ID: {} do thay đổi ở key: {}", productId, key);
                syncService.syncSingleProduct(productId);
            }
        } catch (Exception e) {
            log.error("[REDIS KEYSPACE] Lỗi khi xử lý sự kiện keyspace cho key {}: ", key, e);
        }
    }
}
