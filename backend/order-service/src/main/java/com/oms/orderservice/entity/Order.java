package com.oms.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.oms.common.enums.OrderStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
@Builder
public class Order {

    @Id
    private String id;
    private String userId;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String paymentId;
    private String deliveryId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Embedded
    private OrderAddress shippingAddress;

    @OneToMany(mappedBy = "order",cascade = CascadeType.ALL,  orphanRemoval = true)
    private List<OrderItem> orderItems;


}
