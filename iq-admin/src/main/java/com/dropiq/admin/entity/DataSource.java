package com.dropiq.admin.entity;

import com.dropiq.admin.model.DataSourceStatus;
import com.dropiq.admin.model.DataSourceType;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@JmixEntity
@Entity
@Table(name = "DATA_SOURCE")
public class DataSource {

    @Id
    @Column(name = "ID")
    @JmixGeneratedValue
    private Long id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "SOURCE_TYPE", nullable = false)
    private DataSourceType sourceType;

    @Column(name = "URL", length = 2000)
    private String url;

    @ElementCollection
    @CollectionTable(name = "DATA_SOURCE_HEADERS", joinColumns = @JoinColumn(name = "DATA_SOURCE_ID"))
    @MapKeyColumn(name = "HEADER_KEY")
    @Column(name = "HEADER_VALUE")
    private Map<String, String> headers = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "DATA_SOURCE_CONFIG", joinColumns = @JoinColumn(name = "DATA_SOURCE_ID"))
    @MapKeyColumn(name = "CONFIG_KEY")
    @Column(name = "CONFIG_VALUE")
    private Map<String, String> configuration = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private DataSourceStatus status = DataSourceStatus.DRAFT;

    @Column(name = "AUTO_SYNC")
    private Boolean autoSync = false;

    @Column(name = "SYNC_INTERVAL_HOURS")
    private Integer syncIntervalHours = 24;

    @Column(name = "LAST_SYNC")
    private LocalDateTime lastSync;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "TOTAL_PRODUCTS")
    private Integer totalProducts = 0;

    @Column(name = "ACTIVE_PRODUCTS")
    private Integer activeProducts = 0;

    @Column(name = "SYNC_COUNT")
    private Integer syncCount = 0;

    @Column(name = "ERROR_COUNT")
    private Integer errorCount = 0;

    @Column(name = "LAST_ERROR_MESSAGE", length = 2000)
    private String lastErrorMessage;

    @InstanceName
    public String getName() {
        return name;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
