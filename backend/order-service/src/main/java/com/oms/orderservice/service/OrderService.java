package com.oms.orderservice.service;

import com.oms.orderservice.client.InventoryClient;
import com.oms.orderservice.dto.InventoryUpdateRequest;
import com.oms.orderservice.dto.OrderItemRequest;
import com.oms.orderservice.dto.OrderRequest;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.entity.OrderAddress;
import com.oms.orderservice.entity.OrderItem;
import com.oms.orderservice.entity.OrderStatus;
import com.oms.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    @Transactional
    public String createOrder(OrderRequest request){
        for(OrderItemRequest orderItem : request.getOrderItems()){
            try{
                InventoryUpdateRequest reserveRequest = InventoryUpdateRequest.builder()
                        .productId(orderItem.getProductId())
                        .quantity(orderItem.getQuantity())
                        .type("RESERVE")
                        .build();
                inventoryClient.updateInventory(reserveRequest);
            }catch (Exception ex){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Out of stock or Inventory error: " + ex.getMessage());
            }
        }
        BigDecimal totalPrice = request.getOrderItems().stream()
                .map(item -> item.getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderAddress shippingAddress = new OrderAddress();
        BeanUtils.copyProperties(request.getAddress() , shippingAddress);

        Order order = Order.builder()
                .userId(request.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(totalPrice)
                .shippingAddress(shippingAddress)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<OrderItem> orderItems = request.getOrderItems().stream()
                .map(itemReq -> {
                    OrderItem item = OrderItem.builder()
                            .productId(itemReq.getProductId())
                            .productName(itemReq.getProductName())
                            .price(itemReq.getPrice())
                            .quantity(itemReq.getQuantity())
                            .order(order)
                            .build();
                    return item;
                }).collect(Collectors.toList());

        order.setOrderItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        return savedOrder.getId();

    }
}
