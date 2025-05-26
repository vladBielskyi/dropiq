package com.dropiq.engine.integration.imp.horoshop.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class HoroshopProduct {

    // ===== BASIC PRODUCT INFO =====
    @JsonProperty("parent_article")
    private String parentArticle;

    private String article; // SKU/Product ID (REQUIRED)

    // Multi-language content
    private Map<String, String> title = new HashMap<>(); // REQUIRED
    private Map<String, String> description = new HashMap<>();
    @JsonProperty("short_description")
    private Map<String, String> shortDescription = new HashMap<>();

    // ===== PRODUCT ATTRIBUTES =====
    private String color;
    private String size;
    private String material;
    private String brand;
    private String model;
    private String gtin; // Global Trade Item Number
    private String mpn;  // Manufacturer Part Number

    // Additional product info
    private String weight;
    private String dimensions;
    @JsonProperty("country_of_origin")
    private String countryOfOrigin;
    private String condition; // "new", "used", "refurbished"

    // ===== PRICING =====
    private Double price; // REQUIRED
    @JsonProperty("price_old")
    private Double priceOld;
    @JsonProperty("wholesale_prices")
    private List<HoroshopWholesalePrice> wholesalePrices = new ArrayList<>();

    // Currency and pricing rules
    private String currency = "UAH";
    @JsonProperty("price_per_unit")
    private Double pricePerUnit;
    @JsonProperty("unit_type")
    private String unitType; // "шт", "кг", "м"

    // ===== INVENTORY =====
    private String presence = "В наличии"; // "В наличии", "Нет в наличии", "Под заказ"
    private Integer quantity = 0;
    @JsonProperty("min_quantity")
    private Integer minQuantity = 1;
    @JsonProperty("stock_status")
    private String stockStatus; // "in_stock", "out_of_stock", "on_backorder"

    // ===== CATEGORIES =====
    private String parent; // Category path: "Category / Subcategory"
    @JsonProperty("parent_id")
    private Long parentId; // Category ID (recommended over path)
    @JsonProperty("alt_parent")
    private List<Object> altParent = new ArrayList<>(); // Additional categories

    // ===== SEO & DISPLAY =====
    private String slug;
    @JsonProperty("display_in_showcase")
    private Boolean displayInShowcase = true;
    @JsonProperty("forceAliasUpdate")
    private Boolean forceAliasUpdate = false;

    // SEO meta data
    @JsonProperty("meta_title")
    private Map<String, String> metaTitle = new HashMap<>();
    @JsonProperty("meta_description")
    private Map<String, String> metaDescription = new HashMap<>();
    @JsonProperty("meta_keywords")
    private Map<String, String> metaKeywords = new HashMap<>();

    // ===== IMAGES =====
    private HoroshopImages images;

    // ===== MARKETING =====
    private List<String> icons = new ArrayList<>(); // "Распродажа", "Новинка", "Хит"
    private Integer popularity = 50; // 0-100

    // Labels and badges
    private List<String> labels = new ArrayList<>();
    private List<String> badges = new ArrayList<>();

    // ===== GUARANTEES & PROMOTIONS =====
    @JsonProperty("guarantee_shop")
    private String guaranteeShop;
    @JsonProperty("guarantee_length")
    private Integer guaranteeLength; // months
    @JsonProperty("guarantee_manufacturer")
    private String guaranteeManufacturer;

    // Countdown/Timer promotions
    @JsonProperty("countdown_end_time")
    private String countdownEndTime; // "2021-12-31 23:59:59"
    @JsonProperty("countdown_description")
    private Map<String, String> countdownDescription = new HashMap<>();

    // Special offers
    @JsonProperty("special_offer")
    private Map<String, String> specialOffer = new HashMap<>();
    @JsonProperty("discount_percentage")
    private Double discountPercentage;

    // ===== EXPORT SETTINGS =====
    @JsonProperty("export_to_marketplace")
    private String exportToMarketplace; // "Facebook Feed;Rozetka Feed;Google Shopping"

    // Export configurations
    @JsonProperty("facebook_export")
    private Boolean facebookExport = true;
    @JsonProperty("google_export")
    private Boolean googleExport = true;
    @JsonProperty("rozetka_export")
    private Boolean rozetkaExport = false;
    @JsonProperty("prom_export")
    private Boolean promExport = false;

    // ===== CHARACTERISTICS/ATTRIBUTES =====
    private List<HoroshopCharacteristic> characteristics = new ArrayList<>();

    // ===== SHIPPING & DELIVERY =====
    @JsonProperty("shipping_weight")
    private Double shippingWeight;
    @JsonProperty("shipping_dimensions")
    private String shippingDimensions;
    @JsonProperty("free_shipping")
    private Boolean freeShipping = false;
    @JsonProperty("delivery_time")
    private String deliveryTime; // "1-3 дня", "под заказ 5-7 дней"

    // ===== ADDITIONAL CONTENT =====
    @JsonProperty("care_instructions")
    private Map<String, String> careInstructions = new HashMap<>();
    @JsonProperty("usage_instructions")
    private Map<String, String> usageInstructions = new HashMap<>();
    @JsonProperty("size_chart")
    private String sizeChart;

    // ===== RELATED PRODUCTS =====
    @JsonProperty("related_products")
    private List<String> relatedProducts = new ArrayList<>(); // Article IDs
    @JsonProperty("cross_sell")
    private List<String> crossSell = new ArrayList<>();
    @JsonProperty("up_sell")
    private List<String> upSell = new ArrayList<>();

    // ===== STATUS & VISIBILITY =====
    private String status = "active"; // "active", "inactive", "draft"
    private Boolean visible = true;
    @JsonProperty("featured_product")
    private Boolean featuredProduct = false;

    // Availability scheduling
    @JsonProperty("available_from")
    private String availableFrom; // "2024-01-01 00:00:00"
    @JsonProperty("available_until")
    private String availableUntil; // "2024-12-31 23:59:59"

    // ===== ANALYTICS & TRACKING =====
    @JsonProperty("google_analytics_category")
    private String googleAnalyticsCategory;
    @JsonProperty("facebook_pixel_category")
    private String facebookPixelCategory;

    // Performance tracking
    @JsonProperty("conversion_tracking")
    private Boolean conversionTracking = true;
    @JsonProperty("analytics_enabled")
    private Boolean analyticsEnabled = true;

    // ===== REVIEWS & RATINGS =====
    @JsonProperty("enable_reviews")
    private Boolean enableReviews = true;
    @JsonProperty("auto_approve_reviews")
    private Boolean autoApproveReviews = false;
    @JsonProperty("review_reminder")
    private Boolean reviewReminder = true;

    // ===== VARIANTS & COMBINATIONS =====
    @JsonProperty("has_variants")
    private Boolean hasVariants = false;
    @JsonProperty("variant_type")
    private String variantType; // "size", "color", "size_color"
    @JsonProperty("variant_options")
    private Map<String, List<String>> variantOptions = new HashMap<>();

    // ===== TECHNICAL SPECIFICATIONS =====
    @JsonProperty("technical_specs")
    private Map<String, String> technicalSpecs = new HashMap<>();

    // Product compliance
    @JsonProperty("age_restriction")
    private Integer ageRestriction; // 0, 16, 18
    @JsonProperty("requires_license")
    private Boolean requiresLicense = false;
    @JsonProperty("hazardous_material")
    private Boolean hazardousMaterial = false;

    // ===== CUSTOM FIELDS =====
    @JsonProperty("custom_fields")
    private Map<String, Object> customFields = new HashMap<>();

    // Integration data
    @JsonProperty("external_ids")
    private Map<String, String> externalIds = new HashMap<>(); // Integration with other systems

    // AI and automation
    @JsonProperty("ai_optimized")
    private Boolean aiOptimized = false;
    @JsonProperty("auto_translate")
    private Boolean autoTranslate = false;
    @JsonProperty("auto_seo")
    private Boolean autoSeo = false;

    // ===== META DATA =====
    private Map<String, Object> meta = new HashMap<>();

    // ===== TIMESTAMPS =====
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("updated_at")
    private String updatedAt;
    @JsonProperty("published_at")
    private String publishedAt;

    // ===== UTILITY METHODS =====

    /**
     * Add characteristic with simple name-value pair
     */
    public void addCharacteristic(String name, String value) {
        if (name != null && value != null && !value.trim().isEmpty()) {
            HoroshopCharacteristic characteristic = new HoroshopCharacteristic();
            characteristic.setName(name);
            characteristic.setValue(value);
            this.characteristics.add(characteristic);
        }
    }

    /**
     * Add characteristic with unit
     */
    public void addCharacteristicWithUnit(String name, String value, String unit) {
        if (name != null && value != null && !value.trim().isEmpty()) {
            HoroshopCharacteristic characteristic = new HoroshopCharacteristic();
            characteristic.setName(name);
            characteristic.setValue(value);
            characteristic.setUnit(unit);
            this.characteristics.add(characteristic);
        }
    }

    /**
     * Add multilingual title
     */
    public void addTitle(String language, String titleText) {
        if (language != null && titleText != null && !titleText.trim().isEmpty()) {
            this.title.put(language, titleText);
        }
    }

    /**
     * Add multilingual description
     */
    public void addDescription(String language, String descText) {
        if (language != null && descText != null && !descText.trim().isEmpty()) {
            this.description.put(language, descText);
        }
    }

    /**
     * Add marketing icon
     */
    public void addIcon(String icon) {
        if (icon != null && !icon.trim().isEmpty() && !this.icons.contains(icon)) {
            this.icons.add(icon);
        }
    }

    /**
     * Set discount from old price
     */
    public void calculateDiscount() {
        if (this.price != null && this.priceOld != null && this.priceOld > this.price) {
            this.discountPercentage = ((this.priceOld - this.price) / this.priceOld) * 100;
            this.discountPercentage = Math.round(this.discountPercentage * 100.0) / 100.0; // Round to 2 decimal places
        }
    }

    /**
     * Check if product is on sale
     */
    public boolean isOnSale() {
        return this.priceOld != null && this.price != null && this.priceOld > this.price;
    }

    /**
     * Check if product is in stock
     */
    public boolean isInStock() {
        return "В наличии".equals(this.presence) ||
                "В наявності".equals(this.presence) ||
                (this.quantity != null && this.quantity > 0);
    }

    /**
     * Check if product is ready for export
     */
    public boolean isReadyForExport() {
        return this.article != null && !this.article.trim().isEmpty() &&
                this.title != null && !this.title.isEmpty() &&
                this.price != null && this.price > 0 &&
                this.presence != null && !this.presence.trim().isEmpty();
    }

    /**
     * Get primary title (Ukrainian first, then Russian, then first available)
     */
    public String getPrimaryTitle() {
        if (this.title == null || this.title.isEmpty()) return null;

        if (this.title.containsKey("ua")) return this.title.get("ua");
        if (this.title.containsKey("ru")) return this.title.get("ru");

        return this.title.values().iterator().next();
    }

    /**
     * Get primary description
     */
    public String getPrimaryDescription() {
        if (this.description == null || this.description.isEmpty()) return null;

        if (this.description.containsKey("ua")) return this.description.get("ua");
        if (this.description.containsKey("ru")) return this.description.get("ru");

        return this.description.values().iterator().next();
    }

    /**
     * Validate product data for Horoshop API
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (this.article == null || this.article.trim().isEmpty()) {
            errors.add("Article (SKU) is required");
        }

        if (this.title == null || this.title.isEmpty()) {
            errors.add("Title is required");
        }

        if (this.price == null || this.price <= 0) {
            errors.add("Valid price is required");
        }

        if (this.presence == null || this.presence.trim().isEmpty()) {
            errors.add("Presence status is required");
        }

        return errors;
    }
}