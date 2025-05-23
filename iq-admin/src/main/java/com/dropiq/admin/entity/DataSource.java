package com.dropiq.admin.entity;

import com.dropiq.admin.model.DataSourceStatus;
import com.dropiq.admin.model.DataSourceType;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JmixEntity
@Entity
@Table(name = "DATA_SOURCE")
public class DataSource {

    @Id
    @Column(name = "ID")
    @JmixGeneratedValue
    private UUID id;

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

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @InstanceName
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DataSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(DataSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, String> configuration) {
        this.configuration = configuration;
    }

    public DataSourceStatus getStatus() {
        return status;
    }

    public void setStatus(DataSourceStatus status) {
        this.status = status;
    }

    public Boolean getAutoSync() {
        return autoSync;
    }

    public void setAutoSync(Boolean autoSync) {
        this.autoSync = autoSync;
    }

    public Integer getSyncIntervalHours() {
        return syncIntervalHours;
    }

    public void setSyncIntervalHours(Integer syncIntervalHours) {
        this.syncIntervalHours = syncIntervalHours;
    }

    public LocalDateTime getLastSync() {
        return lastSync;
    }

    public void setLastSync(LocalDateTime lastSync) {
        this.lastSync = lastSync;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Integer totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Integer getActiveProducts() {
        return activeProducts;
    }

    public void setActiveProducts(Integer activeProducts) {
        this.activeProducts = activeProducts;
    }

    public Integer getSyncCount() {
        return syncCount;
    }

    public void setSyncCount(Integer syncCount) {
        this.syncCount = syncCount;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
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
