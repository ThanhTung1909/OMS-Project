package com.oms.reporting.event;

import com.oms.common.dto.DeliveryStatusUpdatedEvent;
import com.oms.reporting.entity.ShipperPerformanceStatistics;
import com.oms.reporting.repository.ShipperPerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventListener {

    private final ShipperPerformanceRepository shipperPerformanceRepository;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "reporting.delivery.status.queue", durable = "true"),
            exchange = @Exchange(value = "oms.exchange", type = "topic", ignoreDeclarationExceptions = "true"),
            key = "delivery.status.update"
    ))
    @Transactional
    public void handleDeliveryStatusUpdate(DeliveryStatusUpdatedEvent event) {
        log.info("Received DeliveryStatusUpdatedEvent for Delivery ID: {}, Status: {}", event.getDeliveryId(), event.getStatus());

        ShipperPerformanceStatistics shipperStat = shipperPerformanceRepository.findByShipperPhone(event.getShipperPhone())
                .orElse(ShipperPerformanceStatistics.builder()
                        .id(UUID.randomUUID().toString())
                        .shipperName(event.getShipperName())
                        .shipperPhone(event.getShipperPhone())
                        .build());

        // Increment total deliveries when a shipper takes it (assuming IN_TRANSIT or similar initially, but let's just increment if it's new or rely on the event)
        // If we want to strictly count total deliveries, maybe we do it on "CREATED" or "PICKED_UP". The plan says "Tăng total_deliveries khi shipper bắt đầu nhận đơn". 
        // For simplicity, we can increment total if they haven't been counted for this delivery, but event-driven might send multiple status updates.
        // Let's assume the plan implies if we see a NEW/PICKING status, we +1 total. Or just +1 total for any non-terminal state.
        // Actually, to keep it idempotent, it's better to calculate. But the plan says: "Tăng total_deliveries khi shipper bắt đầu nhận đơn". 
        // If we get an event, we'll assume it's just a status update. We should probably only increment total if status == "PICKED_UP". 
        // If not provided, we will just manage terminal states.
        
        if ("PICKED_UP".equalsIgnoreCase(event.getStatus())) {
            shipperStat.setTotalDeliveries(shipperStat.getTotalDeliveries() + 1);
        } else if ("DELIVERED".equalsIgnoreCase(event.getStatus())) {
            shipperStat.setSuccessfulDeliveries(shipperStat.getSuccessfulDeliveries() + 1);
            if (event.getCreatedAt() != null && event.getUpdatedAt() != null) {
                long seconds = Duration.between(event.getCreatedAt(), event.getUpdatedAt()).getSeconds();
                shipperStat.setTotalDeliveryTimeSeconds(shipperStat.getTotalDeliveryTimeSeconds() + seconds);
            }
        } else if ("FAILED".equalsIgnoreCase(event.getStatus()) || "RETURNED".equalsIgnoreCase(event.getStatus())) {
            shipperStat.setFailedDeliveries(shipperStat.getFailedDeliveries() + 1);
        }
        
        // Always ensure shipper name is up to date
        if (event.getShipperName() != null) {
            shipperStat.setShipperName(event.getShipperName());
        }

        shipperPerformanceRepository.save(shipperStat);
    }
}
