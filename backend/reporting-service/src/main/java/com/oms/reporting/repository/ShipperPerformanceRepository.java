package com.oms.reporting.repository;

import com.oms.reporting.entity.ShipperPerformanceStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipperPerformanceRepository extends JpaRepository<ShipperPerformanceStatistics, String> {
    Optional<ShipperPerformanceStatistics> findByShipperPhone(String shipperPhone);
}
