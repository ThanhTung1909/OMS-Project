package com.oms.deliveryservice.listener;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.deliveryservice.repository.DeliveryRepository;
import com.oms.deliveryservice.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventListener {

    private final DeliveryService deliveryService;
    private final DeliveryRepository deliveryRepository;

    @RabbitListener(queues = com.oms.deliveryservice.config.RabbitMQConfig.QUEUE_DELIVERY_COMMAND)
    public void handleDeliveryCreateCommand(com.oms.deliveryservice.dto.DeliveryCommand payload) {
        if (payload == null || payload.getOrderId() == null) {
            log.warn("Received invalid delivery create command payload");
            return;
        }
        
        String orderId = payload.getOrderId();
        log.info("Received delivery create command for orderId: {}", orderId);

        // Idempotency Logic
        if (deliveryRepository.existsByOrderId(orderId)) {
            log.warn("Delivery already exists for orderId: {}. Skipping to prevent duplicate.", orderId);
            return;
        }

        try {
            deliveryService.createDelivery(payload);
            log.info("Successfully processed delivery create command for orderId: {}", orderId);
        } catch (Exception e) {
            log.error("Error creating delivery for orderId: {}", orderId, e);
            throw e; 
        }
    }
}
