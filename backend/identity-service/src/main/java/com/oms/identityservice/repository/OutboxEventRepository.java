package com.oms.identityservice.repository;

import com.oms.identityservice.entity.OutboxEvent;
import com.oms.identityservice.entity.Enum.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatus(OutboxStatus status);
}
