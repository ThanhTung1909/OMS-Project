package com.oms.identityservice.dto;

import com.oms.identityservice.entity.Enum.Role;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter

public class AuthResponse {

    public String token;

    public String username;

    public Role role;

    private String userId;
    private String fullName;
    private String email;

}
