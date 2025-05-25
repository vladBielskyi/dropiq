package com.dropiq.engine.product.entity;

import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.product.model.DataSetStatus;
import com.dropiq.engine.product.model.DataSetType;
import com.dropiq.engine.product.model.ProductStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "dataset")
@Data
@ToString(exclude = {"products"})
@EqualsAndHashCode(exclude = {"products"})
public class DataSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DataSetStatus status = DataSetStatus.DRAFT;

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

    // Many-to-many relationship with products
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    @JoinTable(
            name = "dataset_products",
            joinColumns = @JoinColumn(name = "dataset_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> products = new HashSet<>();

    // Track which data sources were used to create this dataset
    @ElementCollection
    @CollectionTable(name = "dataset_sources", joinColumns = @JoinColumn(name = "dataset_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private Set<SourceType> sourcePlatforms = new HashSet<>();

    // Metadata about the dataset
    @ElementCollection
    @CollectionTable(name = "dataset_metadata", joinColumns = @JoinColumn(name = "dataset_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata = new HashMap<>();

    // Business logic fields
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

    private void updateStatistics() {
        totalProducts = products.size();
        activeProducts = (int) products.stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
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
