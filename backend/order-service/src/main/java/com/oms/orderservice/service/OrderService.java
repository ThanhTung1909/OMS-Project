package com.oms.orderservice.service;

import com.oms.common.ApiResponse;
import com.oms.common.AppException;
import com.oms.common.CommonErrorCode;
import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.enums.OrderStatus;
import com.oms.common.dto.DeliveryCommand;
import com.oms.common.dto.NotificationEvent;
import com.oms.common.dto.OrderCreatedEvent;
import com.oms.orderservice.dto.*;
import com.oms.orderservice.entity.Order;
import com.oms.orderservice.entity.OrderAddress;
import com.oms.orderservice.entity.OrderItem;
import com.oms.orderservice.exception.OrderErrorCode;
import com.oms.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.oms.common.constant.RedisConstants;
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
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public org.springframework.data.domain.Page<OrderResponse> getMyOrders(String userId, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToOrderResponse);
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        log.info("[ORDER-SERVICE] Khởi tạo đơn hàng: {}. User: {}", orderId, request.getUserId());

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderCreatedEvent.OrderItem> eventItems = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getOrderItems()) {
            // 1. Lấy Giá và Tên từ Redis (CQRS - Shared Redis)
            String priceKey = RedisConstants.PREFIX_PRODUCT_PRICE + itemReq.getProductId();
            String nameKey = RedisConstants.PREFIX_PRODUCT_NAME + itemReq.getProductId();
            
            String priceStr = stringRedisTemplate.opsForValue().get(priceKey);
            String productName = stringRedisTemplate.opsForValue().get(nameKey);

            if (priceStr == null || productName == null) {
                log.warn("[ORDER-SERVICE] Không tìm thấy dữ liệu sản phẩm {} trong Redis. Fallback: {}", 
                        itemReq.getProductId(), "ORDER_CREATION_FAILED");
                throw new AppException(OrderErrorCode.PRODUCT_NOT_FOUND);
            }

            BigDecimal itemPrice = new BigDecimal(priceStr);
            
            totalPrice = totalPrice.add(itemPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity())));

            OrderItem orderItem = OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .productName(productName)
                    .price(itemPrice)
                    .quantity(itemReq.getQuantity())
                    .build();
            orderItems.add(orderItem);

            eventItems.add(OrderCreatedEvent.OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .productName(productName)
                    .price(itemPrice)
                    .quantity(itemReq.getQuantity())
                    .build());
        }

        AddressRequest addrReq = request.getAddress();
        OrderAddress shippingAddress = new OrderAddress();
        BeanUtils.copyProperties(addrReq, shippingAddress);

        Order order = Order.builder()
                .id(orderId)
                .userId(request.getUserId())
                .status(OrderStatus.PENDING_VALIDATION)
                .totalAmount(totalPrice)
                .paymentMethod(request.getPaymentMethod())
                .shippingAddress(shippingAddress)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setOrderItems(orderItems);

        // Lưu đơn hàng vào DB với trạng thái PENDING_VALIDATION
        orderRepository.save(order);

        // Chỉ bắn duy nhất 1 event OrderCreatedEvent để Orchestrator xử lý tiếp
        String fullAddress = java.util.stream.Stream.of(
                        addrReq.getStreet(), addrReq.getWard(), addrReq.getCity())
                .filter(s -> s != null && !s.isEmpty())
                .collect(java.util.stream.Collectors.joining(", "));

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(orderId)
                .userId(request.getUserId())
                .totalAmount(totalPrice)
                .receiverName(addrReq.getReceiverName())
                .receiverPhone(addrReq.getReceiverPhone())
                .address(fullAddress)
                .paymentMethod(order.getPaymentMethod())
                .items(eventItems)
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.RK_ORDER_EVENT_CREATED, event);
        
        log.info("[ORDER-SERVICE] Đã lưu đơn hàng và gửi sự kiện OrderCreatedEvent cho Orchestrator (Mã đơn: {})", orderId);

        return mapToOrderResponse(order);
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

        // Gửi thông báo chuyển sang trạng thái đang giao
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
                    addr.getStreet(), addr.getWard(), addr.getCity())
                    .filter(s -> s != null && !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(", "));

            BigDecimal codAmount = "COD".equalsIgnoreCase(order.getPaymentMethod()) 
                    ? order.getTotalAmount() 
                    : BigDecimal.ZERO;

            DeliveryCommand deliveryCommand = DeliveryCommand.builder()
                    .orderId(orderId)
                    .receiverName(addr.getReceiverName())
                    .receiverPhone(addr.getReceiverPhone())
                    .address(fullAddress)
                    .codAmount(codAmount)
                    .build();

            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.DELIVERY_COMMAND_CREATE, deliveryCommand);
        } catch (Exception e) {
            log.error("[ORDER-SERVICE] Lỗi khi gửi lệnh tạo vận đơn cho đơn hàng {}: {}", orderId, e.getMessage());
        }
    }

    @Transactional
    public void cancelOrder(String orderId, String userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }

        // Logic hủy đơn hàng sẽ do Orchestrator điều phối nếu cần hoàn trả các bước khác.
        // Ở đây chỉ hỗ trợ hủy nhanh nếu đơn đang chờ xác thực hoặc chờ thanh toán.
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.PENDING_VALIDATION) {
            throw new AppException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
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

        String displayMsg = "Chi tiết đơn hàng #" + order.getId();
        if (order.getStatus() == OrderStatus.CANCELLED && order.getErrorMessage() != null) {
            displayMsg = order.getErrorMessage();
        }

        return OrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus() != null ? order.getStatus().name() : "UNKNOWN")
                .message(displayMsg)
                .totalAmount(order.getTotalAmount())
                .paymentId(order.getPaymentId())
                .deliveryId(order.getDeliveryId())
                .paymentMethod(order.getPaymentMethod())
                .paymentUrl(order.getPaymentUrl())
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

    private void enrichProductImages(List<OrderItemResponse> items) {
        if (items == null || items.isEmpty()) return;

        // Build map productId -> thumbnail URL from Redis (CQRS)
        Map<String, String> imageMap = new HashMap<>();
        for (OrderItemResponse item : items) {
            try {
                String imageKey = RedisConstants.PREFIX_PRODUCT_IMAGE + item.getProductId();
                String imageUrl = stringRedisTemplate.opsForValue().get(imageKey);
                
                if (imageUrl != null) {
                    imageMap.put(item.getProductId(), imageUrl);
                }
            } catch (Exception e) {
                log.warn("Không thể lấy hình ảnh cho sản phẩm {} từ Redis: {}", item.getProductId(), e.getMessage());
            }
        }

        // Gán thumbnail vào từng item (null nếu không có)
        items.forEach(item -> item.setImageUrl(imageMap.get(item.getProductId())));
    }
}
