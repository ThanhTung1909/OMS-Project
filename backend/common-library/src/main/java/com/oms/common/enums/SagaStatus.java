package com.oms.common.enums;

public enum SagaStatus {
    STARTED,
    INVENTORY_RESERVED,
    WAITING_FOR_PAYMENT,
    PAYMENT_PROCESSED,
    COMPLETED,
    FAILED,
    ROLLED_BACK
}
