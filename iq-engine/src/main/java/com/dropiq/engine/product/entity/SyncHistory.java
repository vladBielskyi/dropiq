package com.dropiq.engine.product.entity;

import com.dropiq.engine.product.model.SyncJobStatus;
import com.dropiq.engine.product.support.MapToJsonConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "sync_history")
public class SyncHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_job_id")
    private SyncJob syncJob;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sync_type", nullable = false)
    private String syncType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SyncJobStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "products_added")
    private Integer productsAdded = 0;

    @Column(name = "products_updated")
    private Integer productsUpdated = 0;

    @Column(name = "products_removed")
    private Integer productsRemoved = 0;

    @Column(name = "errors_encountered")
    private Integer errorsEncountered = 0;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = MapToJsonConverter.class)
    private Map<String, Object> metadata = new HashMap<>();
}
