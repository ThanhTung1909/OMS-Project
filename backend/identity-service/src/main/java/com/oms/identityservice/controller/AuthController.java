package com.oms.identityservice.controller;

import com.oms.common.ApiResponse;
import com.oms.identityservice.dto.AuthResponse;
import com.oms.identityservice.dto.LoginRequest;
import com.oms.identityservice.dto.RegisterRequest;
import com.oms.identityservice.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register( @Valid @RequestBody RegisterRequest r){
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<AuthResponse>builder()
                .success(true)
                .status(HttpStatus.CREATED.value())
                .message("Thành công")
                .result(authService.register(r))
                .build()
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest r){
        return ResponseEntity.ok(
            ApiResponse.<AuthResponse>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Thành công")
                .result(authService.login(r))
                .build()
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody com.oms.identityservice.dto.ForgotPasswordRequest r) {
        authService.forgotPassword(r);
        return ResponseEntity.ok(
            ApiResponse.<String>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Mã OTP đã được gửi đến email của bạn")
                .result("Success")
                .build()
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<String>> verifyOtp(@Valid @RequestBody com.oms.identityservice.dto.VerifyOtpRequest r) {
        authService.verifyOtp(r);
        return ResponseEntity.ok(
            ApiResponse.<String>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Mã OTP hợp lệ")
                .result("Success")
                .build()
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody com.oms.identityservice.dto.ResetPasswordRequest r) {
        authService.resetPassword(r);
        return ResponseEntity.ok(
            ApiResponse.<String>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Đặt lại mật khẩu thành công")
                .result("Success")
                .build()
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody com.oms.identityservice.dto.ChangePasswordRequest r) {
        String accountId = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        authService.changePassword(accountId, r);
        return ResponseEntity.ok(
            ApiResponse.<String>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Đổi mật khẩu thành công")
                .result("Success")
                .build()
        );
    }
}