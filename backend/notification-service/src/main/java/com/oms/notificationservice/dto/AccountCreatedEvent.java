package com.oms.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin sự kiện khi một tài khoản mới được tạo
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent {
    private String accountId;   // ID tài khoản
    private String userName;    // Tên đăng nhập
    private String email;       // Địa chỉ email để gửi thông báo
    private String fullname;    // Họ tên đầy đủ
    private String phone;       // Số điện thoại
    private String role;        // Vai trò (USER, STAFF, ADMIN)
}
