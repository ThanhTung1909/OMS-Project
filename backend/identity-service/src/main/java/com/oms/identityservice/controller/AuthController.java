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

}