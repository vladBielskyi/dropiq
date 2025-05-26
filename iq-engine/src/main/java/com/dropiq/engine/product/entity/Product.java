package com.dropiq.engine.product.entity;

import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.product.model.ProductStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Оптимізована ентіті Product спеціально для роботи з Horoshop API
 * - Мінімізовані поля для швидкості
 * - Horoshop-готові атрибути
 * - Оптимізовані індекси
 */
@Entity
@Table(name = "product",
        indexes = {
                @Index(name = "idx_product_group_source", columnList = "group_id, source_type"),
                @Index(name = "idx_product_category", columnList = "category_id"),
                @Index(name = "idx_product_status_available", columnList = "status, available"),
                @Index(name = "idx_product_last_sync", columnList = "last_sync"),
                @Index(name = "idx_product_ai_analyzed", columnList = "ai_analyzed"),
                @Index(name = "idx_product_horoshop_ready", columnList = "horoshop_ready")
        })
@Data
@EqualsAndHashCode(exclude = {"datasets", "category"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==============================================
    // БАЗОВІ ПОЛЯ ДЛЯ ІДЕНТИФІКАЦІЇ
    // ==============================================

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "group_id", length = 100)
    private String groupId; // Для варіантів товару

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "original_description", length = 4000)
    private String originalDescription;

    // ==============================================
    // ЦІНИ ТА НАЯВНІСТЬ
    // ==============================================

    @Column(name = "original_price", precision = 19, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "selling_price", precision = 19, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "markup_percentage", precision = 5, scale = 2)
    private BigDecimal markupPercentage = BigDecimal.valueOf(10);

    @Column(name = "stock")
    private Integer stock = 0;

    @Column(name = "available")
    private Boolean available = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status = ProductStatus.DRAFT;

    // ==============================================
    // HOROSHOP ОПТИМІЗОВАНІ ПОЛЯ
    // ==============================================

    // SEO контент (готовий для Horoshop)
    @Column(name = "seo_title_ua", length = 255)
    private String seoTitleUa;

    @Column(name = "seo_title_ru", length = 255)
    private String seoTitleRu;

    @Column(name = "description_ua", length = 2000)
    private String descriptionUa;

    @Column(name = "description_ru", length = 2000)
    private String descriptionRu;

    @Column(name = "meta_description_ua", length = 300)
    private String metaDescriptionUa;

    @Column(name = "meta_description_ru", length = 300)
    private String metaDescriptionRu;

    // Основні характеристики товару (для Horoshop characteristics)
    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "size", length = 50)
    private String size;

    @Column(name = "material", length = 100)
    private String material;

    @Column(name = "gender", length = 20)
    private String gender; // чоловічий/жіночий/унісекс

    @Column(name = "season", length = 30)
    private String season; // весна/літо/осінь/зима/всесезон

    // ==============================================
    // HOROSHOP СПЕЦИФІЧНІ ПОЛЯ
    // ==============================================

    @Column(name = "horoshop_article", length = 100)
    private String horoshopArticle; // Може відрізнятися від external_id

    @Column(name = "horoshop_category_path", length = 500)
    private String horoshopCategoryPath; // "Одяг / Жіночий / Сукні"

    @Column(name = "horoshop_ready")
    private Boolean horoshopReady = false; // Готовий до експорту

    @Column(name = "horoshop_exported")
    private Boolean horoshopExported = false;

    @Column(name = "horoshop_last_export")
    private LocalDateTime horoshopLastExport;

    @Column(name = "horoshop_status", length = 50)
    private String horoshopStatus; // SUCCESS, ERROR, PENDING

    @Column(name = "horoshop_error_message", length = 1000)
    private String horoshopErrorMessage;

    // Horoshop presence статус
    @Column(name = "presence", length = 50)
    private String presence = "В наличии"; // "В наличии", "Нет в наличии", "Под заказ"

    // ==============================================
    // ЗОБРАЖЕННЯ (ОПТИМІЗОВАНО)
    // ==============================================

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 500)
    @OrderColumn(name = "image_order")
    private List<String> imageUrls = new ArrayList<>();

    // Головне зображення (для швидкого доступу)
    @Column(name = "main_image_url", length = 500)
    private String mainImageUrl;

    // ==============================================
    // КАТЕГОРИЗАЦІЯ
    // ==============================================

    @Column(name = "external_category_id", length = 100)
    private String externalCategoryId;

    @Column(name = "external_category_name", length = 200)
    private String externalCategoryName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DatasetCategory category;

    // ==============================================
    // AI ТА SEO МЕТРИКИ
    // ==============================================

    @Column(name = "ai_analyzed")
    private Boolean aiAnalyzed = false;

    @Column(name = "ai_analysis_date")
    private LocalDateTime aiAnalysisDate;

    @Column(name = "ai_confidence_score", precision = 5, scale = 2)
    private BigDecimal aiConfidenceScore;

    @Column(name = "trend_score", precision = 5, scale = 2)
    private BigDecimal trendScore;

    @Column(name = "seo_optimized")
    private Boolean seoOptimized = false;

    // ==============================================
    // ТЕГИ (ОПТИМІЗОВАНО ДЛЯ ПОШУКУ)
    // ==============================================

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag", length = 50)
    private Set<String> tags = new HashSet<>();

    // ==============================================
    // ОСНОВНІ АТРИБУТИ (ЗАМІСТЬ ВЕЛИКОЇ МАПИ)
    // ==============================================

    @ElementCollection
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "attr_key", length = 100)
    @Column(name = "attr_value", length = 500)
    private Map<String, String> attributes = new HashMap<>();

    // ==============================================
    // ТЕХНІЧНІ ПОЛЯ
    // ==============================================

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

    @ElementCollection
    @CollectionTable(name = "product_platform_data", joinColumns = @JoinColumn(name = "product_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    private Map<String, String> platformSpecificData = new HashMap<>();

    // ==============================================
    // ЗВ'ЯЗКИ
    // ==============================================

    @ManyToMany(mappedBy = "products", fetch = FetchType.EAGER)
    private Set<DataSet> datasets = new HashSet<>();

    // ==============================================
    // БІЗНЕС ЛОГІКА
    // ==============================================

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

    /**
     * Розрахунок продажної ціни з урахуванням націнки
     */
    public void calculateSellingPrice() {
        if (originalPrice != null && markupPercentage != null) {
            BigDecimal markup = originalPrice.multiply(markupPercentage)
                    .divide(BigDecimal.valueOf(100), 4, BigDecimal.ROUND_HALF_UP);
            sellingPrice = originalPrice.add(markup);
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
        return name != null && !name.trim().isEmpty() &&
                sellingPrice != null && sellingPrice.compareTo(BigDecimal.ZERO) > 0 &&
                (seoTitleUa != null || seoTitleRu != null) &&
                (descriptionUa != null || descriptionRu != null) &&
                status == ProductStatus.ACTIVE &&
                category != null;
    }

    /**
     * Оновлення головного зображення
     */
    public void updateMainImageUrl() {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            mainImageUrl = imageUrls.getFirst();
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
     * Отримання оптимізованого заголовку для мови
     */
    public String getOptimizedTitle(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> seoTitleUa != null ? seoTitleUa : name;
            case "ru" -> seoTitleRu != null ? seoTitleRu : name;
            default -> seoTitleUa != null ? seoTitleUa :
                    (seoTitleRu != null ? seoTitleRu : name);
        };
    }

    /**
     * Отримання оптимізованого опису для мови
     */
    public String getOptimizedDescription(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> descriptionUa != null ? descriptionUa : originalDescription;
            case "ru" -> descriptionRu != null ? descriptionRu : originalDescription;
            default -> descriptionUa != null ? descriptionUa :
                    (descriptionRu != null ? descriptionRu : originalDescription);
        };
    }

    /**
     * Отримання meta опису для мови
     */
    public String getOptimizedMetaDescription(String lang) {
        return switch (lang.toLowerCase()) {
            case "ua", "uk" -> metaDescriptionUa;
            case "ru" -> metaDescriptionRu;
            default -> metaDescriptionUa != null ? metaDescriptionUa : metaDescriptionRu;
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
     * Додавання зображення
     */
    public void addImageUrl(String imageUrl) {
        if (imageUrl != null && !imageUrl.trim().isEmpty() && !imageUrls.contains(imageUrl)) {
            imageUrls.add(imageUrl);
            if (mainImageUrl == null) {
                mainImageUrl = imageUrl;
            }
        }
    }

    /**
     * Додавання тегу
     */
    public void addTag(String tag) {
        if (tag != null && !tag.trim().isEmpty() && tags.size() < 10) {
            tags.add(tag.trim().toLowerCase());
        }
    }

    /**
     * Встановлення характеристики
     */
    public void setAttribute(String key, String value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
    }

    /**
     * Отримання характеристики
     */
    public String getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Перевірка чи товар є варіантом
     */
    public boolean isVariant() {
        return groupId != null && !groupId.trim().isEmpty();
    }

    /**
     * Перевірка чи потрібна синхронізація
     */
    public boolean needsSync() {
        if (lastSync == null) return true;

        return updatedAt != null && updatedAt.isAfter(lastSync);
    }

    /**
     * Перевірка чи товар має низький залишок
     */
    public boolean hasLowStock() {
        return stock != null && stock <= 5 && stock > 0;
    }

    /**
     * Перевірка чи товар новий (створений менше ніж 30 днів тому)
     */
    public boolean isNew() {
        return createdAt != null &&
                createdAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * Перевірка чи товар популярний (високий trend score)
     */
    public boolean isPopular() {
        return trendScore != null &&
                trendScore.compareTo(BigDecimal.valueOf(7.0)) >= 0;
    }

    /**
     * Генерація унікального ключа для кешування
     */
    public String getCacheKey() {
        return String.format("%s_%s_%s",
                sourceType != null ? sourceType.name() : "UNKNOWN",
                groupId != null ? groupId : "single",
                externalId);
    }

    /**
     * Копіювання AI даних з іншого продукту (для варіантів)
     */
    public void copyAiDataFrom(Product source) {
        this.seoTitleUa = source.seoTitleUa;
        this.seoTitleRu = source.seoTitleRu;
        this.descriptionUa = source.descriptionUa;
        this.descriptionRu = source.descriptionRu;
        this.metaDescriptionUa = source.metaDescriptionUa;
        this.metaDescriptionRu = source.metaDescriptionRu;
        this.trendScore = source.trendScore;
        this.aiAnalyzed = true;
        this.aiAnalysisDate = LocalDateTime.now();
        this.aiConfidenceScore = source.aiConfidenceScore;

        // Копіюємо теги
        if (source.tags != null) {
            this.tags.addAll(source.tags);
        }
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

    /**
     * Валідація перед збереженням
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (name == null || name.trim().isEmpty()) {
            errors.add("Product name is required");
        }

        if (name != null && name.length() > 500) {
            errors.add("Product name is too long (max 500 characters)");
        }

        if (sellingPrice != null && sellingPrice.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Selling price cannot be negative");
        }

        if (stock != null && stock < 0) {
            errors.add("Stock cannot be negative");
        }

        if (externalId == null || externalId.trim().isEmpty()) {
            errors.add("External ID is required");
        }

        return errors;
    }
}
