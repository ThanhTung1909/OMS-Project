package com.oms.orchestrator.repository;

import com.oms.orchestrator.entity.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    Optional<SagaInstance> findByOrderId(String orderId);
}
