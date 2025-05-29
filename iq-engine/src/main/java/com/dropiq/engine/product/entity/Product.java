package com.dropiq.engine.product.entity;

import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.product.model.ProductStatus;
import com.dropiq.engine.user.service.PriceUtil;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "product")
@Data
@EqualsAndHashCode(exclude = {"datasets", "category"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "external_group_id", length = 100)
    private String externalGroupId;

    @Column(name = "external_name", nullable = false, length = 500)
    private String externalName;

    @Column(name = "external_description", length = 4000)
    private String externalDescription;

    @Column(name = "external_category_id", length = 100)
    private String externalCategoryId;

    @Column(name = "external_category_name", length = 200)
    private String externalCategoryName;

    @Column(name = "brand_detected")
    private Boolean brandDetected = false;

    @Column(name = "detected_brand_name", length = 100)
    private String detectedBrandName;

    @Column(name = "is_replica")
    private Boolean isReplica = false;

    @Column(name = "replica_indicators", length = 1000)
    private String replicaIndicators;

    @Column(name = "original_price", precision = 19, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "selling_price", precision = 19, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "markup_percentage", precision = 5, scale = 2)
    private BigDecimal markupPercentage = BigDecimal.valueOf(20);

    @Column(name = "stock")
    private Integer stock = 0;

    @Column(name = "available")
    private Boolean available = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "seo_title_ua", length = 255)
    private String seoTitleUa;

    @Column(name = "seo_title_ru", length = 255)
    private String seoTitleRu;

    @Column(name = "seo_title_en", length = 255)
    private String seoTitleEn;

    @Column(name = "description_ua", length = 4000)
    private String descriptionUa;

    @Column(name = "description_ru", length = 4000)
    private String descriptionRu;

    @Column(name = "description_en", length = 4000)
    private String descriptionEn;

    @Column(name = "short_description_ua", length = 500)
    private String shortDescriptionUa;

    @Column(name = "short_description_ru", length = 500)
    private String shortDescriptionRu;

    @Column(name = "short_description_en", length = 500)
    private String shortDescriptionEn;

    @Column(name = "meta_description_ua", length = 300)
    private String metaDescriptionUa;

    @Column(name = "meta_description_ru", length = 300)
    private String metaDescriptionRu;

    @Column(name = "meta_description_en", length = 300)
    private String metaDescriptionEn;

    @Column(name = "original_size", length = 50)
    private String originalSize;

    @Column(name = "normalized_size", length = 20)
    private String normalizedSize;

    @Column(name = "size_type", length = 20)
    private String sizeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DatasetCategory category;

    @Column(name = "horoshop_article", length = 100)
    private String horoshopArticle;

    @Column(name = "horoshop_category_path", length = 500)
    private String horoshopCategoryPath;

    @Column(name = "horoshop_ready")
    private Boolean horoshopReady = false;

    @Column(name = "horoshop_exported")
    private Boolean horoshopExported = false;

    @Column(name = "horoshop_last_export")
    private LocalDateTime horoshopLastExport;

    @Column(name = "horoshop_status", length = 50)
    private String horoshopStatus;

    @Column(name = "horoshop_error_message", length = 1000)
    private String horoshopErrorMessage;

    @Column(name = "presence", length = 50)
    private String presence;

    @Column(name = "horoshop_characteristics")
    private String horoshopCharacteristics;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 500)
    @OrderColumn(name = "image_order")
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "main_image_url", length = 500)
    private String mainImageUrl;

    @Column(name = "images_quality_score", precision = 3, scale = 2)
    private BigDecimal imagesQualityScore = BigDecimal.ZERO;

    @Column(name = "ai_analysis_date")
    private LocalDateTime aiAnalysisDate;

    @Column(name = "ai_confidence_score", precision = 5, scale = 2)
    private BigDecimal aiConfidenceScore;

    @Column(name = "trend_score", precision = 5, scale = 2)
    private BigDecimal trendScore;

    @Column(name = "conversion_potential", precision = 5, scale = 2)
    private BigDecimal conversionPotential;

    @Column(name = "seasonality_score", precision = 5, scale = 2)
    private BigDecimal seasonalityScore;

    @ElementCollection
    @CollectionTable(name = "product_keywords_ua", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "keyword", length = 100)
    private Set<String> keywordsUa = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_keywords_ru", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "keyword", length = 100)
    private Set<String> keywordsRu = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_keywords_en", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "keyword", length = 100)
    private Set<String> keywordsEn = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag", length = 50)
    private Set<String> tags = new HashSet<>();

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "material", length = 100)
    private String material;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "season", length = 30)
    private String season;

    @Column(name = "style", length = 50)
    private String style;

    @Column(name = "occasion", length = 100)
    private String occasion;

    @ElementCollection
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attr_key", length = 100)
    @Column(name = "attr_value", length = 500)
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

    @ManyToMany(mappedBy = "products", fetch = FetchType.EAGER)
    private Set<DataSet> datasets = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        calculateSellingPrice();
        updateHoroshopReadyStatus();
        updateMainImageUrl();
        updatePresenceStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateSellingPrice();
        updateHoroshopReadyStatus();
        updateMainImageUrl();
        updatePresenceStatus();
    }

    // ==============================================
    // БІЗНЕС ЛОГІКА
    // ==============================================

    /**
     * Розрахунок продажної ціни з урахуванням націнки
     */
    public void calculateSellingPrice() {
        if (originalPrice != null && markupPercentage != null) {
            BigDecimal markup = originalPrice.multiply(markupPercentage)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal calculatedPrice = originalPrice.add(markup);

            sellingPrice = PriceUtil.roundToMarketingPriceUp(calculatedPrice);
        }
    }

    /**
     * Оновлення статусу готовності до експорту в Horoshop
     */
    public void updateHoroshopReadyStatus() {
        horoshopReady = isReadyForHoroshop();
    }

    /**
     * Перевірка готовності до експорту в Horoshop
     */
    public boolean isReadyForHoroshop() {
        return externalName != null && !externalName.trim().isEmpty() &&
                sellingPrice != null && sellingPrice.compareTo(BigDecimal.ZERO) > 0 &&
                (seoTitleUa != null || seoTitleRu != null) &&
                (descriptionUa != null || descriptionRu != null) &&
                status == ProductStatus.ACTIVE &&
                category != null;
    }

    /**
     * Перевірка чи товар є копією бренду
     */
    public boolean isBrandReplica() {
        return Boolean.TRUE.equals(isReplica);
    }

    /**
     * Отримання оптимізованого заголовку для мови
     */
    public String getOptimizedTitle(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> seoTitleUa != null ? seoTitleUa : externalName;
            case "ru" -> seoTitleRu != null ? seoTitleRu : externalName;
            case "en" -> seoTitleEn != null ? seoTitleEn : externalName;
            default -> seoTitleUa != null ? seoTitleUa :
                    (seoTitleRu != null ? seoTitleRu : externalName);
        };
    }

    /**
     * Отримання оптимізованого опису для мови
     */
    public String getOptimizedDescription(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> descriptionUa != null ? descriptionUa : externalDescription;
            case "ru" -> descriptionRu != null ? descriptionRu : externalDescription;
            case "en" -> descriptionEn != null ? descriptionEn : externalDescription;
            default -> descriptionUa != null ? descriptionUa :
                    (descriptionRu != null ? descriptionRu : externalDescription);
        };
    }

    /**
     * Отримання коroткого опису для мови
     */
    public String getShortDescription(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> shortDescriptionUa;
            case "ru" -> shortDescriptionRu;
            case "en" -> shortDescriptionEn;
            default -> shortDescriptionUa != null ? shortDescriptionUa : shortDescriptionRu;
        };
    }

    /**
     * Отримання meta опису для мови
     */
    public String getMetaDescription(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> metaDescriptionUa;
            case "ru" -> metaDescriptionRu;
            case "en" -> metaDescriptionEn;
            default -> metaDescriptionUa != null ? metaDescriptionUa : metaDescriptionRu;
        };
    }

    /**
     * Отримання ключових слів для мови
     */
    public Set<String> getKeywordsForLang(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> keywordsUa;
            case "ru" -> keywordsRu;
            case "en" -> keywordsEn;
            default -> keywordsUa;
        };
    }

    /**
     * Генерація Horoshop article якщо потрібно
     */
    public String getHoroshopArticleOrDefault() {
        return horoshopArticle != null ? horoshopArticle :
                (externalId.length() > 50 ? externalId.substring(0, 50) : externalId);
    }

    /**
     * Оновлення головного зображення
     */
    public void updateMainImageUrl() {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            mainImageUrl = imageUrls.get(0);
        }
    }

    /**
     * Оновлення статусу наявності для Horoshop
     */
    public void updatePresenceStatus() {
        if (!available) {
            presence = "Нет в наличии";
        } else if (stock != null && stock > 0) {
            presence = "В наличии";
        } else {
            presence = "Под заказ";
        }
    }

    /**
     * Перевірка чи товар має низький залишок
     */
    public boolean hasLowStock() {
        return stock != null && stock <= 5 && stock > 0;
    }

    /**
     * Перевірка чи товар новий
     */
    public boolean isNew() {
        return createdAt != null &&
                createdAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * Перевірка чи товар популярний
     */
    public boolean isPopular() {
        return trendScore != null &&
                trendScore.compareTo(BigDecimal.valueOf(7.0)) >= 0;
    }

    /**
     * Встановлення brand detection результатів
     */
    public void setBrandDetectionResults(String brandName, BigDecimal confidence,
                                         boolean isReplica, List<String> indicators) {
        this.detectedBrandName = brandName;
        this.brandDetected = brandName != null;
        this.isReplica = isReplica;

        if (indicators != null && !indicators.isEmpty()) {
            this.replicaIndicators = String.join(";", indicators);
        }
    }


    public void setNormalizedSizeInfo(String originalSize, String normalizedSize,
                                      String sizeType) {
        this.originalSize = originalSize;
        this.normalizedSize = normalizedSize;
        this.sizeType = sizeType;
    }

    /**
     * Встановлення AI контенту для всіх мов
     */
    public void setMultilingualContent(String titleUa, String titleRu, String titleEn,
                                       String descUa, String descRu, String descEn,
                                       String shortDescUa, String shortDescRu, String shortDescEn,
                                       String metaUa, String metaRu, String metaEn) {
        this.seoTitleUa = titleUa;
        this.seoTitleRu = titleRu;
        this.seoTitleEn = titleEn;

        this.descriptionUa = descUa;
        this.descriptionRu = descRu;
        this.descriptionEn = descEn;

        this.shortDescriptionUa = shortDescUa;
        this.shortDescriptionRu = shortDescRu;
        this.shortDescriptionEn = shortDescEn;

        this.metaDescriptionUa = metaUa;
        this.metaDescriptionRu = metaRu;
        this.metaDescriptionEn = metaEn;

        this.aiAnalysisDate = LocalDateTime.now();
    }

    /**
     * Встановлення ключових слів для всіх мов
     */
    public void setMultilingualKeywords(Set<String> keywordsUa, Set<String> keywordsRu, Set<String> keywordsEn) {
        this.keywordsUa = keywordsUa != null ? keywordsUa : new HashSet<>();
        this.keywordsRu = keywordsRu != null ? keywordsRu : new HashSet<>();
        this.keywordsEn = keywordsEn != null ? keywordsEn : new HashSet<>();
    }

    /**
     * Підготовка для експорту в Horoshop
     */
    public void prepareForHoroshopExport() {
        updateHoroshopReadyStatus();
        updatePresenceStatus();

        if (horoshopArticle == null) {
            horoshopArticle = getHoroshopArticleOrDefault();
        }

        if (category != null && horoshopCategoryPath == null) {
            horoshopCategoryPath = category.getFullPath();
        }
    }

    /**
     * Оновлення статусу експорту в Horoshop
     */
    public void updateHoroshopExportStatus(boolean success, String message) {
        horoshopExported = success;
        horoshopLastExport = LocalDateTime.now();
        horoshopStatus = success ? "SUCCESS" : "ERROR";
        horoshopErrorMessage = success ? null : message;
    }
}