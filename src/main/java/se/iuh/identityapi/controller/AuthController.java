package se.iuh.identityapi.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.iuh.identityapi.dto.AuthResponse;
import se.iuh.identityapi.dto.LoginRequest;
import se.iuh.identityapi.dto.RegisterRequest;
import se.iuh.identityapi.service.AuthService;

@RestController

@RequestMapping("/api/auth")

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