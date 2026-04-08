package com.oms.orderservice.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCommand {
    private String orderId;     
    private String userId;     
    private BigDecimal amount;  
    private String description; 
}
