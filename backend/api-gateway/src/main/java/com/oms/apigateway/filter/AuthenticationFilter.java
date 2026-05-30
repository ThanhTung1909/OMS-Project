package com.oms.apigateway.filter;

import com.oms.apigateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final RouterValidator validator;
    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        boolean isSecured = validator.isSecured.test(request);

        String correlationId = UUID.randomUUID().toString();

        if(isSecured){
            if(!request.getHeaders().containsKey("Authorization")){
                return this.onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            final String authHeader = request.getHeaders().getOrEmpty("Authorization").get(0);

            if(authHeader == null || !authHeader.startsWith("Bearer ")){
                return this.onError(exchange, "Invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String token =  authHeader.substring(7);

            try{
                if(jwtUtil.isInvalid(token)){
                    return this.onError(exchange, "Token đã hết hạn", HttpStatus.UNAUTHORIZED);
                }

                Claims claims = jwtUtil.getAllClaimsFromToken(token);
                String role = String.valueOf(claims.get("role"));
                Object accountIdObj = claims.get("accountId");
                if (accountIdObj == null) {
                    return this.onError(exchange, "Unauthorized: Token missing account info", HttpStatus.UNAUTHORIZED);
                }
                String accountId = String.valueOf(accountIdObj); 

                log.info("[GATEWAY] {} {} | CorrelationId: {} | Account: {} | Role: {}", method, path, correlationId, accountId, role);

                return redisTemplate.hasKey("ACCOUNT_BANNED_STATUS:" + accountId)
                    .flatMap(isBanned -> {
                        if (Boolean.TRUE.equals(isBanned)) {
                            log.warn("[GATEWAY] Blocked request from banned account: {}", accountId);
                            return this.onError(exchange, "Tài khoản của bạn đã bị khoá trên hệ thống", HttpStatus.FORBIDDEN);
                        }
                        
                        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                            .header("X-Account-Id", accountId) 
                            .header("X-User-Role", role)
                            .header("X-Correlation-Id", correlationId)
                            .build();
                        
                        return chain.filter(exchange.mutate().request(modifiedRequest).build());
                    });

            }catch (Exception e){
                log.error("[GATEWAY] Invalid token for request {} {}", method, path, e);
                return this.onError(exchange, "Unauthorized: Invalid token", HttpStatus.UNAUTHORIZED);
            }
        }
        
        log.info("[GATEWAY] {} {} | CorrelationId: {} | Account: anonymous | Role: NONE", method, path, correlationId);
        
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-Correlation-Id", correlationId)
                .build();
                
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add("Content-Type", "application/json");

        String jsonResponse = String.format(
            "{\"success\": false, \"status\": %d, \"message\": \"%s\"}",
            httpStatus.value(), err
        );

        byte[] bytes = jsonResponse.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.core.io.buffer.DataBuffer buffer = response.bufferFactory().wrap(bytes);

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder(){
        return -1;
    }
}
