package com.oms.orderservice.service;

import com.oms.common.ApiResponse;
import com.oms.common.AppException;
import com.oms.common.CommonErrorCode;
import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.enums.OrderStatus;
import com.oms.orderservice.client.InventoryClient;
import com.oms.orderservice.client.ProductClient;
import com.oms.orderservice.dto.*;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.entity.OrderAddress;
import com.oms.orderservice.entity.OrderItem;
import com.oms.orderservice.exception.OrderErrorCode;
import com.oms.orderservice.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final ProductClient productClient;
    private final RabbitTemplate rabbitTemplate;

    public org.springframework.data.domain.Page<OrderResponse> getMyOrders(String userId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToOrderResponse);
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        log.info("Bắt đầu tạo đơn hàng {}. Thông tin người nhận từ request: Name={}, Phone={}", 
                orderId, request.getAddress().getReceiverName(), request.getAddress().getReceiverPhone());

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<InventoryReserveRequest> reserveRequests = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getOrderItems()) {
            ApiResponse<ProductResponse> productApiRes = productClient.getProductById(itemReq.getProductId());
            if (productApiRes == null || !productApiRes.isSuccess() || productApiRes.getResult() == null) {
                throw new AppException(OrderErrorCode.ORDER_CREATION_FAILED);
            }
            ProductResponse product = productApiRes.getResult();
            BigDecimal itemPrice = product.getPrice();
            
            totalPrice = totalPrice.add(itemPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .price(itemPrice)
                    .quantity(itemReq.getQuantity())
                    .build();
            orderItems.add(orderItem);

            InventoryReserveRequest reserveReq = new InventoryReserveRequest();
            reserveReq.setProductId(product.getId());
            reserveReq.setQuantity(itemReq.getQuantity());
            reserveRequests.add(reserveReq);
        }

        AddressRequest addrReq = request.getAddress();
        OrderAddress shippingAddress = new OrderAddress();
        BeanUtils.copyProperties(addrReq, shippingAddress);

        Order order = Order.builder()
                .id(orderId)
                .userId(request.getUserId())
                .status("COD".equalsIgnoreCase(request.getPaymentMethod()) ? OrderStatus.CONFIRMED : OrderStatus.PAYMENT_PENDING)
                .totalAmount(totalPrice)
                .paymentMethod(request.getPaymentMethod())
                .shippingAddress(shippingAddress)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setOrderItems(orderItems);

        boolean inventoryReserved = false;
        try {
            reserveInventory(reserveRequests);
            inventoryReserved = true;

            orderRepository.save(order);

            if ("COD".equalsIgnoreCase(request.getPaymentMethod())) {
                log.info("Order {} is COD. Confirming inventory and sending notification immediately.", orderId);
                
                // 1. Xác nhận kho luôn
                for (OrderItem item : orderItems) {
                    InventoryCommand confirmCmd = new InventoryCommand(orderId, item.getProductId(), item.getQuantity(), "CONFIRM");
                    rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_CONFIRM, confirmCmd);
                }

                // 2. Bắn thông báo
                NotificationEvent notifyEvent = NotificationEvent.builder()
                        .orderId(orderId)
                        .userId(request.getUserId())
                        .status("CONFIRMED")
                        .message("Đơn hàng đã đặt thành công (COD). Đang chờ đóng gói.")
                        .build();
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.NOTIFICATION_ORDER_STATUS, notifyEvent);

            } else {
                // Bắn event payment cho thanh toán online
                PaymentCommand command = PaymentCommand.builder()
                        .orderId(orderId)
                        .userId(request.getUserId())
                        .amount(totalPrice)
                        .description("Thanh toán đơn hàng: " + orderId)
                        .build();
                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.PAYMENT_COMMAND_CREATE, command);
                log.info("Order created with status PAYMENT_PENDING. Command sent to Payment Service for OrderId: {}", orderId);
            }

            return mapToOrderResponse(order);

        } catch (Exception ex) {
            log.error("Lỗi tạo đơn hàng {}: {}", orderId, ex.getMessage());

            if (inventoryReserved) {
                for (OrderItem item : orderItems) {
                    InventoryCommand rollbackCmd = new InventoryCommand(orderId, item.getProductId(), item.getQuantity(), "ROLLBACK");
                    rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_ROLLBACK, rollbackCmd);
                }
            }
            throw new AppException(OrderErrorCode.ORDER_CREATION_FAILED);
        }
    }

    @CircuitBreaker(name = "inventoryCB", fallbackMethod = "fallbackReserve")
    public void reserveInventory(List<InventoryReserveRequest> requests) {
        inventoryClient.reserveBulk(requests);
    }

    public void fallbackReserve(List<InventoryReserveRequest> requests, Throwable t) {
        log.error("Inventory Service is down. Cannot reserve inventory.", t);
        throw new AppException(OrderErrorCode.ORDER_CREATION_FAILED);
    }

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

        NotificationEvent notifyEvent = NotificationEvent.builder()
                .orderId(orderId)
                .userId(order.getUserId())
                .status("SHIPPING")
                .message("Đơn hàng đã được duyệt và đang chuẩn bị giao cho đơn vị vận chuyển.")
                .build();
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.NOTIFICATION_ORDER_STATUS, notifyEvent);

        try {
            OrderAddress addr = order.getShippingAddress();
            String fullAddress = java.util.stream.Stream.of(
                    addr.getStreet(), addr.getWard(), addr.getDistrict(), addr.getCity())
                    .filter(s -> s != null && !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(", "));

            BigDecimal codAmount = "COD".equalsIgnoreCase(order.getPaymentMethod()) 
                    ? order.getTotalAmount() 
                    : BigDecimal.ZERO;

            DeliveryRequest deliveryRequest = DeliveryRequest.builder()
                    .orderId(orderId)
                    .receiverName(addr.getReceiverName())
                    .receiverPhone(addr.getReceiverPhone())
                    .address(fullAddress)
                    .codAmount(codAmount)
                    .build();

            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.DELIVERY_COMMAND_CREATE, deliveryRequest);
        } catch (Exception e) {
            log.error("Lỗi khi gửi lệnh tạo vận đơn cho Order {}: {}", orderId, e.getMessage());
        }
    }

    @Transactional
    public void cancelOrder(String orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new AppException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        for (OrderItem item : order.getOrderItems()) {
            InventoryCommand rollbackCmd = new InventoryCommand(orderId, item.getProductId(), item.getQuantity(), "ROLLBACK");
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.INVENTORY_COMMAND_ROLLBACK, rollbackCmd);
        }
    }

    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        return mapToOrderResponse(order);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        // Enrich hình ảnh sản phẩm từ Product Service
        enrichProductImages(itemResponses);

        AddressRequest addrResponse = new AddressRequest();
        if (order.getShippingAddress() != null) {
            BeanUtils.copyProperties(order.getShippingAddress(), addrResponse);
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .message("Chi tiết đơn hàng #" + order.getId())
                .totalAmount(order.getTotalAmount())
                .paymentId(order.getPaymentId())
                .deliveryId(order.getDeliveryId())
                .paymentMethod(order.getPaymentMethod())
                .errorMessage(order.getErrorMessage())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .shippingAddress(addrResponse)
                .orderItems(itemResponses)
                .build();
    }

    public String getUserIdByOrderId(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        return order.getUserId();
    }

    /**
     * Enrich thumbnail hình ảnh sản phẩm cho từng OrderItemResponse.
     * Gọi ProductClient theo từng productId, lấy ảnh đầu tiên (thumbnail).
     * Fallback: imageUrl = null nếu Product Service không phản hồi hoặc sản phẩm không có ảnh.
     *
     * @param items Danh sách OrderItemResponse cần enrich
     */
    private void enrichProductImages(List<OrderItemResponse> items) {
        if (items == null || items.isEmpty()) return;

        // Build map productId -> thumbnail URL
        Map<String, String> imageMap = new HashMap<>();
        for (OrderItemResponse item : items) {
            try {
                ApiResponse<ProductResponse> res = productClient.getProductById(item.getProductId());
                if (res != null && res.isSuccess() && res.getResult() != null) {
                    List<String> urls = res.getResult().getImageUrl();
                    if (urls != null && !urls.isEmpty()) {
                        imageMap.put(item.getProductId(), urls.get(0));
                    }
                }
            } catch (Exception e) {
                log.warn("Không thể lấy hình ảnh cho sản phẩm {}: {}", item.getProductId(), e.getMessage());
            }
        }

        // Gán thumbnail vào từng item (null nếu không có)
        items.forEach(item -> item.setImageUrl(imageMap.get(item.getProductId())));
    }
}
