package com.oms.orderservice.exception;

import com.oms.common.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Đơn hàng không tồn tại"),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "Giao dịch thanh toán thất bại");

    private final HttpStatus httpStatus;
    private final String message;

    OrderErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
