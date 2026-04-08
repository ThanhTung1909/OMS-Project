package com.oms.orderservice.service;

import com.oms.orderservice.client.InventoryClient;
import com.oms.orderservice.dto.InventoryUpdateRequest;
import com.oms.orderservice.dto.OrderItemRequest;
import com.oms.orderservice.dto.OrderRequest;
import com.oms.orderservice.dto.PaymentCommand;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.entity.OrderAddress;
import com.oms.orderservice.entity.OrderItem;
import com.oms.orderservice.entity.OrderStatus;
import com.oms.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    @Transactional
    public String createOrder(OrderRequest request){
        // 1. Tính tổng tiền
        BigDecimal totalPrice = request.getOrderItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Chuẩn bị đối tượng Order
        OrderAddress shippingAddress = new OrderAddress();
        BeanUtils.copyProperties(request.getAddress(), shippingAddress);

        Order order = Order.builder()
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

        // 3. GỌI INVENTORY (Giữ kho)
        try {
            for (OrderItemRequest item : request.getOrderItems()) {
                InventoryUpdateRequest reserveReq = InventoryUpdateRequest.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .type("RESERVE")
                        .build();
                inventoryClient.updateInventory(reserveReq);
            }
        } catch (Exception ex) {
            log.error("Lỗi giữ kho: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm trong kho không đủ hoặc có lỗi hệ thống kho.");
        }

        // 4. Lưu DB thành công trả về ID cho FE
        Order savedOrder = orderRepository.save(order);

        PaymentCommand command = PaymentCommand.builder()
            .orderId(savedOrder.getId())
            .userId(savedOrder.getUserId())
            .amount(savedOrder.getTotalAmount())
            .description("Thanh toán đơn hàng: " + savedOrder.getId())
            .build();

        rabbitTemplate.convertAndSend("order.exchange", "payment.command.create", command);

        log.info("Order created with status PAYMENT_PENDING. Command sent to Payment Service for OrderId: {}", savedOrder.getId());

        return savedOrder.getId();

    }
}
