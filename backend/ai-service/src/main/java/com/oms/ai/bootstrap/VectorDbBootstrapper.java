package com.oms.ai.bootstrap;

import com.oms.ai.service.VectorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorDbBootstrapper implements CommandLineRunner {

    private final VectorSyncService syncService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void run(String... args) {
        log.info("[BOOTSTRAP] Kích hoạt tự động đồng bộ hóa Vector DB khi khởi tạo dịch vụ...");

        // Bật notify-keyspace-events trên Redis
        try {
            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                connection.setConfig("notify-keyspace-events", "KEA");
                return null;
            });
            log.info("[BOOTSTRAP] Đã cấu hình notify-keyspace-events = KEA trên Redis thành công.");
        } catch (Exception e) {
            log.warn("[BOOTSTRAP] Không thể cấu hình notify-keyspace-events tự động (đây là cảnh báo, không phải lỗi nghiêm trọng): {}", e.getMessage());
        }

        // Chạy đồng bộ hóa lần đầu sau 10 giây trên luồng riêng để Eureka kịp cập nhật các instance
        executorService.schedule(this::tryBootstrap, 10, TimeUnit.SECONDS);
    }

    private void tryBootstrap() {
        log.info("[BOOTSTRAP] Bắt đầu tiến hành đồng bộ Vector DB...");
        int attempts = 0;
        int maxAttempts = 15;
        boolean success = false;

        while (attempts < maxAttempts && !success) {
            attempts++;
            try {
                int count = syncService.bootstrapProducts();
                if (count > 0) {
                    log.info("[BOOTSTRAP] Hoàn tất đồng bộ Vector DB thành công với {} sản phẩm.", count);
                    success = true;
                } else {
                    log.warn("[BOOTSTRAP] Kết quả đồng bộ 0 sản phẩm hoặc lỗi kết nối. Thử lại sau 5 giây (Lần thử {}/{})", attempts, maxAttempts);
                    Thread.sleep(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[BOOTSTRAP] Tiến trình đồng bộ bị ngắt quãng: ", e);
                break;
            } catch (Exception e) {
                log.error("[BOOTSTRAP] Lỗi khi đồng bộ Vector DB (Lần thử {}/{}): {}", attempts, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!success) {
            log.error("[BOOTSTRAP] Hoàn tất quá trình thử đồng bộ nhưng không thành công sau {} lần.", maxAttempts);
        }
        
        // Shutdown executor
        executorService.shutdown();
    }
}
