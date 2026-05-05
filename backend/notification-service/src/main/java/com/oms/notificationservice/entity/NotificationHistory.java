package com.oms.notificationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Thực thể lưu trữ lịch sử các thông báo đã gửi
 */
@Entity
@Table(name = "notification_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "recipient", nullable = false)
    private String recipient; // Người nhận (email)

    @Column(name = "subject", nullable = false)
    private String subject; // Tiêu đề thông báo

    @Column(name = "body", columnDefinition = "TEXT")
    private String body; // Nội dung thông báo

    @Column(name = "status")
    private String status; // SENT, FAILED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
