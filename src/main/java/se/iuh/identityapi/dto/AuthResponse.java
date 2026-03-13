package se.iuh.identityapi.dto;

import lombok.Getter;
import lombok.Setter;
import se.iuh.identityapi.entity.Enum.Role;

@Getter
@Setter

public class AuthResponse {

    public String token;

    public String username;

    public Role role;

}
