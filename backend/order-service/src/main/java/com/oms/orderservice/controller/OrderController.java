package com.oms.orderservice.controller;

import com.oms.orderservice.dto.OrderRequest;
import com.oms.orderservice.dto.OrderResponse;
import com.oms.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("Nhận yêu cầu tạo đơn hàng cho User: {}", request.getUserId());

        String orderId = orderService.createOrder(request);

        OrderResponse response = OrderResponse.builder()
                .orderId(orderId)
                .status("PAYMENT_PENDING")
                .message("Đơn hàng đã được khởi tạo thành công. Vui lòng tiến hành thanh toán.")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}