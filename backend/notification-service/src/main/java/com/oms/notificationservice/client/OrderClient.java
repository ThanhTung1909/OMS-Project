package com.oms.notificationservice.client;

import com.oms.common.ApiResponse;
import com.oms.notificationservice.dto.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", url = "${app.services.order-service.url:http://order-service:8083}")
public interface OrderClient {

    @GetMapping("/api/v1/orders/{orderId}")
    ApiResponse<OrderResponse> getOrderById(@PathVariable("orderId") String orderId);

    @GetMapping("/api/v1/orders/internal/{orderId}/user-id")
    String getUserIdByOrderId(@PathVariable("orderId") String orderId);
}
