package com.oms.orderservice.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    private static final String INTERNAL_SERVICE_ID   = "order-service";
    private static final String INTERNAL_SERVICE_ROLE = "INTERNAL";

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            String accountId = null;
            String userRole  = null;

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                accountId = request.getHeader("X-Account-Id");
                userRole  = request.getHeader("X-User-Role");
            }

            // Fallback: dùng danh tính nội bộ khi không có user context
            if (accountId == null) accountId = INTERNAL_SERVICE_ID;
            if (userRole  == null) userRole  = INTERNAL_SERVICE_ROLE;

            requestTemplate.header("X-Account-Id", accountId);
            requestTemplate.header("X-User-Role",  userRole);
        };
    }
}
