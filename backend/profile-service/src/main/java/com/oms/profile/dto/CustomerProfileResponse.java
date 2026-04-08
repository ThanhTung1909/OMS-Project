package com.oms.profile.dto;

import lombok.*;
import java.util.List;

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
    private List<AddressResponse> addresses;
}