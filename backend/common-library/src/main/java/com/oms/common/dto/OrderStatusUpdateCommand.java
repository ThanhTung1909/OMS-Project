package com.oms.common.dto;

import com.oms.common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateCommand {
    private String orderId;
    private OrderStatus newStatus;
    private String message;
    private String paymentId;
    private String errorMessage;
}
