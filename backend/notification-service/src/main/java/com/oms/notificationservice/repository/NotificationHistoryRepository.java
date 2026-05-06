package com.oms.notificationservice.repository;

import com.oms.notificationservice.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository quản lý dữ liệu lịch sử thông báo
 */
@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, String> {
    List<NotificationHistory> findByRecipientOrderByCreatedAtDesc(String recipient);
}
