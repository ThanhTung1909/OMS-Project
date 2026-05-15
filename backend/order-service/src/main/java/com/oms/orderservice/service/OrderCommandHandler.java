package com.oms.orderservice.service;

import com.oms.common.dto.DeliveryUpdatePayload;
import com.oms.common.dto.OrderStatusUpdateCommand;
import com.oms.common.enums.OrderStatus;
import com.oms.orderservice.config.RabbitMQConfig;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCommandHandler {

    private final OrderRepository orderRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_COMMAND_UPDATE)
    @Transactional
    public void handleOrderStatusUpdate(OrderStatusUpdateCommand command) {
        if (command == null || command.getOrderId() == null) return;

        log.info("[ORDER-SERVICE] Nhận lệnh OrderStatusUpdateCommand cho đơn hàng: {}, Trạng thái mới: {}", 
                command.getOrderId(), command.getNewStatus());

        Order order = orderRepository.findById(command.getOrderId())
                .orElse(null);

        if (order == null) {
            log.error("[ORDER-SERVICE] Không tìm thấy đơn hàng: {}", command.getOrderId());
            return;
        }

        // Cập nhật trạng thái và các thông tin liên quan
        order.setStatus(command.getNewStatus());
        order.setUpdatedAt(LocalDateTime.now());
        
        if (command.getPaymentId() != null) {
            order.setPaymentId(command.getPaymentId());
        }

        if (command.getErrorMessage() != null) {
            order.setErrorMessage(command.getErrorMessage());
        } else {
            // Nếu không có lỗi, đảm bảo errorMessage là null (tránh gán message thành công vào error)
            order.setErrorMessage(null);
        }

        orderRepository.save(order);
        log.info("[ORDER-SERVICE] Đã cập nhật đơn hàng {} sang trạng thái {}", order.getId(), order.getStatus());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELIVERY_STATUS)
    @Transactional
    public void handleDeliveryStatusUpdate(DeliveryUpdatePayload payload) {
        if (payload == null || payload.getOrderId() == null) return;

        log.info("[ORDER-SERVICE] Nhận cập nhật trạng thái vận đơn: {}, DeliveryId: {}, Status: {}", 
                payload.getOrderId(), payload.getDeliveryId(), payload.getStatus());

        Order order = orderRepository.findById(payload.getOrderId()).orElse(null);
        if (order == null) {
            log.error("[ORDER-SERVICE] Không tìm thấy đơn hàng cho vận đơn: {}", payload.getOrderId());
            return;
        }

        // 1. Cập nhật deliveryId ngay khi nhận được (bất kể trạng thái nào)
        if (payload.getDeliveryId() != null) {
            order.setDeliveryId(payload.getDeliveryId());
        }

        // 2. Cập nhật trạng thái đơn hàng dựa trên vận đơn
        String status = payload.getStatus();
        if ("COMPLETED".equals(status) || "DELIVERED".equals(status)) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setErrorMessage(null);
        } else if ("RETURNED".equals(status)) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setErrorMessage("Giao hàng thất bại: " + payload.getFailReason());
        }

        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }
}
