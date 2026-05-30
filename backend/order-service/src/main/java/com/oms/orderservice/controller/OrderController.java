package com.oms.orderservice.controller;

import com.oms.common.ApiResponse;
import com.oms.orderservice.dto.OrderRequest;
import com.oms.orderservice.dto.OrderResponse;
import com.oms.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;


@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody OrderRequest request) {
        
        // Gán accountId vào request để đảm bảo đơn hàng thuộc về đúng người đang đăng nhập
        request.setUserId(accountId);
        log.info("Nhận yêu cầu tạo đơn hàng cho Account: {}", accountId);

        OrderResponse result = orderService.createOrder(request);

        String displayMessage = "COD".equalsIgnoreCase(request.getPaymentMethod()) 
                ? "Đơn hàng COD đã được khởi tạo thành công." 
                : "Đơn hàng đã được khởi tạo thành công. Vui lòng tiến hành thanh toán.";

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<OrderResponse>builder()
                .success(true)
                .status(HttpStatus.CREATED.value())
                .message(displayMessage)
                .result(result)
                .build()
        );
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderStatus(@PathVariable String orderId) {
        OrderResponse result = orderService.getOrder(orderId);
        
        if ("CANCELLED".equals(result.getStatus())) {
            String displayMsg = result.getErrorMessage() != null ? result.getErrorMessage() : "Giao dịch thanh toán thất bại";
            return ResponseEntity.badRequest().body(
                ApiResponse.<OrderResponse>builder()
                    .success(false)
                    .status(HttpStatus.BAD_REQUEST.value())
                    .message(displayMsg)
                    .result(result)
                    .build()
            );
        }

        return ResponseEntity.ok(
            ApiResponse.<OrderResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(result)
                .build()
        );
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            @RequestHeader("X-Account-Id") String accountId,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.<Page<OrderResponse>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(orderService.getMyOrders(accountId, pageable))
                .build());
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrdersForAdmin(
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.<Page<OrderResponse>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(orderService.getAllOrders(pageable))
                .build());
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @GetMapping("/admin/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetailsForAdmin(@PathVariable String orderId) {
        OrderResponse result = orderService.getOrder(orderId);
        return ResponseEntity.ok(
            ApiResponse.<OrderResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(result)
                .build()
        );
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @PutMapping("/{id}/prepare")
    public ResponseEntity<ApiResponse<Void>> prepareOrder(@PathVariable String id) {
        orderService.prepareOrder(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Đã duyệt đơn")
                .build());
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable String id, @RequestHeader("X-Account-Id") String accountId) {
        orderService.cancelOrder(id, accountId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Đã hủy đơn")
                .build());
    }

    @GetMapping("/internal/{orderId}/user-id")
    public ResponseEntity<String> getUserIdByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getUserIdByOrderId(orderId));
    }
}