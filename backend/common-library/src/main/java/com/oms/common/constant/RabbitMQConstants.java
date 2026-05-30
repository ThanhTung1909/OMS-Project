package com.oms.common.constant;

public class RabbitMQConstants {
    
    // Exchange chung
    public static final String EXCHANGE_NAME = "oms.exchange";
    
    // Order 
    public static final String RK_ORDER_EVENT_CREATED = "order.event.created";
    public static final String RK_ORDER_COMMAND_UPDATE = "order.command.update";
    
    // Identity 
    public static final String IDENTITY_ACCOUNT_CREATED = "identity.account.created";
    public static final String IDENTITY_FORGOT_PASSWORD_REQUESTED = "identity.forgot_password.requested";
    public static final String IDENTITY_ACCOUNT_STATUS_CHANGED = "identity.account.status_changed";
    
    // Payment 
    public static final String PAYMENT_COMMAND_CREATE = "payment.command.create";
    public static final String PAYMENT_REPLY_RESULT = "payment.reply.result";
    public static final String PAYMENT_REPLY_URL_CREATED = "payment.reply.url_created";
    
    // Inventory 
    public static final String INVENTORY_COMMAND_RESERVE = "inventory.command.reserve";
    public static final String INVENTORY_COMMAND_CONFIRM = "inventory.command.confirm";
    public static final String INVENTORY_COMMAND_ROLLBACK = "inventory.command.rollback";
    public static final String INVENTORY_REPLY_RESULT = "inventory.reply.result";
    
    // Delivery 
    public static final String DELIVERY_COMMAND_CREATE = "delivery.command.create";
    public static final String DELIVERY_STATUS_UPDATE = "delivery.status.update";

    public static final String RK_DELIVERY_STATUS_UPDATE = DELIVERY_STATUS_UPDATE;
    
    // Notification 
    public static final String NOTIFICATION_ORDER_STATUS = "notification.order.status";
    public static final String NOTIFICATION_STOCK_LOW = "notification.stock.low";

    // AI
    public static final String AI_COMMAND_CHECK_FRAUD = "ai.command.check_fraud";
    public static final String AI_REPLY_CHECK_FRAUD = "ai.reply.check_fraud";

    private RabbitMQConstants() {
        // Prevent instantiation
    }
}
