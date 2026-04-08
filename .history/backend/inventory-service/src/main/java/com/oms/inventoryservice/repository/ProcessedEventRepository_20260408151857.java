package com.oms.inventoryservice.repository;

import com.oms.inventoryservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Tìm event đã xử lý theo orderId
     * Dùng để check idempotency
     */
    Optional<ProcessedEvent> findByOrderId(String orderId);

    /**
     * Check xem một orderId có được xử lý rồi không
     */
    boolean existsByOrderId(String orderId);
}
