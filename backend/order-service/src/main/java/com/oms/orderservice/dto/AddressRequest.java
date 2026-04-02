package com.oms.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {
    private String street;
    private String ward;
    private String district;
    private String city;
    private String receiverName;
    private String receiverPhone;
}
