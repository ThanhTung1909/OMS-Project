package com.oms.deliveryservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public TopicExchange omsExchange() {
        System.out.println("[DEBUG] Creating oms.exchange topic exchange!");
        return new TopicExchange("oms.exchange", true, false);
    }
}
