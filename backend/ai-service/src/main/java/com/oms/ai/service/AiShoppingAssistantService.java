package com.oms.ai.service;

import com.oms.ai.dto.ChatMessageResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiShoppingAssistantService {

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore redisEmbeddingStore;
    private final RestTemplate restTemplate;

    // Pattern để tìm orderId trong câu hỏi (ví dụ: ord-12345, ord-uuid-etc)
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?i)ord-[a-zA-Z0-9\\-]+");

    public ChatMessageResponse chat(String message, String userId) {
        log.info("[AI SERVICE] Nhận câu hỏi từ User ({}): {}", userId, message);

        String deliveryContext = "";
        String orderIdFound = null;

        // 1. Kiểm tra xem câu hỏi có chứa mã đơn hàng (orderId) hay không
        Matcher matcher = ORDER_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            orderIdFound = matcher.group();
            log.info("[AI SERVICE] Tìm thấy Order ID trong câu hỏi: {}", orderIdFound);
            deliveryContext = fetchDeliveryContext(orderIdFound);
        }

        List<ChatMessageResponse.ProductSuggestion> suggestions = new ArrayList<>();
        String productsContext = "";

        // 2. Nếu không phải câu hỏi tra cứu đơn hàng, thực hiện Tìm kiếm Tương đồng Sản phẩm (RAG)
        if (deliveryContext.isEmpty()) {
            log.info("[AI SERVICE] Tiến hành RAG tìm kiếm sản phẩm cho câu hỏi...");
            Embedding queryEmbedding = embeddingModel.embed(message).content();
            
            // Tìm kiếm top 3 sản phẩm khớp nhất
            List<EmbeddingMatch<TextSegment>> matches = redisEmbeddingStore.findRelevant(queryEmbedding, 3, 0.0);
            
            java.util.Set<String> processedProductIds = new java.util.HashSet<>();
            StringBuilder sb = new StringBuilder("Dưới đây là một số sản phẩm phù hợp được tìm thấy trong cửa hàng:\n");
            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                
                log.info("[AI SERVICE] Match score: {}, metadata: {}", match.score(), segment.metadata() != null ? segment.metadata().asMap() : "null");
                
                String prodId = segment.metadata().getString("productId");
                if (prodId == null || processedProductIds.contains(prodId)) {
                    continue; // Bỏ qua trùng lặp hoặc ID rỗng
                }
                processedProductIds.add(prodId);

                String text = segment.text();
                String prodName = segment.metadata().getString("productName");
                String priceStr = segment.metadata().getString("price");
                String desc = segment.metadata().getString("description");
                String stockQtyStr = segment.metadata().getString("stockQuantity");
                
                double price = 0.0;
                try {
                    if (priceStr != null) price = Double.parseDouble(priceStr);
                } catch (NumberFormatException ignored) {}

                int stockQuantity = 0;
                try {
                    if (stockQtyStr != null) stockQuantity = Integer.parseInt(stockQtyStr);
                } catch (NumberFormatException ignored) {}

                sb.append(text).append("\n---\n");

                suggestions.add(ChatMessageResponse.ProductSuggestion.builder()
                        .id(prodId)
                        .name(prodName)
                        .price(price)
                        .description(desc)
                        .stockQuantity(stockQuantity)
                        .build());
            }
            productsContext = sb.toString();
        }

        // 3. Xây dựng System Prompt cho Gemini
        String systemPrompt = "Bạn là trợ lý mua sắm AI (AI Shopping Assistant) cực kỳ thân thiện, chuyên nghiệp và lịch sự của hệ thống thương mại điện tử OMS.\n"
                + "Nhiệm vụ của bạn là tư vấn sản phẩm, kiểm tra tồn kho và giải đáp thắc mắc của khách hàng dựa trên ngữ cảnh dữ liệu cửa hàng và chính sách được cung cấp bên dưới.\n"
                + "Hãy trả lời trôi chảy, sử dụng tiếng Việt tự nhiên và Markdown để định dạng câu trả lời đẹp mắt.\n\n"
                + "=== CHÍNH SÁCH CỬA HÀNG (PURCHASE POLICIES) ===\n"
                + "- **Chính sách Giao hàng**: Miễn phí vận chuyển toàn quốc cho mọi đơn hàng từ 2.000.000 VNĐ trở lên. Đối với đơn hàng dưới 2.000.000 VNĐ, phí giao hàng đồng giá 30.000 VNĐ toàn quốc. Thời gian giao hàng dự kiến từ 2-4 ngày làm việc.\n"
                + "- **Chính sách Đổi trả**: Hỗ trợ 1-đổi-1 hoặc hoàn tiền trong vòng 7 ngày kể từ khi nhận sản phẩm nếu phát hiện lỗi từ nhà sản xuất. Sản phẩm yêu cầu còn nguyên hộp, phụ kiện, hóa đơn mua hàng và chưa qua sử dụng.\n"
                + "- **Phương thức Thanh toán**: Chấp nhận thanh toán qua Chuyển khoản ngân hàng trực tuyến (BANKING) hoặc Thanh toán khi nhận hàng (COD).\n"
                + "- **Chính sách Chống gian lận AI**: Hệ thống sử dụng trí tuệ nhân tạo để rà soát đơn hàng tự động. Các đơn hàng COD có giá trị giao dịch cực lớn hoặc thông tin liên hệ không chính xác (số điện thoại ảo, địa chỉ sơ sài) sẽ bị AI tự động từ chối đặt hàng để bảo đảm an toàn kho hàng.\n\n"
                + "=== NGỮ CẢNH DỮ LIỆU CỬA HÀNG ===\n";

        if (!deliveryContext.isEmpty()) {
            systemPrompt += deliveryContext;
        } else if (!productsContext.isEmpty() && !suggestions.isEmpty()) {
            systemPrompt += productsContext;
            systemPrompt += "\nHãy tư vấn chi tiết cho khách hàng về các sản phẩm trên, nhấn mạnh lý do tại sao chúng phù hợp với câu hỏi của khách hàng. Nếu khách hàng hỏi về số lượng hàng còn lại (tồn kho/số lượng khả dụng), hãy đọc thông tin 'Số lượng tồn kho' trong dữ liệu sản phẩm để trả lời chính xác.";
        } else {
            systemPrompt += "Không tìm thấy sản phẩm nào phù hợp trực tiếp. Hãy lịch sự phản hồi và hỏi rõ thêm nhu cầu của khách hàng, hoặc trả lời các câu hỏi về chính sách mua hàng nếu khách hàng đang hỏi về chúng.";
        }

        systemPrompt += "\n\nCâu hỏi của khách hàng: " + message;

        // 4. Gọi LLM sinh câu trả lời
        log.info("[AI SERVICE] Gọi Google Gemini sinh câu trả lời...");
        String reply;
        try {
            reply = chatModel.generate(systemPrompt);
        } catch (Exception e) {
            log.warn("[AI SERVICE] Không thể gọi Google Gemini (Lỗi: {}). Sử dụng fallback tạo câu trả lời cục bộ.", e.getMessage());
            reply = generateFallbackReply(message, suggestions, deliveryContext);
        }

        return ChatMessageResponse.builder()
                .reply(reply)
                .suggestions(suggestions)
                .build();
    }

    private String generateFallbackReply(String message, List<ChatMessageResponse.ProductSuggestion> suggestions, String deliveryContext) {
        if (deliveryContext != null && !deliveryContext.isEmpty()) {
            return "Xin chào! Tôi là trợ lý mua sắm AI của OMS. Về đơn hàng của bạn, tôi đã kiểm tra và tìm thấy thông tin giao hàng chi tiết:\n\n"
                    + deliveryContext
                    + "\nĐơn hàng đang được vận chuyển đúng lộ trình. Nếu bạn cần hỗ trợ thêm bất kỳ thông tin nào khác, vui lòng để lại câu hỏi tại đây nhé!";
        }

        if (suggestions != null && !suggestions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Xin chào! Tôi là trợ lý mua sắm AI của OMS. Dựa trên câu hỏi tìm kiếm của bạn (\"")
              .append(message)
              .append("\"), tôi xin đề xuất một số sản phẩm nổi bật và phù hợp nhất hiện có tại cửa hàng:\n\n");

            for (int i = 0; i < suggestions.size(); i++) {
                var p = suggestions.get(i);
                sb.append(String.format("%d. **%s**\n", i + 1, p.getName()))
                  .append(String.format("   - **Giá bán**: %,.0f VNĐ\n", p.getPrice()))
                  .append(String.format("   - **Mô tả**: %s\n\n", p.getDescription() != null ? p.getDescription() : "Sản phẩm chất lượng cao"));
            }

            sb.append("Các sản phẩm này đều rất phù hợp với tiêu chí của bạn. Bạn có thể nhấn vào danh sách đề xuất ở bên dưới để xem chi tiết và mua hàng. Nếu cần tư vấn thêm về cấu hình hay khuyến mãi, bạn hãy cứ hỏi tôi nhé!");
            return sb.toString();
        }

        return "Xin chào! Hiện tại tôi chưa tìm thấy sản phẩm nào khớp hoàn toàn với yêu cầu \"" + message + "\" của bạn. Bạn có thể cung cấp thêm một số chi tiết (như thương hiệu mong muốn, tầm giá hoặc nhu cầu sử dụng cụ thể) để tôi tìm kiếm chính xác hơn được không?";
    }

    /**
     * Tra cứu thông tin vận chuyển của đơn hàng từ delivery-service
     */
    private String fetchDeliveryContext(String orderId) {
        try {
            String url = "http://delivery-service/api/v1/deliveries/order/" + orderId;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                Map<String, Object> delivery = (Map<String, Object>) response.get("result");
                if (delivery != null) {
                    String tracking = (String) delivery.get("trackingNumber");
                    String status = (String) delivery.get("status");
                    String receiver = (String) delivery.get("receiverName");
                    String phone = (String) delivery.get("receiverPhone");
                    String address = (String) delivery.get("address");
                    String shipper = (String) delivery.get("shipperName");
                    String shipperPhone = (String) delivery.get("shipperPhone");
                    Object codObj = delivery.get("codAmount");
                    double cod = codObj != null ? ((Number) codObj).doubleValue() : 0.0;

                    return String.format(
                            "THÔNG TIN VẬN CHUYỂN ĐƠN HÀNG %s:\n"
                            + "- Trạng thái vận chuyển: %s\n"
                            + "- Mã vận đơn: %s\n"
                            + "- Người nhận: %s (SĐT: %s)\n"
                            + "- Địa chỉ giao hàng: %s\n"
                            + "- Tài xế giao hàng (Shipper): %s (SĐT: %s)\n"
                            + "- Số tiền cần thanh toán COD: %,.0f VNĐ\n",
                            orderId, translateStatus(status), tracking != null ? tracking : "Chưa có",
                            receiver, phone, address,
                            shipper != null ? shipper : "Đang điều phối", shipperPhone != null ? shipperPhone : "Chưa có",
                            cod
                    );
                }
            }
        } catch (Exception e) {
            log.warn("[AI SERVICE] Không thể lấy thông tin giao hàng cho đơn hàng {}: {}", orderId, e.getMessage());
        }
        return "";
    }

    private String translateStatus(String status) {
        if (status == null) return "Không rõ";
        switch (status.toUpperCase()) {
            case "ASSIGNED": return "Đã phân công tài xế";
            case "PICKED_UP": return "Đã lấy hàng, đang giao";
            case "DELIVERED": return "Đã giao hàng thành công";
            case "FAILED": return "Giao hàng thất bại";
            case "CANCELLED": return "Đã hủy đơn vận chuyển";
            default: return status;
        }
    }
}
