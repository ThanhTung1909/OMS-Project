package com.oms.orderservice.entity;

public enum OrderStatus {
    CREATED,
    PENDING,
    CONFIRMED,
    SHIPPING,
    COMPLETED,
    CANCELLED,
    PENDING_PAYMENT,
    PAYMENT_FAILED,

}
