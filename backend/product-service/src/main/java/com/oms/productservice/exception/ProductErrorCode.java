package com.oms.productservice.exception;

import com.oms.common.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ProductErrorCode implements ErrorCode {
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Danh mục không tồn tại"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Sản phẩm không tồn tại");

    private final HttpStatus httpStatus;
    private final String message;

    ProductErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
