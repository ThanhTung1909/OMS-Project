package com.oms.orchestrator.config;

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

    public static final String QUEUE_ORCHESTRATOR_ORDER_CREATED = "q.orchestrator.order.created";
    public static final String QUEUE_ORCHESTRATOR_INVENTORY_REPLY = "q.orchestrator.inventory.reply";
    public static final String QUEUE_ORCHESTRATOR_PAYMENT_REPLY = "q.orchestrator.payment.reply";
    public static final String QUEUE_ORCHESTRATOR_DELIVERY_REPLY = "q.orchestrator.delivery.reply";
    public static final String QUEUE_ORCHESTRATOR_PAYMENT_URL = "q.orchestrator.payment.url";

    @Bean
    public TopicExchange omsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(QUEUE_ORCHESTRATOR_ORDER_CREATED, true);
    }

    @Bean
    public Queue inventoryReplyQueue() {
        return new Queue(QUEUE_ORCHESTRATOR_INVENTORY_REPLY, true);
    }

    @Bean
    public Queue paymentReplyQueue() {
        return new Queue(QUEUE_ORCHESTRATOR_PAYMENT_REPLY, true);
    }

    @Bean
    public Queue paymentUrlQueue() {
        return new Queue(QUEUE_ORCHESTRATOR_PAYMENT_URL, true);
    }

    @Bean
    public Queue deliveryReplyQueue() {
        return new Queue(QUEUE_ORCHESTRATOR_DELIVERY_REPLY, true);
    }

    @Bean
    public Binding bindingOrderCreated(Queue orderCreatedQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(omsExchange).with(RabbitMQConstants.RK_ORDER_EVENT_CREATED);
    }

    @Bean
    public Binding bindingInventoryReply(Queue inventoryReplyQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(inventoryReplyQueue).to(omsExchange).with(RabbitMQConstants.INVENTORY_REPLY_RESULT);
    }

    @Bean
    public Binding bindingPaymentReply(Queue paymentReplyQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(paymentReplyQueue).to(omsExchange).with(RabbitMQConstants.PAYMENT_REPLY_RESULT);
    }

    @Bean
    public Binding bindingPaymentUrl(Queue paymentUrlQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(paymentUrlQueue).to(omsExchange).with(RabbitMQConstants.PAYMENT_REPLY_URL_CREATED);
    }

    @Bean
    public Binding bindingDeliveryReply(Queue deliveryReplyQueue, TopicExchange omsExchange) {
        return BindingBuilder.bind(deliveryReplyQueue).to(omsExchange).with(RabbitMQConstants.DELIVERY_STATUS_UPDATE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
