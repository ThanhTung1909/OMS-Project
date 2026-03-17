package com.oms.apigateway.filter;

import com.oms.apigateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final RouterValidator validator;

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        boolean isSecured = validator.isSecured.test(request);

        System.out.println("DEBUG: Path: " + path + " | isSecured: " + isSecured);

        if(validator.isSecured.test(request)){
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

                if(!isAuthorized(path, method, role)){
                    return this.onError(exchange, "Forbidden: Bạn không có quyền thực hiện hành động này", HttpStatus.FORBIDDEN);
                }

                exchange.getRequest().mutate()
                        .header("X-User-Id", String.valueOf(claims.get("userId")))
                        .header("X-User-Role", String.valueOf(claims.get("role")))
                        .header("X-User-Name", claims.getSubject())
                        .build();

            }catch (Exception e){
                return this.onError(exchange, "Unauthorized: Invalid token", HttpStatus.UNAUTHORIZED);
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return  response.setComplete();
    }

    private boolean isAuthorized(String path, HttpMethod method, String role){
        if(path.startsWith("/api/v1/products")){
            if(method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE){
                return "ADMIN".equalsIgnoreCase(role);
            }
        }
        return true;
    }

    @Override
    public int getOrder(){
        return -1;
    }
}
