package com.oms.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Tài khoản chưa được xác thực"),
    UNAUTHORIZED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Tài nguyên không tồn tại"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống không xác định");

    private final HttpStatus httpStatus;
    private final String message;

    CommonErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
