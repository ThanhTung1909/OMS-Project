package com.oms.orderservice.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class OrderAddress {
    private String street;
    private String ward;
    private String city;
    private String receiverName;
    private String receiverPhone;
}
