package com.dropiq.admin.entity;

import com.dropiq.admin.model.ProductOptimizationStatus;
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

@Getter
@Setter
@JmixEntity
@Entity
@Table(name = "PRODUCT")
public class Product {

    @Id
    @Column(name = "ID")
    @JmixGeneratedValue
    private Long id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @NotNull
    @Column(name = "EXTERNAL_ID", nullable = false)
    private String externalId;

    @Column(name = "GROUP_ID")
    private String groupId;

    @NotNull
    @Column(name = "NAME", nullable = false, length = 500)
    private String name;

    @Column(name = "DESCRIPTION", length = 4000)
    private String description;

    @Column(name = "SHORT_DESCRIPTION", length = 1000)
    private String shortDescription;

    // Many-to-one relationship with Dataset
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DATASET_ID")
    private DataSet dataset;

    // Pricing
    @Column(name = "ORIGINAL_PRICE", precision = 19, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "SELLING_PRICE", precision = 19, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "MARKUP_PERCENTAGE", precision = 5, scale = 2)
    private BigDecimal markupPercentage;

    @Column(name = "PROFIT_MARGIN", precision = 5, scale = 2)
    private BigDecimal profitMargin;

    // Inventory
    @Column(name = "STOCK")
    private Integer stock = 0;

    @Column(name = "AVAILABLE")
    private Boolean available = false;

    @Column(name = "MIN_STOCK_LEVEL")
    private Integer minStockLevel = 0;

    // Category
    @Column(name = "EXTERNAL_CATEGORY_ID")
    private String externalCategoryId;

    @Column(name = "EXTERNAL_CATEGORY_NAME")
    private String externalCategoryName;

    @Column(name = "INTERNAL_CATEGORY")
    private String internalCategory;

    // Images
    @ElementCollection
    @CollectionTable(name = "PRODUCT_IMAGES", joinColumns = @JoinColumn(name = "PRODUCT_ID"))
    @Column(name = "IMAGE_URL")
    @OrderColumn(name = "IMAGE_ORDER")
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "PRIMARY_IMAGE_URL", length = 500)
    private String primaryImageUrl;

    // Attributes
    @ElementCollection
    @CollectionTable(name = "PRODUCT_ATTRIBUTES", joinColumns = @JoinColumn(name = "PRODUCT_ID"))
    @MapKeyColumn(name = "ATTR_KEY")
    @Column(name = "ATTR_VALUE")
    private Map<String, String> attributes = new HashMap<>();

    // Source information
    @Column(name = "SOURCE_TYPE")
    private String sourceType;

    @Column(name = "SOURCE_URL", length = 500)
    private String sourceUrl;

    @Column(name = "SOURCE_PLATFORM")
    private String sourcePlatform;

    // AI Optimization
    @Enumerated(EnumType.STRING)
    @Column(name = "OPTIMIZATION_STATUS")
    private ProductOptimizationStatus optimizationStatus = ProductOptimizationStatus.PENDING;

    @Column(name = "AI_OPTIMIZED")
    private Boolean aiOptimized = false;

    @Column(name = "AI_OPTIMIZED_NAME", length = 500)
    private String aiOptimizedName;

    @Column(name = "AI_OPTIMIZED_DESCRIPTION", length = 4000)
    private String aiOptimizedDescription;

    @Column(name = "AI_DETECTED_ATTRIBUTES", length = 2000)
    private String aiDetectedAttributes;

    // SEO
    @Column(name = "SEO_TITLE", length = 200)
    private String seoTitle;

    @Column(name = "SEO_DESCRIPTION", length = 500)
    private String seoDescription;

    @Column(name = "SEO_KEYWORDS", length = 500)
    private String seoKeywords;

    @Column(name = "SEO_SCORE", precision = 3, scale = 1)
    private BigDecimal seoScore;

    // Analytics
    @Column(name = "TREND_SCORE", precision = 5, scale = 2)
    private BigDecimal trendScore;

    @Column(name = "COMPETITION_LEVEL")
    private Integer competitionLevel;

    @Column(name = "POPULARITY_SCORE", precision = 5, scale = 2)
    private BigDecimal popularityScore;

    @Column(name = "VIEW_COUNT")
    private Long viewCount = 0L;

    @Column(name = "CLICK_COUNT")
    private Long clickCount = 0L;

    // Status and timestamps
    @Column(name = "ACTIVE")
    private Boolean active = true;

    @Column(name = "FEATURED")
    private Boolean featured = false;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "LAST_SYNC")
    private LocalDateTime lastSync;

    @Column(name = "LAST_OPTIMIZATION")
    private LocalDateTime lastOptimization;

    // Export tracking
    @ElementCollection
    @CollectionTable(name = "PRODUCT_EXPORTS", joinColumns = @JoinColumn(name = "PRODUCT_ID"))
    @MapKeyColumn(name = "PLATFORM")
    @Column(name = "EXPORT_DATE")
    private Map<String, LocalDateTime> exportHistory = new HashMap<>();

    // Tags
    @ElementCollection
    @CollectionTable(name = "PRODUCT_TAGS", joinColumns = @JoinColumn(name = "PRODUCT_ID"))
    @Column(name = "TAG")
    private Set<String> tags = new HashSet<>();

    // Platform specific data
    @ElementCollection
    @CollectionTable(name = "PRODUCT_PLATFORM_DATA", joinColumns = @JoinColumn(name = "PRODUCT_ID"))
    @MapKeyColumn(name = "DATA_KEY")
    @Column(name = "DATA_VALUE")
    private Map<String, String> platformSpecificData = new HashMap<>();

    // Notes and comments
    @Column(name = "NOTES", length = 2000)
    private String notes;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "UPDATED_BY")
    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        calculateSellingPrice();
    }

    private void calculateSellingPrice() {
        if (originalPrice != null && markupPercentage != null) {
            BigDecimal markup = originalPrice.multiply(markupPercentage).divide(BigDecimal.valueOf(100), 4,
                    java.math.RoundingMode.HALF_UP);
            sellingPrice = originalPrice.add(markup);

            if (originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                profitMargin = markup.divide(originalPrice, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
        }
    }

    @InstanceName
    public String getName() {
        return name;
    }
}