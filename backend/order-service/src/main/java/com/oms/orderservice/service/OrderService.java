package com.oms.orderservice.service;

import com.oms.common.AppException;
import com.oms.common.CommonErrorCode;
import com.oms.orderservice.client.InventoryClient;
import com.oms.orderservice.config.RabbitMQConfig;
import com.oms.orderservice.dto.InventoryUpdateRequest;
import com.oms.orderservice.dto.OrderItemRequest;
import com.oms.orderservice.dto.OrderRequest;
import com.oms.orderservice.dto.OrderResponse;
import com.oms.orderservice.dto.PaymentCommand;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.entity.OrderAddress;
import com.oms.orderservice.entity.OrderItem;
import com.oms.orderservice.entity.OrderStatus;
import com.oms.orderservice.exception.OrderErrorCode;
import com.oms.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final RabbitTemplate rabbitTemplate;

    // 1. Lấy lịch sử đơn hàng
    public List<OrderResponse> getMyOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> OrderResponse.builder()
                        .orderId(order.getId())
                        .status(order.getStatus().name())
                        .message("Đơn hàng tạo lúc: " + order.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // 2. Tạo đơn hàng với Local Compensation
    @Transactional
    public String createOrder(OrderRequest request) {
        // Tạo UUID trước để dùng cho compensation nếu cần
        String orderId = java.util.UUID.randomUUID().toString();

        BigDecimal totalPrice = request.getOrderItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderAddress shippingAddress = new OrderAddress();
        BeanUtils.copyProperties(request.getAddress(), shippingAddress);

        Order order = Order.builder()
                .id(orderId)
                .userId(request.getUserId())
                .status(OrderStatus.PAYMENT_PENDING)
                .totalAmount(totalPrice)
                .shippingAddress(shippingAddress)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<OrderItem> orderItems = request.getOrderItems().stream()
                .map(itemReq -> OrderItem.builder()
                        .productId(itemReq.getProductId())
                        .productName(itemReq.getProductName())
                        .price(itemReq.getPrice())
                        .quantity(itemReq.getQuantity())
                        .order(order)
                        .build())
                .collect(Collectors.toList());
        order.setOrderItems(orderItems);

        boolean inventoryReserved = false;
        try {
            // Bước A: Giữ kho
            for (OrderItemRequest item : request.getOrderItems()) {
                inventoryClient.updateInventory(new InventoryUpdateRequest(item.getProductId(), item.getQuantity(), "RESERVE"));
            }
            inventoryReserved = true;

            // Bước B: Lưu DB
            orderRepository.save(order);

            // Bước C: Bắn lệnh thanh toán
            PaymentCommand command = PaymentCommand.builder()
                    .orderId(orderId)
                    .userId(request.getUserId())
                    .amount(totalPrice)
                    .description("Thanh toán đơn hàng: " + orderId)
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "payment.command.create", command);

            log.info("Order created with status PAYMENT_PENDING. Command sent to Payment Service for OrderId: {}", orderId);

            return orderId;

        } catch (Exception ex) {
            log.error("Lỗi tạo đơn hàng {}: {}", orderId, ex.getMessage());

            // Compensation: Trả kho nếu đã giữ thành công
            if (inventoryReserved) {
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "inventory.command.rollback", orderId);
            }

            // Throw exception để rollback DB Transaction (Order sẽ không bị lưu record lỗi)
            throw new AppException(OrderErrorCode.ORDER_CREATION_FAILED);
        }
    }

    // 3. Duyệt đơn (Prepare)
    @Transactional
    public void prepareOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new AppException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        order.setStatus(OrderStatus.SHIPPING);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    // 4. Hủy đơn (Cancel)
    @Transactional
    public void cancelOrder(String orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        // Chỉ được hủy khi đang chờ thanh toán hoặc mới xác nhận
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new AppException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Bắn event trả hàng về kho
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "inventory.command.rollback", orderId);
    }

    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        return OrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .message("Thông tin đơn hàng")
                .build();
    }
}
