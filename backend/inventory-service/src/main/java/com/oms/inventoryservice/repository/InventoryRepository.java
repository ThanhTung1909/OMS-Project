package com.oms.inventoryservice.repository;

import com.oms.inventoryservice.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {
    Optional<Inventory> findByProductId(String productId);

    /** Truy vấn nhiều sản phẩm trong 1 câu SQL IN */
    List<Inventory> findByProductIdIn(List<String> productIds);
}
