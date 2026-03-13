package se.iuh.identityapi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class LoginRequest {

    public String username;

    public String password;

}