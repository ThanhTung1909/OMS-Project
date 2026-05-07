package com.oms.notificationservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Giả lập định danh hệ thống cho các cuộc gọi nội bộ
            requestTemplate.header("X-Account-Id", "SYSTEM_NOTIFICATION");
            requestTemplate.header("X-User-Role", "ADMIN");
        };
    }
}
