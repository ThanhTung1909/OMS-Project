package com.oms.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity dùng để theo dõi các event đã xử lý
 * Giúp tránh xử lý lại event do RabbitMQ gửi lại (Idempotency)
 */
@Entity
@Table(name = "processed_events", indexes = {
        @Index(name = "idx_order_id", columnList = "order_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public enum EventType {
        CONFIRM,
        ROLLBACK
    }

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
