package com.oms.deliveryservice.controller;

import com.oms.common.ApiResponse;
import com.oms.common.enums.DeliveryStatus;
import com.oms.deliveryservice.entity.Delivery;
import com.oms.deliveryservice.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@Slf4j
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Delivery>> updateStatus(
            @PathVariable String id, 
            @RequestParam DeliveryStatus status,
            @RequestParam(required = false) String failReason,
            @RequestHeader(value = "X-Account-Id", required = false) String headerId) {
            
        log.info("Received request to update delivery {} status to {} by shipper: {}", id, status, headerId);
        
        Optional<Delivery> updated;
        if (headerId != null) {
            updated = deliveryService.updateStatusForShipper(id, headerId, status, failReason);
        } else {
            updated = deliveryService.updateStatus(id, status, failReason);
        }
        
        if (updated.isPresent()) {
            return ResponseEntity.ok(ApiResponse.<Delivery>builder()
                    .success(true)
                    .status(HttpStatus.OK.value())
                    .message("Cập nhật trạng thái vận chuyển thành công")
                    .result(updated.get())
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Delivery>builder()
                            .success(false)
                            .status(HttpStatus.NOT_FOUND.value())
                            .message("Không tìm thấy đơn vận chuyển")
                            .build()
            );
        }
    }

    @GetMapping("/shipper")
    public ResponseEntity<ApiResponse<java.util.List<Delivery>>> getDeliveriesByShipper(
            @RequestHeader("X-Account-Id") String shipperId,
            @RequestParam(required = false) DeliveryStatus status) {
        
        log.info("Received request to get deliveries for shipper {} with status {}", shipperId, status);
        java.util.List<Delivery> result = deliveryService.getDeliveriesByShipper(shipperId, status);
        return ResponseEntity.ok(ApiResponse.<java.util.List<Delivery>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(result)
                .build());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<Delivery>> getByOrderId(@PathVariable String orderId) {
        Optional<Delivery> delivery = deliveryService.getByOrderId(orderId);
        if (delivery.isPresent()) {
            return ResponseEntity.ok(ApiResponse.<Delivery>builder()
                    .success(true)
                    .status(HttpStatus.OK.value())
                    .message("Thành công")
                    .result(delivery.get())
                    .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Delivery>builder()
                            .success(false)
                            .status(HttpStatus.NOT_FOUND.value())
                            .message("Không tìm thấy đơn vận chuyển cho đơn hàng này")
                            .build()
            );
        }
    }
}
