package com.example.deliveryservice.service;

import com.example.deliveryservice.entity.Delivery;
import com.example.deliveryservice.entity.DeliveryStatus;
import com.example.deliveryservice.repository.DeliveryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DeliveryService {
    @Autowired
    private DeliveryRepository deliveryRepository;

    public Delivery createDelivery(Long orderId) {
        Delivery delivery = new Delivery(orderId, DeliveryStatus.READY_TO_UP);
        return deliveryRepository.save(delivery);
    }

    public Optional<Delivery> getByOrderId(Long orderId) {
        return deliveryRepository.findByOrderId(orderId);
    }

    public Optional<Delivery> updateStatus(Long orderId, DeliveryStatus status) {
        Optional<Delivery> optionalDelivery = deliveryRepository.findByOrderId(orderId);
        if (optionalDelivery.isPresent()) {
            Delivery delivery = optionalDelivery.get();
            delivery.setStatus(status);
            return Optional.of(deliveryRepository.save(delivery));
        }
        return Optional.empty();
    }
}
