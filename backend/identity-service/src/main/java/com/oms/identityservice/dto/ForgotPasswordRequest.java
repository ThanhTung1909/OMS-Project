package com.oms.identityservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;
}
