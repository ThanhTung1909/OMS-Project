package com.oms.profile.config;

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

    public static final String QUEUE_PROFILE_ACCOUNT_CREATE = "q.profile.account.create";

    @Bean
    public TopicExchange omsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue profileAccountCreateQueue() {
        return new Queue(QUEUE_PROFILE_ACCOUNT_CREATE, true);
    }

    @Bean
    public Binding bindingProfileAccountCreate(Queue profileAccountCreateQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(profileAccountCreateQueue)
                .to(omsExchange)
                .with(RabbitMQConstants.IDENTITY_ACCOUNT_CREATED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
