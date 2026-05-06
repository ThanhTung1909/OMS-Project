package com.oms.inventoryservice.config;

import com.oms.common.constant.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    public static final String QUEUE_INVENTORY_CONFIRM = "q.inventory.confirm";
    public static final String QUEUE_INVENTORY_ROLLBACK = "q.inventory.rollback";

    @Bean
    public TopicExchange omsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue inventoryConfirmQueue() {
        return new Queue(QUEUE_INVENTORY_CONFIRM, true);
    }

    @Bean
    public Queue inventoryRollbackQueue() {
        return new Queue(QUEUE_INVENTORY_ROLLBACK, true);
    }

    @Bean
    public Binding bindingInventoryConfirm(Queue inventoryConfirmQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(inventoryConfirmQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.INVENTORY_COMMAND_CONFIRM);
    }

    @Bean
    public Binding bindingInventoryRollback(Queue inventoryRollbackQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(inventoryRollbackQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.INVENTORY_COMMAND_ROLLBACK);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
