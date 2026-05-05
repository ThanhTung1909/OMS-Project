package com.oms.apigateway.controller;

import com.oms.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<ApiResponse<String>>> fallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ApiResponse.<String>builder()
                .success(false)
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .message("Dịch vụ hiện đang bận hoặc gặp sự cố, vui lòng thử lại sau")
                .build()
        ));
    }
}
