package com.oms.identityservice.exception;

import com.oms.common.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum IdentityErrorCode implements ErrorCode {
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu nhập lại không khớp với mật khẩu đã nhập"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Tài khoản hoặc mật khẩu không chính xác"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Tài khoản không tồn tại trên hệ thống"),
    USER_EXISTED(HttpStatus.CONFLICT, "Tên đăng nhập này đã tồn tại trên hệ thống"),
    EMAIL_EXISTED(HttpStatus.CONFLICT, "Email này đã được sử dụng bởi một tài khoản khác"),
    RABBITMQ_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi gửi tin nhắn đồng bộ dữ liệu"),
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khoá trên hệ thống"),
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống không xác định");

    private final HttpStatus httpStatus;
    private final String message;

    IdentityErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
