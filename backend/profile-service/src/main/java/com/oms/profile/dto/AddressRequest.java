package com.oms.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {
    private String street;
    private String ward;
    private String district;
    private String city;
    private boolean isDefault;
}
