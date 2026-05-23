package com.oms.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckReply {
    private String orderId;
    private int fraudScore; // 0 - 100
    private String status;  // SAFE hoặc RISKY
    private String reason;  // Lý do phát hiện rủi ro (ngôn ngữ tự nhiên để hiển thị cho người dùng)
}
