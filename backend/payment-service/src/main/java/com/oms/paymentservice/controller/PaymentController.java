package com.oms.paymentservice.controller;

import com.oms.paymentservice.dto.PaymentRequest;
import com.oms.paymentservice.dto.PaymentResponse;
import com.oms.paymentservice.service.PaymentService;
import com.oms.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(
            ApiResponse.<PaymentResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(paymentService.pay(request))
                .build()
        );
    }
}
