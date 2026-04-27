package com.oms.inventoryservice.exception;

import com.oms.common.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum InventoryErrorCode implements ErrorCode {
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "Kho hàng không đủ số lượng");

    private final HttpStatus httpStatus;
    private final String message;

    InventoryErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
