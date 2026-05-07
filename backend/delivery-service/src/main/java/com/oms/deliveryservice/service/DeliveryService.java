package com.oms.deliveryservice.service;

import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.enums.DeliveryStatus;
import com.oms.deliveryservice.entity.Delivery;
import com.oms.deliveryservice.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {
    
    private final DeliveryRepository deliveryRepository;
    private final RabbitTemplate rabbitTemplate;

    public Delivery createDelivery(com.oms.deliveryservice.dto.DeliveryRequest request) {
        String trackingNumber = generateTrackingNumber();
        
        Delivery delivery = Delivery.builder()
                .orderId(request.getOrderId())
                .trackingNumber(trackingNumber)
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .address(request.getAddress())
                .shipperName("OMS Logistics - Shipper")
                .shipperPhone("0988888888")
                .status(DeliveryStatus.READY_TO_UP)
                .build();
                
        Delivery savedDelivery = deliveryRepository.save(delivery);
        log.info("Created new delivery with tracking number {} for order {}", trackingNumber, request.getOrderId());
        return savedDelivery;
    }

    public Optional<Delivery> getByOrderId(String orderId) {
        return deliveryRepository.findByOrderId(orderId);
    }

    @org.springframework.transaction.annotation.Transactional
    public Optional<Delivery> updateStatus(String id, DeliveryStatus status, String failReason) {
        Optional<Delivery> optionalDelivery = deliveryRepository.findById(id);
        if (optionalDelivery.isPresent()) {
            Delivery delivery = optionalDelivery.get();
            delivery.setStatus(status);
            
            if (status == DeliveryStatus.RETURNED && failReason != null) {
                delivery.setFailReason(failReason);
            }
            
            Delivery updatedDelivery = deliveryRepository.save(delivery);
            
            // Saga Logic: Nếu trạng thái là DELIVERED hoặc RETURNED, gửi sự kiện báo cho SAGA
            if (status == DeliveryStatus.DELIVERED || status == DeliveryStatus.RETURNED) {
                String sagaStatus = status == DeliveryStatus.DELIVERED ? "COMPLETED" : "FAILED";
                com.oms.deliveryservice.dto.DeliveryUpdatePayload event = com.oms.deliveryservice.dto.DeliveryUpdatePayload.builder()
                        .orderId(delivery.getOrderId())
                        .deliveryId(delivery.getId())
                        .status(sagaStatus)
                        .failReason(delivery.getFailReason())
                        .build();
                
                log.info("Delivery {} for order {} is {}. Sending {} event to SAGA.", id, delivery.getOrderId(), status, sagaStatus);
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.DELIVERY_STATUS_UPDATE, event);
            }
            
            return Optional.of(updatedDelivery);
        }
        return Optional.empty();
    }
    
    private String generateTrackingNumber() {
        return "OMS-SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
