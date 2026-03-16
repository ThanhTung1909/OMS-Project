package com.oms.identityservice.controller;

import com.oms.identityservice.dto.AuthResponse;
import com.oms.identityservice.dto.LoginRequest;
import com.oms.identityservice.dto.RegisterRequest;
import com.oms.identityservice.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController

@RequestMapping("/api/v1/auth")

public class AuthController {

    @Autowired
    AuthService authService;

    @PostMapping("/register")

    public String register(
            @RequestBody RegisterRequest r){

        authService.register(r);

        return "SUCCESS";

    }

    @PostMapping("/login")

    public AuthResponse login(
            @RequestBody LoginRequest r){

        return authService.login(r);

    }

}