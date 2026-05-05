package com.oms.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Lấy X-Account-Id từ request header (đã được add bởi AuthenticationFilter)
            String accountId = exchange.getRequest().getHeaders().getFirst("X-Account-Id");
            return Mono.just(accountId != null ? accountId : "anonymous");
        };
    }
}
