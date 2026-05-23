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

        try {
            // 1. Phân tích Heuristics (Quy tắc cứng ban đầu)
            // Ví dụ: Đơn hàng COD có giá trị cực lớn trên 50,000,000 VNĐ là một dấu hiệu khả nghi ban đầu
            boolean isHighValueCod = "COD".equalsIgnoreCase(command.getPaymentMethod()) && 
                    command.getTotalAmount().compareTo(new BigDecimal("50000000")) > 0;

            // Số điện thoại không hợp lệ (ít hơn 9 chữ số)
            String phone = command.getReceiverPhone() != null ? command.getReceiverPhone().replaceAll("\\s+", "") : "";
            boolean isInvalidPhone = phone.length() < 9;

            // Địa chỉ nhận hàng quá sơ sài hoặc chung chung
            String address = command.getAddress() != null ? command.getAddress().trim() : "";
            boolean isWeakAddress = address.length() < 8 || address.equalsIgnoreCase("Việt Nam") || address.equalsIgnoreCase("Hà Nội") || address.equalsIgnoreCase("Hồ Chí Minh");

            // 2. Sử dụng Google Gemini AI để phân tích hành vi nâng cao
            String aiPrompt = "Bạn là một chuyên gia phân tích an ninh thương mại điện tử chuyên nghiệp (Cybersecurity & Fraud Analyst).\n"
                    + "Hãy phân tích chi tiết đơn hàng sau để tìm kiếm các dấu hiệu lừa đảo, giả mạo thông tin, đặt đơn ảo hoặc phá hoại:\n\n"
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
                    + "- Địa chỉ giao hàng sơ sài: " + (isWeakAddress ? "CÓ" : "KHÔNG") + "\n\n"
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
                aiResult = generateFallbackFraudResponse(command, isHighValueCod, isInvalidPhone, isWeakAddress);
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
            if (command.getTotalAmount().compareTo(new BigDecimal("100000000")) > 0) {
                fraudScore = 90;
                status = "RISKY";
                reason = "Giá trị đơn hàng vượt quá hạn mức tối đa cho phép thanh toán COD (trên 100 triệu VNĐ).";
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
    private String generateFallbackFraudResponse(com.oms.common.dto.FraudCheckCommand command, boolean isHighValueCod, boolean isInvalidPhone, boolean isWeakAddress) {
        int score = 15;
        String status = "SAFE";
        String reason = "Thông tin đơn hàng đầy đủ, phương thức thanh toán an toàn.";

        if (isHighValueCod) {
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
            reason = "Phát hiện thông tin khả nghi: Địa chỉ nhận hàng quá sơ sài hoặc chung chung \"" + (command.getAddress() != null ? command.getAddress() : "") 
                    + "\", không đảm bảo tính xác thực để đơn vị vận chuyển có thể giao hàng thành công.";
        }

        return String.format("{\n  \"fraudScore\": %d,\n  \"status\": \"%s\",\n  \"reason\": \"%s\"\n}", score, status, reason);
    }
}
