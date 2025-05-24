package com.dropiq.admin.entity;

import com.dropiq.admin.model.DataSetStatus;
import com.dropiq.admin.model.ProductStatus;
import com.dropiq.admin.model.SourceType;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@JmixEntity
@Entity
@Table(name = "dataset")
@Getter
@Setter
public class DataSet {

    @Column(nullable = false)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @InstanceName
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_sync")
    private LocalDateTime lastSync;

    @Column(name = "auto_sync")
    private Boolean autoSync = false;

    @Column(name = "sync_interval_hours")
    private Integer syncIntervalHours = 24;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "dataset_products",
            joinColumns = @JoinColumn(name = "dataset_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "product_id", referencedColumnName = "id")
    )
    private Set<Product> products = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "dataset_sources", joinColumns = @JoinColumn(name = "dataset_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private Set<SourceType> sourcePlatforms = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "dataset_metadata", joinColumns = @JoinColumn(name = "dataset_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata = new HashMap<>();

    @Column(name = "default_markup", precision = 5, scale = 2)
    private BigDecimal defaultMarkup = BigDecimal.valueOf(30);

    @Column(name = "min_profit_margin", precision = 5, scale = 2)
    private BigDecimal minProfitMargin = BigDecimal.valueOf(20);

    @Column(name = "auto_price_update")
    private Boolean autoPriceUpdate = false;

    @Column(name = "auto_stock_update")
    private Boolean autoStockUpdate = true;

    @Column(name = "ai_optimization_enabled")
    private Boolean aiOptimizationEnabled = false;

    @Column(name = "seo_optimization_enabled")
    private Boolean seoOptimizationEnabled = false;

    @Column(name = "trend_analysis_enabled")
    private Boolean trendAnalysisEnabled = false;

    @Column(name = "total_products")
    private Integer totalProducts = 0;

    @Column(name = "active_products")
    private Integer activeProducts = 0;

    @Column(name = "sync_count")
    private Integer syncCount = 0;

    @Column(name = "error_count")
    private Integer errorCount = 0;

    @Column(name = "last_error_message", length = 2000)
    private String lastErrorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        updateStatistics();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updateStatistics();
    }

    public DataSetStatus getStatus() {
        return status == null ? null : DataSetStatus.valueOf(status);
    }

    public void setStatus(DataSetStatus status) {
        this.status = status.name();
    }

    private void updateStatistics() {
        totalProducts = products.size();
        activeProducts = (int) products.stream()
                .filter(p -> Objects.equals(p.getStatus(), ProductStatus.ACTIVE.name()))
                .count();
    }

    public void addProduct(Product product) {
        products.add(product);
        product.getDatasets().add(this);
        sourcePlatforms.add(product.getSourceType());
        updateStatistics();
    }

    public void removeProduct(Product product) {
        products.remove(product);
        product.getDatasets().remove(this);
        updateStatistics();
    }

    public void addProducts(List<Product> productList) {
        for (Product product : productList) {
            addProduct(product);
        }
    }

    public int getProductCount() {
        return products.size();
    }

    public boolean containsProduct(String externalId, SourceType sourceType) {
        return products.stream()
                .anyMatch(p -> p.getExternalId().equals(externalId) &&
                        p.getSourceType().equals(sourceType));
    }
}
