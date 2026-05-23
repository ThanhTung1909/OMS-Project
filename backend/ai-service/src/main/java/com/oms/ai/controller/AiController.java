package com.oms.ai.controller;

import com.oms.ai.dto.ChatMessageRequest;
import com.oms.ai.dto.ChatMessageResponse;
import com.oms.ai.service.AiShoppingAssistantService;
import com.oms.ai.service.VectorSyncService;
import com.oms.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiShoppingAssistantService assistantService;
    private final VectorSyncService syncService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> chat(@RequestBody ChatMessageRequest request) {
        log.info("[AI CONTROLLER] Nhận yêu cầu chat từ người dùng");
        ChatMessageResponse response = assistantService.chat(request.getMessage(), request.getUserId());
        return ResponseEntity.ok(ApiResponse.<ChatMessageResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(response)
                .build());
    }

    @PostMapping("/sync/bootstrap")
    public ResponseEntity<ApiResponse<String>> bootstrapVectorDb() {
        log.info("[AI CONTROLLER] Kích hoạt đồng bộ hóa sản phẩm thủ công sang Vector DB");
        try {
            int syncCount = syncService.bootstrapProducts();
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .success(true)
                    .status(HttpStatus.OK.value())
                    .message("Đồng bộ hóa thành công " + syncCount + " sản phẩm.")
                    .result("Đã đồng bộ hóa " + syncCount + " sản phẩm vào Redis Vector Store.")
                    .build());
        } catch (Exception e) {
            log.error("[AI CONTROLLER] Đồng bộ hóa thủ công thất bại: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<String>builder()
                            .success(false)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .message("Đồng bộ hóa thất bại: " + e.getMessage())
                            .result(null)
                            .build());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("AI Service đang hoạt động ổn định")
                .result("Healthy")
                .build());
    }
}
