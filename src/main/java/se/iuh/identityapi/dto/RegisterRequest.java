package se.iuh.identityapi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class RegisterRequest {

    public String username;

    public String password;

    public String email;

    public String fullName;

}
