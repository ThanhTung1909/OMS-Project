package com.oms.deliveryservice.repository;

import com.oms.deliveryservice.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, String> {
    Optional<Delivery> findByOrderId(String orderId);
    boolean existsByOrderId(String orderId);
    java.util.List<Delivery> findByShipperId(String shipperId);
    java.util.List<Delivery> findByShipperIdAndStatus(String shipperId, com.oms.common.enums.DeliveryStatus status);
}
