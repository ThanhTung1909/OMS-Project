package com.oms.ai.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSyncService {

    private final RestTemplate restTemplate;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore redisEmbeddingStore;
    private final StringRedisTemplate stringRedisTemplate;

    private String getVectorIdKey(String productId) {
        return "ai:product:vector-id:" + productId;
    }

    /**
     * Xóa vector cũ khỏi Redis Vector DB bằng cách xóa trực tiếp key JSON
     * vì RedisEmbeddingStore 0.35.0 chưa hỗ trợ phương thức remove()
     */
    private void removeOldVector(String oldVectorId, String productLabel) {
        try {
            // LangChain4j RedisEmbeddingStore lưu vector dưới key: embedding:<vectorId>
            String embeddingKey = "embedding:" + oldVectorId;
            Boolean deleted = stringRedisTemplate.delete(embeddingKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("[AI SERVICE] Đã xóa vector cũ '{}' cho sản phẩm '{}'", oldVectorId, productLabel);
            }
        } catch (Exception ex) {
            log.warn("[AI SERVICE] Không thể xóa vector cũ {} cho '{}': {}", oldVectorId, productLabel, ex.getMessage());
        }
    }

    /**
     * Đồng bộ hóa toàn bộ sản phẩm từ product-service sang Redis Vector DB
     */
    public int bootstrapProducts() throws Exception {
        log.info("[AI SERVICE] Bắt đầu đồng bộ hóa toàn bộ sản phẩm từ product-service...");
        int count = 0;
        String url = "http://product-service/api/v1/products?size=1000";
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            log.error("[AI SERVICE] Không thể gọi product-service hoặc phản hồi thất bại.");
            return 0;
        }

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null) {
            log.warn("[AI SERVICE] Không tìm thấy dữ liệu 'result' trong phản hồi.");
            return 0;
        }

        List<Map<String, Object>> products = (List<Map<String, Object>>) result.get("content");
        if (products == null || products.isEmpty()) {
            log.warn("[AI SERVICE] Danh sách sản phẩm rỗng.");
            return 0;
        }

        for (Map<String, Object> product : products) {
            String id = (String) product.get("id");
            String name = (String) product.get("name");
            String description = (String) product.get("description");
            Object priceObj = product.get("price");
            double price = priceObj != null ? ((Number) priceObj).doubleValue() : 0.0;
            String categoryName = (String) product.get("categoryName");
            Object stockQtyObj = product.get("stockQuantity");
            int stockQuantity = stockQtyObj != null ? ((Number) stockQtyObj).intValue() : 0;

            // Tạo snippet text đại diện cho sản phẩm để làm RAG
            String textSegmentContent = String.format(
                    "Tên sản phẩm: %s\nGiá: %,.0f VNĐ\nPhân loại: %s\nSố lượng tồn kho: %d chiếc\nMô tả: %s",
                    name, price, categoryName != null ? categoryName : "Chưa phân loại", stockQuantity, description != null ? description : ""
            );

            // Cấu hình metadata để trích xuất sau này
            Map<String, String> metadata = new HashMap<>();
            metadata.put("productId", id);
            metadata.put("productName", name);
            metadata.put("price", String.valueOf(price));
            metadata.put("stockQuantity", String.valueOf(stockQuantity));
            metadata.put("description", description != null ? description : "");

            TextSegment segment = TextSegment.from(textSegmentContent, dev.langchain4j.data.document.Metadata.from(metadata));
            
            // Tạo Embedding vector
            Embedding embedding = embeddingModel.embed(textSegmentContent).content();
            
            // Kiểm tra và xóa vector cũ nếu đã tồn tại để tránh trùng lặp
            String vectorIdKey = getVectorIdKey(id);
            String oldVectorId = stringRedisTemplate.opsForValue().get(vectorIdKey);
            if (oldVectorId != null && !oldVectorId.isEmpty()) {
                removeOldVector(oldVectorId, name);
            }

            // Lưu vào Vector DB Redis Stack (nhận về ID ngẫu nhiên)
            String newVectorId = redisEmbeddingStore.add(embedding, segment);
            
            // Lưu ánh xạ ID vào Redis
            stringRedisTemplate.opsForValue().set(vectorIdKey, newVectorId);
            count++;
        }
        log.info("[AI SERVICE] Đồng bộ hóa thành công {} sản phẩm vào Redis Vector DB.", count);
        return count;
    }

    private void deleteProductFromVectorDb(String productId) {
        String vectorIdKey = getVectorIdKey(productId);
        String oldVectorId = stringRedisTemplate.opsForValue().get(vectorIdKey);
        if (oldVectorId != null && !oldVectorId.isEmpty()) {
            removeOldVector(oldVectorId, productId);
            stringRedisTemplate.delete(vectorIdKey);
            log.info("[AI SERVICE] Đã xóa sản phẩm ID: {} khỏi Redis Vector DB.", productId);
        }
    }

    /**
     * Đồng bộ hóa hoặc cập nhật 1 sản phẩm duy nhất vào Redis Vector DB
     */
    public void syncSingleProduct(String productId) {
        log.info("[AI SERVICE] Bắt đầu cập nhật thời gian thực sản phẩm ID: {}", productId);
        try {
            String url = "http://product-service/api/v1/products/" + productId;
            Map<String, Object> response = null;
            try {
                response = restTemplate.getForObject(url, Map.class);
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                log.warn("[AI SERVICE] Sản phẩm ID {} không tồn tại trên product-service (404). Tiến hành xóa khỏi Vector DB.", productId);
                deleteProductFromVectorDb(productId);
                return;
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                if (e.getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND) {
                    log.warn("[AI SERVICE] Sản phẩm ID {} không tồn tại trên product-service (404 status). Tiến hành xóa khỏi Vector DB.", productId);
                    deleteProductFromVectorDb(productId);
                } else {
                    log.warn("[AI SERVICE] Lỗi HTTP status {} khi gọi product-service cho ID {}. Giữ nguyên Vector DB để tránh mất dữ liệu.", e.getStatusCode(), productId);
                }
                return;
            } catch (Exception e) {
                log.warn("[AI SERVICE] Lỗi kết nối/mạng hoặc LoadBalancer khi gọi product-service cho ID {} (giữ nguyên Vector DB để tránh mất dữ liệu): {}", productId, e.getMessage());
                return;
            }

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                log.warn("[AI SERVICE] Phản hồi từ product-service cho ID {} không thành công hoặc trống. Giữ nguyên Vector DB.", productId);
                return;
            }

            Map<String, Object> product = (Map<String, Object>) response.get("result");
            if (product == null) {
                log.warn("[AI SERVICE] Không tìm thấy dữ liệu 'result' cho sản phẩm: {}", productId);
                return;
            }

            String id = (String) product.get("id");
            String name = (String) product.get("name");
            String description = (String) product.get("description");
            Object priceObj = product.get("price");
            double price = priceObj != null ? ((Number) priceObj).doubleValue() : 0.0;
            String categoryName = (String) product.get("categoryName");
            Object stockQtyObj = product.get("stockQuantity");
            int stockQuantity = stockQtyObj != null ? ((Number) stockQtyObj).intValue() : 0;

            String textSegmentContent = String.format(
                    "Tên sản phẩm: %s\nGiá: %,.0f VNĐ\nPhân loại: %s\nSố lượng tồn kho: %d chiếc\nMô tả: %s",
                    name, price, categoryName != null ? categoryName : "Chưa phân loại", stockQuantity, description != null ? description : ""
            );

            Map<String, String> metadata = new HashMap<>();
            metadata.put("productId", id);
            metadata.put("productName", name);
            metadata.put("price", String.valueOf(price));
            metadata.put("stockQuantity", String.valueOf(stockQuantity));
            metadata.put("description", description != null ? description : "");

            TextSegment segment = TextSegment.from(textSegmentContent, dev.langchain4j.data.document.Metadata.from(metadata));
            Embedding embedding = embeddingModel.embed(textSegmentContent).content();

            // Kiểm tra và xóa vector cũ nếu đã tồn tại để tránh trùng lặp
            String vectorIdKey = getVectorIdKey(id);
            String oldVectorId = stringRedisTemplate.opsForValue().get(vectorIdKey);
            if (oldVectorId != null && !oldVectorId.isEmpty()) {
                removeOldVector(oldVectorId, name);
            }

            // Ghi đè vào Redis Vector DB
            String newVectorId = redisEmbeddingStore.add(embedding, segment);
            stringRedisTemplate.opsForValue().set(vectorIdKey, newVectorId);
            log.info("[AI SERVICE] Đã cập nhật sản phẩm {} vào Redis Vector DB thành công (Tồn kho: {} chiếc).", name, stockQuantity);
        } catch (Exception e) {
            log.error("[AI SERVICE] Lỗi khi cập nhật sản phẩm single {}: ", productId, e);
        }
    }
}
