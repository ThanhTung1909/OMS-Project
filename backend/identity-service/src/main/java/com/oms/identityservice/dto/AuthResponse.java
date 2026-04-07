package com.oms.identityservice.dto;

import com.oms.identityservice.entity.Enum.Role;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter

public class AuthResponse {

    public String token;

    public String username;

    public String role;

    private String accountId;
    private String fullName;
    private String email;

}
