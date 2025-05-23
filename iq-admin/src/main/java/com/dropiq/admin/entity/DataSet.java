package com.dropiq.admin.entity;

import com.dropiq.admin.model.DatasetStatus;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Setter
@Getter
@JmixEntity
@Entity
@Table(name = "DATASET")
public class DataSet {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private DatasetStatus status = DatasetStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DATA_SOURCE_ID")
    private DataSource dataSource;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "LAST_SYNC")
    private LocalDateTime lastSync;

    @Column(name = "AUTO_SYNC")
    private Boolean autoSync = false;

    @Column(name = "SYNC_INTERVAL_HOURS")
    private Integer syncIntervalHours = 24;

    // Business logic fields
    @Column(name = "DEFAULT_MARKUP", precision = 5, scale = 2)
    private BigDecimal defaultMarkup = BigDecimal.valueOf(30);

    @Column(name = "MIN_PROFIT_MARGIN", precision = 5, scale = 2)
    private BigDecimal minProfitMargin = BigDecimal.valueOf(20);

    @Column(name = "AUTO_PRICE_UPDATE")
    private Boolean autoPriceUpdate = false;

    @Column(name = "AUTO_STOCK_UPDATE")
    private Boolean autoStockUpdate = true;

    // AI Features
    @Column(name = "AI_OPTIMIZATION_ENABLED")
    private Boolean aiOptimizationEnabled = false;

    @Column(name = "SEO_OPTIMIZATION_ENABLED")
    private Boolean seoOptimizationEnabled = false;

    @Column(name = "TREND_ANALYSIS_ENABLED")
    private Boolean trendAnalysisEnabled = false;

    @Column(name = "AUTO_CATEGORIZATION")
    private Boolean autoCategorization = false;

    @Column(name = "IMAGE_ANALYSIS_ENABLED")
    private Boolean imageAnalysisEnabled = false;

    // Statistics
    @Column(name = "TOTAL_PRODUCTS")
    private Integer totalProducts = 0;

    @Column(name = "ACTIVE_PRODUCTS")
    private Integer activeProducts = 0;

    @Column(name = "OPTIMIZED_PRODUCTS")
    private Integer optimizedProducts = 0;

    @Column(name = "EXPORTED_PRODUCTS")
    private Integer exportedProducts = 0;

    @Column(name = "SYNC_COUNT")
    private Integer syncCount = 0;

    @Column(name = "ERROR_COUNT")
    private Integer errorCount = 0;

    @Column(name = "LAST_ERROR_MESSAGE", length = 2000)
    private String lastErrorMessage;

    // Metadata
    @ElementCollection
    @CollectionTable(name = "DATASET_METADATA", joinColumns = @JoinColumn(name = "DATASET_ID"))
    @MapKeyColumn(name = "META_KEY")
    @Column(name = "META_VALUE")
    private Map<String, String> metadata = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "DATASET_TAGS", joinColumns = @JoinColumn(name = "DATASET_ID"))
    @Column(name = "TAG")
    private Set<String> tags = new HashSet<>();

    // One-to-many relationship with products
    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @InstanceName
    public String getName() {
        return name;
    }

}