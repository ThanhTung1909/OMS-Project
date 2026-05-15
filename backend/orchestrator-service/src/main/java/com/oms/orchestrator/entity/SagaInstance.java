package com.oms.orchestrator.entity;

import com.oms.common.enums.SagaStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    private SagaStatus currentStep;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
