package com.oms.paymentservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String PAYMENT_EXCHANGE = "payment_exchange";
    public static final String PAYMENT_ROUTING_KEY = "payment.reply.result";
    public static final String PAYMENT_REPLY_QUEUE = "payment.reply.result.queue";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Queue paymentReplyQueue() {
        return new Queue(PAYMENT_REPLY_QUEUE, true);
    }

    @Bean
    public Binding paymentReplyBinding(Queue paymentReplyQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentReplyQueue).to(paymentExchange).with(PAYMENT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
