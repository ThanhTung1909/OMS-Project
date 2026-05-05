package com.oms.inventoryservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình RabbitMQ cho Inventory Service
 * Định nghĩa các queue, exchange, và binding cho events
 */
@Configuration
public class RabbitMQConfig {

    // Queue names
    public static final String INVENTORY_CONFIRM_QUEUE = "q.inventory.confirm";
    public static final String INVENTORY_ROLLBACK_QUEUE = "q.inventory.rollback";

    // Exchange name
    public static final String OMS_EXCHANGE = "oms.exchange";

    // Routing keys
    public static final String CONFIRM_ROUTING_KEY = "inventory.command.confirm";
    public static final String ROLLBACK_ROUTING_KEY = "inventory.command.rollback";

    // ===== CONFIRM COMMAND =====
    @Bean
    public Queue confirmQueue() {
        return new Queue(INVENTORY_CONFIRM_QUEUE, true, false, false);
    }

    // ===== ROLLBACK COMMAND =====
    @Bean
    public Queue rollbackQueue() {
        return new Queue(INVENTORY_ROLLBACK_QUEUE, true, false, false);
    }

    // ===== EXCHANGE =====
    @Bean
    public TopicExchange inventoryExchange() {
        return new TopicExchange(OMS_EXCHANGE, true, false);
    }

    // ===== BINDINGS =====
    @Bean
    public Binding confirmBinding(Queue confirmQueue, TopicExchange inventoryExchange) {
        return BindingBuilder.bind(confirmQueue)
                .to(inventoryExchange)
                .with(CONFIRM_ROUTING_KEY);
    }

    @Bean
    public Binding rollbackBinding(Queue rollbackQueue, TopicExchange inventoryExchange) {
        return BindingBuilder.bind(rollbackQueue)
                .to(inventoryExchange)
                .with(ROLLBACK_ROUTING_KEY);
    }
}
