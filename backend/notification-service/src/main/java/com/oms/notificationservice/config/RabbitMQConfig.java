package com.oms.notificationservice.config;

import com.oms.common.constant.RabbitMQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
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

    public static final String QUEUE_NOTIFICATION_ACCOUNT_CREATE = "q.notification.account.create";
    public static final String QUEUE_NOTIFICATION_ORDER_STATUS = "q.notification.order.status";
    public static final String QUEUE_NOTIFICATION_DELIVERY_STATUS = "q.notification.delivery.status";
    public static final String QUEUE_NOTIFICATION_FORGOT_PASSWORD = "q.notification.forgot.password";
    public static final String QUEUE_NOTIFICATION_STOCK_LOW = "q.notification.stock.low";

    @Bean
    public TopicExchange omsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue notificationAccountCreateQueue() {
        return new Queue(QUEUE_NOTIFICATION_ACCOUNT_CREATE, true);
    }

    @Bean
    public Queue notificationOrderStatusQueue() {
        return new Queue(QUEUE_NOTIFICATION_ORDER_STATUS, true);
    }

    @Bean
    public Queue notificationDeliveryStatusQueue() {
        return new Queue(QUEUE_NOTIFICATION_DELIVERY_STATUS, true);
    }

    @Bean
    public Queue notificationForgotPasswordQueue() {
        return new Queue(QUEUE_NOTIFICATION_FORGOT_PASSWORD, true);
    }

    @Bean
    public Queue notificationStockLowQueue() {
        return new Queue(QUEUE_NOTIFICATION_STOCK_LOW, true);
    }

    @Bean
    public Binding bindingNotificationAccountCreate(Queue notificationAccountCreateQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(notificationAccountCreateQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.IDENTITY_ACCOUNT_CREATED);
    }

    @Bean
    public Binding bindingNotificationOrderStatus(Queue notificationOrderStatusQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(notificationOrderStatusQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.NOTIFICATION_ORDER_STATUS);
    }

    @Bean
    public Binding bindingNotificationDeliveryStatus(Queue notificationDeliveryStatusQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(notificationDeliveryStatusQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.DELIVERY_STATUS_UPDATE);
    }

    @Bean
    public Binding bindingNotificationForgotPassword(Queue notificationForgotPasswordQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(notificationForgotPasswordQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.IDENTITY_FORGOT_PASSWORD_REQUESTED);
    }

    @Bean
    public Binding bindingNotificationStockLow(Queue notificationStockLowQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(notificationStockLowQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.NOTIFICATION_STOCK_LOW);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
