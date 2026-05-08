package com.oms.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin sự kiện dùng chung cho thông báo đơn hàng và vận chuyển
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String orderId;         // Mã đơn hàng
    private String userId;          // Mã người dùng
    private String customerEmail;   // Email khách hàng nhận thông báo
    private String customerName;    // Tên khách hàng
    private String status;          // Trạng thái mới (ví dụ: CONFIRMED, DELIVERING)
    private String message;         // Thông điệp bổ sung (nếu có)
}
