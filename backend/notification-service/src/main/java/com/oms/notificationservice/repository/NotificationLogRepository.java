package com.oms.notificationservice.repository;

import com.oms.notificationservice.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {
    Page<NotificationLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
