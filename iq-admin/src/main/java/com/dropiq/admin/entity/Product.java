package com.dropiq.admin.entity;

import com.dropiq.admin.model.ProductStatus;
import com.dropiq.admin.model.SourceType;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@JmixEntity
@Entity
@Table(name = "product")
@Getter
@Setter
public class Product {

    @Column(nullable = false)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "group_id")
    private String groupId;

    @InstanceName
    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "short_description", length = 1000)
    private String shortDescription;

    @Column(name = "original_price", precision = 19, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "selling_price", precision = 19, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "markup_percentage", precision = 5, scale = 2)
    private BigDecimal markupPercentage;

    @Column(name = "stock")
    private Integer stock = 0;

    @Column(name = "available")
    private Boolean available = false;

    @Column(name = "external_category_id")
    private String externalCategoryId;

    @Column(name = "external_category_name")
    private String externalCategoryName;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    @OrderColumn(name = "image_order")
    private List<String> imageUrls = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attr_key")
    @Column(name = "attr_value")
    private Map<String, String> attributes = new HashMap<>();

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_sync")
    private LocalDateTime lastSync;

    @Column(name = "status")
    private String status;

    @Column(name = "ai_optimized")
    private Boolean aiOptimized = false;

    @Column(name = "seo_title", length = 200)
    private String seoTitle;

    @Column(name = "seo_description", length = 500)
    private String seoDescription;

    @Column(name = "seo_keywords", length = 500)
    private String seoKeywords;

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @ManyToMany(mappedBy = "products")
    private Set<DataSet> datasets = new HashSet<>();

    @Column(name = "trend_score", precision = 5, scale = 2)
    private BigDecimal trendScore;

    @Column(name = "competition_level")
    private Integer competitionLevel;

    @Column(name = "profit_margin", precision = 5, scale = 2)
    private BigDecimal profitMargin;

    @ElementCollection
    @CollectionTable(name = "product_platform_data", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    private Map<String, String> platformSpecificData = new HashMap<>();

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

    public SourceType getSourceType() {
        return sourceType == null ? null : SourceType.valueOf(sourceType);
    }

    public ProductStatus getStatus() {
        return status == null ? null : ProductStatus.valueOf(status);
    }

    public void setStatus(ProductStatus status) {
        this.status = status.name();
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType.name();
    }

    private void calculateSellingPrice() {
        if (originalPrice != null && markupPercentage != null) {
            BigDecimal markup = originalPrice.multiply(markupPercentage)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            sellingPrice = originalPrice.add(markup);

            if (originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                profitMargin = markup
                        .divide(originalPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
        }
    }
}
