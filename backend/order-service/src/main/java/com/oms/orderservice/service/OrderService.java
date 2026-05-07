package com.oms.orderservice.service;

import com.oms.common.ApiResponse;
import com.oms.common.AppException;
import com.oms.common.CommonErrorCode;
import com.oms.common.constant.RabbitMQConstants;
import com.oms.common.enums.OrderStatus;
import com.oms.orderservice.client.InventoryClient;
import com.oms.orderservice.client.ProductClient;
import com.oms.orderservice.dto.AddressRequest;
import com.oms.orderservice.dto.InventoryCommand;
import com.oms.orderservice.dto.InventoryReserveRequest;
import com.oms.orderservice.dto.OrderItemRequest;
import com.oms.orderservice.dto.OrderRequest;
import com.oms.orderservice.dto.OrderResponse;
import com.oms.orderservice.dto.PaymentCommand;
import com.oms.orderservice.dto.ProductResponse;
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
import java.util.List;
import java.util.UUID;


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
                .map(order -> OrderResponse.builder()
                        .orderId(order.getId())
                        .userId(order.getUserId())
                        .status(order.getStatus().name())
                        .message("Đơn hàng tạo lúc: " + order.getCreatedAt())
                        .build());
    }

    @Transactional
    public String createOrder(OrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        log.info("Bắt đầu tạo đơn hàng {}. Thông tin người nhận từ request: Name={}, Phone={}", 
                orderId, request.getAddress().getReceiverName(), request.getAddress().getReceiverPhone());

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        List<InventoryReserveRequest> reserveRequests = new ArrayList<>();

        for (OrderItemRequest itemReq : request.getOrderItems()) {
            // Lấy thông tin giá chuẩn từ Product Service, bảo mật giá tuyệt đối
            ApiResponse<ProductResponse> productApiRes = productClient.getProductById(itemReq.getProductId());
            if (productApiRes == null || !productApiRes.isSuccess() || productApiRes.getResult() == null) {
                throw new AppException(OrderErrorCode.ORDER_CREATION_FAILED); // Hoặc PRODUCT_NOT_FOUND
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
        shippingAddress.setStreet(addrReq.getStreet());
        shippingAddress.setWard(addrReq.getWard());
        shippingAddress.setDistrict(addrReq.getDistrict());
        shippingAddress.setCity(addrReq.getCity());
        shippingAddress.setReceiverName(addrReq.getReceiverName());
        shippingAddress.setReceiverPhone(addrReq.getReceiverPhone());

        Order order = Order.builder()
                .id(orderId)
                .userId(request.getUserId())
                .status(OrderStatus.PAYMENT_PENDING)
                .totalAmount(totalPrice)
                .shippingAddress(shippingAddress)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setOrderItems(orderItems);

        boolean inventoryReserved = false;
        try {
            // Gọi bulk api duy nhất 1 lần để giữ kho
            reserveInventory(reserveRequests);
            inventoryReserved = true;

            orderRepository.save(order);

            // Bắn event payment
            PaymentCommand command = PaymentCommand.builder()
                    .orderId(orderId)
                    .userId(request.getUserId())
                    .amount(totalPrice)
                    .description("Thanh toán đơn hàng: " + orderId)
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.PAYMENT_COMMAND_CREATE, command);

            log.info("Order created with status PAYMENT_PENDING. Command sent to Payment Service for OrderId: {}", orderId);

            return orderId;

        } catch (Exception ex) {
            log.error("Lỗi tạo đơn hàng {}: {}", orderId, ex.getMessage());

            if (inventoryReserved) {
                // Rollback lại kho cho từng item
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
            log.error("Không thể duyệt đơn hàng {}. Trạng thái hiện tại: {}", orderId, order.getStatus());
            throw new AppException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        // 1. Cập nhật trạng thái DB
        order.setStatus(OrderStatus.SHIPPING);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Đã cập nhật đơn hàng {} sang trạng thái SHIPPING", orderId);

        // 2. Gửi lệnh tạo vận đơn sang Delivery Service
        try {
            OrderAddress addr = order.getShippingAddress();
            
            // Xử lý ghép địa chỉ an toàn (bỏ qua null/empty)
            String fullAddress = java.util.stream.Stream.of(
                    addr.getStreet(), addr.getWard(), addr.getDistrict(), addr.getCity())
                    .filter(s -> s != null && !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(", "));
            log.info("[SAGA] Chuẩn bị gửi lệnh tạo vận đơn. Người nhận: {}, SĐT: {}, Địa chỉ: {}", 
                    addr.getReceiverName(), addr.getReceiverPhone(), fullAddress);

            com.oms.orderservice.dto.DeliveryRequest deliveryRequest = com.oms.orderservice.dto.DeliveryRequest.builder()
                    .orderId(orderId)
                    .receiverName(addr.getReceiverName())
                    .receiverPhone(addr.getReceiverPhone())
                    .address(fullAddress)
                    .build();

            log.info("[SAGA] Đang gửi lệnh tạo vận đơn cho Order: {}", orderId);
            rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_NAME, RabbitMQConstants.DELIVERY_COMMAND_CREATE, deliveryRequest);
            log.info("[SAGA] Đã gửi tin nhắn thành công cho Order: {}", orderId);

        } catch (Exception e) {
            log.error("[SAGA] Lỗi khi gửi lệnh tạo vận đơn cho Order {}: {}", orderId, e.getMessage(), e);
            // Có thể cân nhắc bắn bù hoặc xử lý retry ở đây nếu cần
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

        return OrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .message("Thông tin đơn hàng")
                .build();
    }
}
