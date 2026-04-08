package com.oms.identityservice.entity.Enum;

public enum ErrorCode {
    INVALID_PASSWORD(400, "Mật khẩu nhập lại không khớp với mật khẩu đã nhập"),
    INVALID_CREDENTIALS(401, "Tài khoản hoặc mật khẩu không chính xác"),
    USER_NOT_FOUND(404, "Tài khoản không tồn tại trên hệ thống"),
    USER_EXISTED(409, "Tên đăng nhập này đã tồn tại trên hệ thống"),
    EMAIL_EXISTED(409, "Email này đã được sử dụng bởi một tài khoản khác"),
    UNCATEGORIZED_EXCEPTION(500, "Lỗi hệ thống không xác định"),
    RABBITMQ_SEND_FAILED(500, "Lỗi gửi tin nhắn đồng bộ dữ liệu");

    private int code;
    private String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
