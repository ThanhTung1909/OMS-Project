package com.oms.inventoryservice.repository;

import com.oms.inventoryservice.entity.InventoryAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryAuditLogRepository extends JpaRepository<InventoryAuditLog, String> {
    List<InventoryAuditLog> findByProductIdOrderByCreatedAtDesc(String productId);
}
