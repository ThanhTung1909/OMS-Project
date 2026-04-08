package com.oms.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // 1. Khai báo Exchange chung cho toàn bộ hệ thống Order (nên dùng TopicExchange)
    public static final String EXCHANGE = "order.exchange";

    // 2. Khai báo Tên Queue mà Order Service sẽ lắng nghe (Saga Orchestrator)
    public static final String PAYMENT_REPLY_QUEUE = "payment.reply.result.queue";

    // 3. Khai báo Routing Key (Theo đúng tài liệu của bạn)
    public static final String PAYMENT_REPLY_ROUTING_KEY = "payment.reply.result";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue paymentReplyQueue() {
        return new Queue(PAYMENT_REPLY_QUEUE, true);
    }

    // 4. Binding: Nối Queue vào Exchange thông qua Routing Key
    @Bean
    public Binding bindingPaymentReply(Queue paymentReplyQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(paymentReplyQueue).to(orderExchange).with(PAYMENT_REPLY_ROUTING_KEY);
    }

    // 5. Quan trọng: Converter để tự động chuyển đổi Object <-> JSON khi gửi/nhận
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
