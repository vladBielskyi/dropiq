package com.dropiq.engine.product.entity;

import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.product.model.ProductStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"external_id", "source_type"}))
@Data
@EqualsAndHashCode(exclude = {"datasets", "category"})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "group_id") // KEY FIELD FOR VARIANTS
    private String groupId;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "original_description", length = 4000)
    private String originalDescription; // ORIGINAL from source

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

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType sourceType;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_sync")
    private LocalDateTime lastSync;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status = ProductStatus.DRAFT;

    // ==============================================
    // AI ANALYSIS FIELDS - SHARED BY GROUP + SOURCE
    // ==============================================
    @Column(name = "ai_analyzed")
    private Boolean aiAnalyzed = false;

    @Column(name = "ai_analysis_date")
    private LocalDateTime aiAnalysisDate;

    @Column(name = "ai_confidence_score")
    private Double aiConfidenceScore = 0.0;

    // MULTILINGUAL SEO CONTENT - SHARED
    @Column(name = "seo_title_uk", length = 200)
    private String seoTitleUk;

    @Column(name = "seo_title_ru", length = 200)
    private String seoTitleRu;

    @Column(name = "seo_title_en", length = 200)
    private String seoTitleEn;

    @Column(name = "description_uk", length = 4000)
    private String descriptionUk;

    @Column(name = "description_ru", length = 4000)
    private String descriptionRu;

    @Column(name = "description_en", length = 4000)
    private String descriptionEn;

    @Column(name = "meta_description_uk", length = 500)
    private String metaDescriptionUk;

    @Column(name = "meta_description_ru", length = 500)
    private String metaDescriptionRu;

    @Column(name = "meta_description_en", length = 500)
    private String metaDescriptionEn;

    @ElementCollection
    @CollectionTable(name = "product_tags_uk", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private Set<String> tagsUk = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_tags_ru", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private Set<String> tagsRu = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_tags_en", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private Set<String> tagsEn = new HashSet<>();

    // CATEGORY RELATIONSHIP - SHARED BY GROUP
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DatasetCategory category;

    // AI GENERATED ATTRIBUTES - SHARED
    @Column(name = "predicted_price_range", length = 50)
    private String predictedPriceRange;

    @Column(name = "target_audience_uk", length = 500)
    private String targetAudienceUk;

    @Column(name = "target_audience_ru", length = 500)
    private String targetAudienceRu;

    @Column(name = "target_audience_en", length = 500)
    private String targetAudienceEn;

    @Column(name = "style_tags", length = 500)
    private String styleTags;

    @Column(name = "color_analysis", length = 500)
    private String colorAnalysis;

    @Column(name = "main_features", length = 1000)
    private String mainFeatures;

    @Column(name = "trend_score", precision = 5, scale = 2)
    private BigDecimal trendScore;

    @Column(name = "competition_level")
    private Integer competitionLevel;

    @Column(name = "profit_margin", precision = 5, scale = 2)
    private BigDecimal profitMargin;

    @ManyToMany(mappedBy = "products")
    private Set<DataSet> datasets = new HashSet<>();

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

    /**
     * Get unique key for AI analysis sharing
     */
    public String getAiAnalysisKey() {
        if (groupId != null && !groupId.trim().isEmpty()) {
            return sourceType.name() + ":" + groupId;
        }
        return sourceType.name() + ":" + externalId;
    }
}
