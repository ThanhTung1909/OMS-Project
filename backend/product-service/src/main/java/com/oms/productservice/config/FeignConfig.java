package com.oms.productservice.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Cấu hình Feign Client để propagate internal security headers
 * (X-Account-Id, X-User-Role) sang các service khác khi gọi service-to-service.
 *
 * Nếu request hiện tại là public (không có user context), tự gán danh tính
 * nội bộ của Product Service để Inventory Service không từ chối 403.
 */
@Configuration
public class FeignConfig {

    private static final String INTERNAL_SERVICE_ID   = "product-service";
    private static final String INTERNAL_SERVICE_ROLE = "INTERNAL";

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            String accountId = null;
            String userRole  = null;

            // Lấy headers từ request đến (nếu có – tức là user đã login)
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                accountId = request.getHeader("X-Account-Id");
                userRole  = request.getHeader("X-User-Role");
            }

            // Fallback: dùng danh tính service khi không có user context
            // (ví dụ: public endpoint GET /products gọi sang Inventory Service)
            if (accountId == null) accountId = INTERNAL_SERVICE_ID;
            if (userRole  == null) userRole  = INTERNAL_SERVICE_ROLE;

            requestTemplate.header("X-Account-Id", accountId);
            requestTemplate.header("X-User-Role",  userRole);
        };
    }
}
