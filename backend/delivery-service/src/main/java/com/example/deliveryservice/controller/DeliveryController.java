package com.example.deliveryservice.controller;

import com.example.deliveryservice.entity.Delivery;
import com.example.deliveryservice.entity.DeliveryStatus;
import com.example.deliveryservice.service.DeliveryService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PatchMapping("/{orderId}/status")
    public Map<String, Object> updateStatus(@PathVariable Long orderId, @RequestParam DeliveryStatus status) {
        Optional<Delivery> updated = deliveryService.updateStatus(orderId, status);
        Map<String, Object> response = new HashMap<>();
        if (updated.isPresent()) {
            Delivery delivery = updated.get();
            // Gửi event khi trạng thái là DELIVERING hoặc DELIVERED
            if (status == DeliveryStatus.DELIVERING || status == DeliveryStatus.DELIVERED) {
                Map<String, Object> event = new HashMap<>();
                event.put("orderId", delivery.getOrderId());
                event.put("deliveryId", delivery.getId());
                event.put("status", delivery.getStatus().name());
                rabbitTemplate.convertAndSend("oms.exchange", "delivery.status.update", event);
            }
            response.put("success", true);
            response.put("delivery", delivery);
        } else {
            response.put("success", false);
            response.put("message", "Delivery not found");
        }
        return response;
    }
}
