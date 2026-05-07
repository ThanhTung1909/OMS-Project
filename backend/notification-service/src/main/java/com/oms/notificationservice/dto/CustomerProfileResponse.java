package com.oms.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileResponse {
    private String fullname;
    private String phone;
    private String avatarUrl;
    private String gender;
    private String dateOfBirth;
    private String accountId;
}
