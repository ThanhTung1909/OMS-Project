package com.oms.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent {
    private String accountId;
    private String userName;
    private String email;
    private String fullname;
    private String phone;
    private String role;
}
