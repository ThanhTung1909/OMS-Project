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

    public Delivery createDelivery(com.oms.deliveryservice.dto.DeliveryCommand request) {
        String trackingNumber = generateTrackingNumber();
        
        Delivery delivery = Delivery.builder()
                .orderId(request.getOrderId())
                .trackingNumber(trackingNumber)
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .address(request.getAddress())
                .codAmount(request.getCodAmount())
                .shipperName("OMS Logistics - Shipper")
                .shipperPhone("0988888888")
                .status(DeliveryStatus.READY_TO_UP)
                .build();
                
        Delivery savedDelivery = deliveryRepository.save(delivery);
        log.info("Created new delivery with tracking number {} for order {}", trackingNumber, request.getOrderId());
        
        // Gửi thông báo "vừa tạo xong" để Order Service cập nhật deliveryId
        com.oms.deliveryservice.dto.DeliveryUpdatePayload event = com.oms.deliveryservice.dto.DeliveryUpdatePayload.builder()
                .orderId(savedDelivery.getOrderId())
                .deliveryId(savedDelivery.getId())
                .status(savedDelivery.getStatus().name())
                .trackingNumber(savedDelivery.getTrackingNumber())
                .shipperName(savedDelivery.getShipperName())
                .shipperPhone(savedDelivery.getShipperPhone())
                .createdAt(savedDelivery.getCreatedAt())
                .updatedAt(savedDelivery.getUpdatedAt())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConstants.EXCHANGE_NAME,
                RabbitMQConstants.RK_DELIVERY_STATUS_UPDATE,
                event);

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
            DeliveryStatus currentStatus = delivery.getStatus();

            if (currentStatus == status) {
                return Optional.of(delivery);
            }

            delivery.setStatus(status);
            
            if (status == DeliveryStatus.RETURNED && failReason != null) {
                delivery.setFailReason(failReason);
            }
            
            Delivery updatedDelivery = deliveryRepository.save(delivery);
            
            com.oms.deliveryservice.dto.DeliveryUpdatePayload event = com.oms.deliveryservice.dto.DeliveryUpdatePayload.builder()
                    .orderId(updatedDelivery.getOrderId())
                    .deliveryId(updatedDelivery.getId())
                    .status(updatedDelivery.getStatus().name())
                    .failReason(updatedDelivery.getFailReason())
                    .trackingNumber(updatedDelivery.getTrackingNumber())
                    .shipperName(updatedDelivery.getShipperName())
                    .shipperPhone(updatedDelivery.getShipperPhone())
                    .createdAt(updatedDelivery.getCreatedAt())
                    .updatedAt(updatedDelivery.getUpdatedAt())
                    .build();

            log.info("Delivery {} for order {} changed from {} to {}. Sending status update event.",
                    id, updatedDelivery.getOrderId(), currentStatus, status);
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.EXCHANGE_NAME,
                    RabbitMQConstants.RK_DELIVERY_STATUS_UPDATE,
                    event);
            
            return Optional.of(updatedDelivery);
        }
        return Optional.empty();
    }
    
    private String generateTrackingNumber() {
        return "OMS-SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
