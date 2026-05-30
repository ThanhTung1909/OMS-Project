package com.oms.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.dto.FraudCheckCommand;
import com.oms.common.dto.FraudCheckReply;
import com.oms.ai.config.RabbitMQConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiFraudDetectionService {

    private final ChatLanguageModel chatModel;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_AI_FRAUD_CHECK)
    public void handleFraudCheckCommand(FraudCheckCommand command) {
        log.info("[AI FRAUD DETECTOR] Bắt đầu kiểm tra gian lận cho đơn hàng: {}", command.getOrderId());

        int fraudScore = 15; // Điểm mặc định cơ bản
        String status = "SAFE";
        String reason = "Đơn hàng bình thường.";

        boolean isHighValueCod = false;
        boolean isInvalidPhone = false;
        boolean isWeakAddress = false;
        boolean isPriceSuspicious = false;

        try {
            // 1. Phân tích Heuristics (Quy tắc cứng ban đầu)
            // Ví dụ: Đơn hàng COD có giá trị cực lớn trên 50,000,000 VNĐ là một dấu hiệu khả nghi ban đầu
            isHighValueCod = "COD".equalsIgnoreCase(command.getPaymentMethod()) && 
                    command.getTotalAmount().compareTo(new BigDecimal("50000000")) > 0;

            // Số điện thoại không hợp lệ (ít hơn 9 chữ số)
            String phone = command.getReceiverPhone() != null ? command.getReceiverPhone().replaceAll("\\s+", "") : "";
            isInvalidPhone = phone.length() < 9;

            // Địa chỉ nhận hàng quá sơ sài hoặc chung chung, không chuẩn định dạng hành chính Việt Nam
            String address = command.getAddress() != null ? command.getAddress().trim() : "";
            String addressLower = address.toLowerCase();
            
            boolean hasStreetIndicator = addressLower.contains("đường") || addressLower.contains("phố") || addressLower.contains("ngõ") 
                    || addressLower.contains("ngách") || addressLower.contains("hẻm") || addressLower.contains("số") 
                    || addressLower.contains("/") || addressLower.matches(".*\\d+.*");
            boolean hasWardCommune = addressLower.contains("phường") || addressLower.contains("xã") || addressLower.contains("thị trấn") 
                    || addressLower.contains("ấp") || addressLower.contains("thôn") || addressLower.contains("xóm");
            boolean hasDistrict = addressLower.contains("quận") || addressLower.contains("huyện") || addressLower.contains("thị xã");
            boolean hasProvinceCity = addressLower.contains("tỉnh") || addressLower.contains("thành phố") || addressLower.contains("tp");
            
            int administrativeScore = 0;
            if (hasStreetIndicator) administrativeScore++;
            if (hasWardCommune) administrativeScore++;
            if (hasDistrict) administrativeScore++;
            if (hasProvinceCity) administrativeScore++;

            isWeakAddress = address.length() < 15 || administrativeScore < 2 
                    || addressLower.equalsIgnoreCase("Việt Nam") 
                    || addressLower.equalsIgnoreCase("Hà Nội") 
                    || addressLower.equalsIgnoreCase("Hồ Chí Minh")
                    || (addressLower.contains("abc") && address.length() < 20);

            // Kiểm tra gian lận đơn giá sản phẩm sai lệch với giá trị thực tế thị trường tại Việt Nam (Price Tampering / Client Hack)
            if (command.getItems() != null) {
                for (var item : command.getItems()) {
                    if (item.getProductName() == null || item.getPrice() == null) continue;
                    String nameLower = item.getProductName().toLowerCase();
                    BigDecimal price = item.getPrice();

                    // Heuristics 1: Mặt hàng thời trang bình dân (áo thun, quần, váy, đầm, mũ, kính, túi...) có đơn giá quá cao (> 3,000,000 VNĐ) hoặc quá thấp (< 10,000 VNĐ)
                    if ((nameLower.contains("áo") || nameLower.contains("quần") || nameLower.contains("đầm") 
                            || nameLower.contains("váy") || nameLower.contains("túi") || nameLower.contains("kính") 
                            || nameLower.contains("dép") || nameLower.contains("giày") || nameLower.contains("thun"))
                            && (price.compareTo(new BigDecimal("3000000")) > 0 || price.compareTo(new BigDecimal("10000")) < 0)) {
                        isPriceSuspicious = true;
                    }

                    // Heuristics 2: Mặt hàng công nghệ cao cấp chính hãng (MacBook, Laptop, Galaxy S23, PS5, Bose, Garmin, Sony headphones, AirPods)
                    // có giá rẻ mạt đáng ngờ (< 200,000 VNĐ) hoặc quá đắt (> 200,000,000 VNĐ)
                    if ((nameLower.contains("laptop") || nameLower.contains("macbook") || nameLower.contains("điện thoại") 
                            || nameLower.contains("samsung") || nameLower.contains("playstation") || nameLower.contains("sony") 
                            || nameLower.contains("airpods") || nameLower.contains("bose") || nameLower.contains("garmin") 
                            || nameLower.contains("canon") || nameLower.contains("tivi"))
                            && (price.compareTo(new BigDecimal("200000")) < 0 || price.compareTo(new BigDecimal("200000000")) > 0)) {
                        isPriceSuspicious = true;
                    }
                }
            }

            // 2. Sử dụng Google Gemini AI để phân tích hành vi nâng cao
            String aiPrompt = "Bạn là một chuyên gia phân tích an ninh thương mại điện tử chuyên nghiệp (Cybersecurity & Fraud Analyst).\n"
                    + "Hãy phân tích chi tiết đơn hàng sau để tìm kiếm các dấu hiệu lừa đảo, giả mạo thông tin, đặt đơn ảo, phá hoại hoặc can thiệp chỉnh sửa giá (Price Tampering):\n\n"
                    + "=== CHI TIẾT ĐƠN HÀNG ===\n"
                    + "Mã đơn hàng: " + command.getOrderId() + "\n"
                    + "Mã khách hàng: " + command.getUserId() + "\n"
                    + "Tổng giá trị: " + command.getTotalAmount() + " VNĐ\n"
                    + "Phương thức thanh toán: " + command.getPaymentMethod() + "\n"
                    + "Tên người nhận: " + command.getReceiverName() + "\n"
                    + "Số điện thoại: " + command.getReceiverPhone() + "\n"
                    + "Địa chỉ giao hàng: " + command.getAddress() + "\n"
                    + "Danh sách sản phẩm mua:\n";
            
            for (var item : command.getItems()) {
                aiPrompt += String.format("- %s (Mã SP: %s), Số lượng: %d, Giá: %s VNĐ\n", 
                        item.getProductName(), item.getProductId(), item.getQuantity(), item.getPrice());
            }

            aiPrompt += "\n=== DỰ BÁO CỦA HỆ THỐNG HEURISTICS ===\n"
                    + "- Đơn hàng COD giá trị cao: " + (isHighValueCod ? "CÓ" : "KHÔNG") + "\n"
                    + "- Số điện thoại khả nghi: " + (isInvalidPhone ? "CÓ" : "KHÔNG") + "\n"
                    + "- Địa chỉ giao hàng sơ sài/ảo ở Việt Nam: " + (isWeakAddress ? "CÓ" : "KHÔNG") + "\n"
                    + "- Có sản phẩm lệch giá thị trường nghiêm trọng (Price Tampering): " + (isPriceSuspicious ? "CÓ" : "KHÔNG") + "\n\n"
                    + "=== HƯỚNG DẪN KIỂM TRA CHO CHUYÊN GIA AI ===\n"
                    + "1. Địa chỉ Việt Nam hợp chuẩn phải gồm tối thiểu 2 cấp hành chính chi tiết (ví dụ: Số nhà/Tên đường, Phường/Xã, Quận/Huyện, Tỉnh/Thành phố). Nếu địa chỉ chỉ ghi mỗi tên tỉnh/thành phố chung chung hoặc chứa ký tự vô nghĩa (abc, xyz), hãy đánh giá là rủi ro rất cao.\n"
                    + "LƯU Ý CẬP NHẬT HÀNH CHÍNH TOÀN VIỆT NAM (MỚI NHẤT GIAI ĐOẠN 2023-2025 VÀ SAU ĐÓ): Kể từ năm 2024-2025, theo các Nghị quyết của Ủy ban Thường vụ Quốc hội về sắp xếp, sáp nhập đơn vị hành chính cấp huyện/xã, đã có hàng trăm phường, xã cũ trên khắp Việt Nam (bao gồm Hà Nội, TP.HCM, Đà Nẵng, Hải Phòng, Quảng Ninh...) được sáp nhập hoặc đổi tên thành các đơn vị hành chính mới hiện hành (ví dụ điển hình: tại TP.HCM, Phường Võ Thị Sáu sáp nhập thành Phường Xuân Hòa thuộc Quận 3; nhiều phường tại các quận Hoàn Kiếm, Hai Bà Trưng tại Hà Nội cũng được sáp nhập mới). Do dữ liệu cũ của bạn có thể chưa cập nhật kịp các thay đổi địa giới hành chính mới này, bạn BẮT BUỘC phải cởi mở, luôn ưu tiên chấp nhận và đánh giá là HỢP LỆ (SAFE) đối với tất cả địa chỉ sử dụng các tên phường, xã, quận, huyện mới sáp nhập này. Tuyệt đối không được đối chiếu một cách máy móc với bản đồ cũ trước năm 2025 rồi báo lỗi mâu thuẫn địa lý hoặc đánh giá là rủi ro/lừa đảo nếu địa chỉ đó có cấu trúc đầy đủ, rõ ràng.\n"
                    + "2. Kiểm tra kỹ đơn giá (Price) của từng mặt hàng: Đối chiếu xem có sản phẩm nào bị chỉnh sửa giá bất thường so với giá trị thực tế của chúng trên thị trường Việt Nam hay không (Ví dụ: Áo thun nam có giá tới vài triệu đồng, hoặc Laptop/MacBook xịn có giá chỉ vài nghìn hay vài trăm nghìn đồng). Đây là dấu hiệu của việc can thiệp giá client bất hợp pháp. BẮT BUỘC chấm điểm rủi ro >= 80 (RISKY) nếu phát hiện bất kỳ sản phẩm nào lệch giá bất hợp lý.\n\n"
                    + "=== YÊU CẦU TRẢ LỜI ===\n"
                    + "Hãy chấm điểm rủi ro gian lận (fraudScore) từ 0 (Hoàn toàn an toàn) đến 100 (Gian lận tuyệt đối).\n"
                    + "Nếu điểm rủi ro lớn hơn hoặc bằng 80, trạng thái (status) phải là 'RISKY'. Ngược lại là 'SAFE'.\n"
                    + "Đồng thời cung cấp một lời giải thích bằng TIẾNG VIỆT tự nhiên nhất (tối đa 2 câu) giải thích lý do tại sao chấm số điểm đó.\n\n"
                    + "BẮT BUỘC TRẢ LỜI DƯỚI DẠNG JSON duy nhất, khớp chính xác định dạng sau (không viết thêm lời giới thiệu, không bọc trong ```json):\n"
                    + "{\n"
                    + "  \"fraudScore\": 85,\n"
                    + "  \"status\": \"RISKY\",\n"
                    + "  \"reason\": \"Lý do chi tiết bằng tiếng Việt\"\n"
                    + "}";

            log.info("[AI FRAUD DETECTOR] Gửi yêu cầu phân tích đơn hàng sang Gemini...");
            String aiResult;
            try {
                aiResult = chatModel.generate(aiPrompt);
            } catch (Exception e) {
                log.warn("[AI FRAUD DETECTOR] Không thể gọi Google Gemini (Lỗi: {}). Sử dụng fallback Heuristics với lý do tiếng Việt tự nhiên nhất.", e.getMessage());
                aiResult = generateFallbackFraudResponse(command, isHighValueCod, isInvalidPhone, isWeakAddress, isPriceSuspicious);
            }
            log.info("[AI FRAUD DETECTOR] Kết quả nhận được: {}", aiResult);

            // Làm sạch kết quả JSON nếu LLM bọc nó trong markdown code block
            String cleanedJson = aiResult.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonNode jsonNode = objectMapper.readTree(cleanedJson);
            fraudScore = jsonNode.get("fraudScore").asInt();
            status = jsonNode.get("status").asText();
            reason = jsonNode.get("reason").asText();

        } catch (Exception e) {
            log.error("[AI FRAUD DETECTOR] Lỗi nghiêm trọng khi phân tích gian lận: ", e);
            // Thuật toán Heuristic dự phòng trong trường hợp API AI bị lỗi
            if (isPriceSuspicious) {
                fraudScore = 95;
                status = "RISKY";
                reason = "Đơn hàng bị từ chối do phát hiện chênh lệch đơn giá sản phẩm so với thực tế (Price Tampering).";
            } else if (command.getTotalAmount().compareTo(new BigDecimal("100000000")) > 0) {
                fraudScore = 90;
                status = "RISKY";
                reason = "Giá trị đơn hàng vượt quá hạn mức tối đa cho phép thanh toán COD (trên 100 triệu VNĐ).";
            } else if (isWeakAddress) {
                fraudScore = 80;
                status = "RISKY";
                reason = "Địa chỉ nhận hàng sơ sài hoặc không đúng cấu trúc địa lý Việt Nam.";
            }
        }

        // Tạo DTO phản hồi
        FraudCheckReply reply = FraudCheckReply.builder()
                .orderId(command.getOrderId())
                .fraudScore(fraudScore)
                .status(status)
                .reason(reason)
                .build();

        // Gửi kết quả về RabbitMQ cho SAGA Orchestrator
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.AI_REPLY_CHECK_FRAUD, reply);
        log.info("[AI FRAUD DETECTOR] Đã hoàn thành kiểm tra và gửi phản hồi cho đơn hàng: {} (Status: {}, Score: {})", 
                command.getOrderId(), status, fraudScore);
    }
    private String generateFallbackFraudResponse(com.oms.common.dto.FraudCheckCommand command, boolean isHighValueCod, boolean isInvalidPhone, boolean isWeakAddress, boolean isPriceSuspicious) {
        int score = 15;
        String status = "SAFE";
        String reason = "Thông tin đơn hàng đầy đủ, phương thức thanh toán an toàn.";

        if (isPriceSuspicious) {
            score = 95;
            status = "RISKY";
            reason = "CẢNH BÁO GIAN LẬN ĐƠN GIÁ: Phát hiện một hoặc nhiều sản phẩm có đơn giá chênh lệch quá xa so với giá trị thị trường thực tế tại Việt Nam (dấu hiệu sửa đổi giá bất hợp pháp).";
        } else if (isHighValueCod) {
            score = 85;
            status = "RISKY";
            reason = "Phát hiện dấu hiệu rủi ro cao: Đơn hàng COD có tổng giá trị giao dịch rất lớn (" 
                    + String.format("%,.0f", command.getTotalAmount()) 
                    + " VNĐ), tiềm ẩn nguy cơ cao về việc bùng hàng hoặc đặt đơn ảo phá hoại.";
        } else if (isInvalidPhone) {
            score = 80;
            status = "RISKY";
            reason = "Phát hiện thông tin khả nghi: Số điện thoại người nhận \"" + command.getReceiverPhone() 
                    + "\" không hợp lệ hoặc thiếu chữ số, tiềm ẩn nguy cơ sử dụng thông tin liên lạc giả mạo.";
        } else if (isWeakAddress) {
            score = 80;
            status = "RISKY";
            reason = "Phát hiện thông tin khả nghi: Địa chỉ nhận hàng tại Việt Nam quá sơ sài, thiếu số nhà/đường hoặc không rõ phường/xã, quận/huyện, không đảm bảo tính xác thực để giao hàng.";
        }

        return String.format("{\n  \"fraudScore\": %d,\n  \"status\": \"%s\",\n  \"reason\": \"%s\"\n}", score, status, reason);
    }
}
