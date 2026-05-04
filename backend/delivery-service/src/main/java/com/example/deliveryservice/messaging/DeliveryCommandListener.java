package com.example.deliveryservice.messaging;

import com.example.deliveryservice.service.DeliveryService;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeliveryCommandListener {
    @Autowired
    private DeliveryService deliveryService;

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(value = "q.delivery.create", durable = "true"),
        exchange = @Exchange(value = "oms.exchange", type = "topic"),
        key = "delivery.command.create"
    ))
    public void handleCreateDelivery(DeliveryCreateEvent event) {
        deliveryService.createDelivery(event.getOrderId());
    }

    // Inner class for event payload (can be replaced by a shared DTO)
    public static class DeliveryCreateEvent {
        private Long orderId;
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }
}
