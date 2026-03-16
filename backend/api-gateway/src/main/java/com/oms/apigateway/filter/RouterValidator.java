package com.oms.apigateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouterValidator {

    public static final List<String> openApiEnpoints = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login"
    );

    public Predicate<ServerHttpRequest> isSecured = request -> openApiEnpoints
            .stream().noneMatch(uri -> request.getURI().getPath().contains(uri));
}
