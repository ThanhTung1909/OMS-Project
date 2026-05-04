package com.example.paymentservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public TopicExchange omsExchange() {
        return new TopicExchange("oms.exchange", true, false);
    }
}
