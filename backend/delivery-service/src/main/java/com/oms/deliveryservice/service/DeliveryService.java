package com.oms.deliveryservice.service;

import com.oms.deliveryservice.entity.Delivery;
import com.oms.deliveryservice.entity.DeliveryStatus;
import com.oms.deliveryservice.repository.DeliveryRepository;
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
            delivery.setUpdatedAt(java.time.LocalDateTime.now());
            return Optional.of(deliveryRepository.save(delivery));
        }
        return Optional.empty();
    }
}
