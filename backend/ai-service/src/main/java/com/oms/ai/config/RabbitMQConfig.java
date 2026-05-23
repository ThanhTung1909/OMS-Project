package com.oms.ai.config;

import com.oms.common.constant.RabbitMQConstants;
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

    public static final String QUEUE_AI_FRAUD_CHECK = "q.ai.fraud.check";

    @Bean
    public TopicExchange omsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue aiFraudCheckQueue() {
        return new Queue(QUEUE_AI_FRAUD_CHECK, true);
    }

    @Bean
    public Binding bindingAiFraudCheck(Queue aiFraudCheckQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(aiFraudCheckQueue).to(omsExchange).with(RabbitMQConstants.AI_COMMAND_CHECK_FRAUD);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
