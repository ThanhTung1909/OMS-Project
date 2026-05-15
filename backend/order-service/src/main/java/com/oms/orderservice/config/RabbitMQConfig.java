package com.oms.orderservice.config;

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

    public static final String QUEUE_PAYMENT_REPLY = "q.order.payment.reply";
    public static final String QUEUE_DELIVERY_STATUS = "q.order.delivery.status";
    public static final String QUEUE_ORDER_COMMAND_UPDATE = "q.order.command.update";

    @Bean
    public TopicExchange omsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue paymentReplyQueue() {
        return new Queue(QUEUE_PAYMENT_REPLY, true);
    }

    @Bean
    public Queue deliveryStatusQueue() {
        return new Queue(QUEUE_DELIVERY_STATUS, true);
    }

    @Bean
    public Queue orderCommandUpdateQueue() {
        return new Queue(QUEUE_ORDER_COMMAND_UPDATE, true);
    }

    @Bean
    public Binding bindingPaymentReply(Queue paymentReplyQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(paymentReplyQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.PAYMENT_REPLY_RESULT);
    }

    @Bean
    public Binding bindingDeliveryStatus(Queue deliveryStatusQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(deliveryStatusQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.DELIVERY_STATUS_UPDATE);
    }

    @Bean
    public Binding bindingOrderCommandUpdate(Queue orderCommandUpdateQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(orderCommandUpdateQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.RK_ORDER_COMMAND_UPDATE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
