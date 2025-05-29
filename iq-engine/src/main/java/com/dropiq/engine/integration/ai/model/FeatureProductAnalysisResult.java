package com.dropiq.engine.integration.ai.model;

import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Data
public class FeatureProductAnalysisResult {

    // ===== CORE PRODUCT INFORMATION =====
    private String commercialTitle;          // New attractive commercial title
    private String seoTitle;                 // SEO optimized title
    private String h1Title;                  // H1 page title

    // ===== MULTILINGUAL DESCRIPTIONS =====
    private String descriptionUa;            // Ukrainian detailed description
    private String descriptionRu;            // Russian detailed description
    private String descriptionEn;            // English detailed description

    private String shortDescriptionUa;       // Ukrainian short description
    private String shortDescriptionRu;       // Russian short description
    private String shortDescriptionEn;       // English short description

    private String metaDescriptionUa;        // Ukrainian meta description
    private String metaDescriptionRu;        // Russian meta description
    private String metaDescriptionEn;        // English meta description

    // ===== MULTILINGUAL KEYWORDS =====
    private List<String> primaryKeywordsUa = new ArrayList<>();   // Ukrainian primary keywords
    private List<String> primaryKeywordsRu = new ArrayList<>();   // Russian primary keywords
    private List<String> primaryKeywordsEn = new ArrayList<>();   // English primary keywords

    private List<String> longTailKeywordsUa = new ArrayList<>();  // Ukrainian long tail
    private List<String> longTailKeywordsRu = new ArrayList<>();  // Russian long tail
    private List<String> longTailKeywordsEn = new ArrayList<>();  // English long tail

    private List<String> tagsUa = new ArrayList<>();              // Ukrainian tags
    private List<String> tagsRu = new ArrayList<>();              // Russian tags
    private List<String> tagsEn = new ArrayList<>();              // English tags

    // ===== CATEGORY STRUCTURE =====
    private String mainCategory;             // Main Horoshop category
    private String subCategory;              // Sub category
    private String microCategory;            // Micro category
    private String categoryPathUa;           // Full category path in Ukrainian
    private String categoryPathRu;           // Full category path in Russian

    // ===== PRODUCT ATTRIBUTES =====
    private String brandName;                // Detected brand name
    private String modelName;                // Model name if available
    private String detectedGender;           // мужской/женский/унисекс
    private String color;                    // Primary color
    private String material;                 // Material type
    private String style;                    // Style category
    private String season;                   // Season relevance
    private String occasion;                 // Usage occasion

    // Additional attributes map
    private Map<String, String> attributes = new HashMap<>();

    // ===== MARKETING CONTENT =====
    private List<String> sellingPoints = new ArrayList<>();      // Top selling points
    private String targetAudience;           // Target audience description
    private String uniqueSellingPoint;       // Unique selling proposition
    private String emotionalTrigger;         // Emotional purchase trigger
    private String urgencyMessage;           // Urgency/scarcity message

    // ===== USAGE AND CARE =====
    private String careInstructions;         // Care instructions
    private String usageInstructions;        // Usage instructions
    private String sizeGuide;                // Size selection guide
    private String stylingTips;              // Styling recommendations

    // ===== ANALYTICS AND SCORING =====
    private Double trendScore;               // Trend score 1-10
    private Double conversionPotential;      // Conversion potential 1-10
    private Boolean seasonalRelevance;       // Is seasonally relevant
    private String priceCategory;            // бюджетный/средний/премиум
    private String competitiveAdvantage;     // Main competitive advantage

    // ===== HOROSHOP SPECIFIC =====
    private String horoshopPresence;         // В наличии/Под заказ/Нет в наличии
    private List<String> horoshopIcons = new ArrayList<>();      // Product icons for Horoshop
    private List<String> marketplaceExport = new ArrayList<>();  // Export destinations
    private List<String> crossSellCategories = new ArrayList<>(); // Cross-sell categories

    // ===== QUALITY METRICS =====
    private Double qualityScore;
    private Double visualQuality;// Overall quality score
    private Double analysisConfidence;       // Analysis confidence 0-1
    private Long analysisTimestamp;          // Analysis timestamp
    private String analysisVersion;          // Analysis version

    public FeatureProductAnalysisResult() {
        this.analysisTimestamp = System.currentTimeMillis();
        this.analysisVersion = "4.0-horoshop-optimized";
    }

    /**
     * Validation for Horoshop integration
     */
    public boolean isValidForHoroshop() {
        return commercialTitle != null && !commercialTitle.trim().isEmpty() &&
                seoTitle != null && !seoTitle.trim().isEmpty() &&
                (descriptionUa != null || descriptionRu != null) &&
                (metaDescriptionUa != null || metaDescriptionRu != null) &&
                mainCategory != null && !mainCategory.trim().isEmpty() &&
                trendScore != null && trendScore >= 1.0 && trendScore <= 10.0;
    }

    /**
     * Get best available title based on language preference
     */
    public String getBestTitle(String language) {
        if ("ua".equals(language) && commercialTitle != null) return commercialTitle;
        if ("ru".equals(language) && commercialTitle != null) return commercialTitle;
        if ("en".equals(language) && commercialTitle != null) return commercialTitle;
        return commercialTitle != null ? commercialTitle : seoTitle;
    }

    /**
     * Get best available description based on language
     */
    public String getBestDescription(String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return descriptionUa != null ? descriptionUa : descriptionRu;
            case "ru":
                return descriptionRu != null ? descriptionRu : descriptionUa;
            case "en":
                return descriptionEn != null ? descriptionEn : descriptionUa;
            default:
                return descriptionUa != null ? descriptionUa :
                        (descriptionRu != null ? descriptionRu : descriptionEn);
        }
    }

    /**
     * Get best available short description
     */
    public String getBestShortDescription(String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return shortDescriptionUa != null ? shortDescriptionUa : shortDescriptionRu;
            case "ru":
                return shortDescriptionRu != null ? shortDescriptionRu : shortDescriptionUa;
            case "en":
                return shortDescriptionEn != null ? shortDescriptionEn : shortDescriptionUa;
            default:
                return shortDescriptionUa != null ? shortDescriptionUa : shortDescriptionRu;
        }
    }

    /**
     * Get best available meta description
     */
    public String getBestMetaDescription(String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return metaDescriptionUa != null ? metaDescriptionUa : metaDescriptionRu;
            case "ru":
                return metaDescriptionRu != null ? metaDescriptionRu : metaDescriptionUa;
            case "en":
                return metaDescriptionEn != null ? metaDescriptionEn : metaDescriptionUa;
            default:
                return metaDescriptionUa != null ? metaDescriptionUa : metaDescriptionRu;
        }
    }

    /**
     * Get keywords for specific language
     */
    public List<String> getKeywordsForLanguage(String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return primaryKeywordsUa != null && !primaryKeywordsUa.isEmpty() ?
                        primaryKeywordsUa : primaryKeywordsRu;
            case "ru":
                return primaryKeywordsRu != null && !primaryKeywordsRu.isEmpty() ?
                        primaryKeywordsRu : primaryKeywordsUa;
            case "en":
                return primaryKeywordsEn != null && !primaryKeywordsEn.isEmpty() ?
                        primaryKeywordsEn : primaryKeywordsUa;
            default:
                return primaryKeywordsUa != null ? primaryKeywordsUa : primaryKeywordsRu;
        }
    }

    /**
     * Get tags for specific language
     */
    public List<String> getTagsForLanguage(String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return tagsUa != null && !tagsUa.isEmpty() ? tagsUa : tagsRu;
            case "ru":
                return tagsRu != null && !tagsRu.isEmpty() ? tagsRu : tagsUa;
            case "en":
                return tagsEn != null && !tagsEn.isEmpty() ? tagsEn : tagsUa;
            default:
                return tagsUa != null ? tagsUa : tagsRu;
        }
    }

    /**
     * Generate full category path for Horoshop
     */
    public String getFullCategoryPath(String language) {
        String path = "ua".equals(language) || "uk".equals(language) ?
                categoryPathUa : categoryPathRu;

        if (path != null && !path.trim().isEmpty()) {
            return path;
        }

        // Fallback construction
        StringBuilder pathBuilder = new StringBuilder();
        if (mainCategory != null) {
            pathBuilder.append(mainCategory);
            if (subCategory != null) {
                pathBuilder.append(" / ").append(subCategory);
                if (microCategory != null) {
                    pathBuilder.append(" / ").append(microCategory);
                }
            }
        }

        return pathBuilder.length() > 0 ? pathBuilder.toString() : "Товари";
    }

    /**
     * Check if product has high commercial potential
     */
    public boolean hasHighCommercialPotential() {
        return (trendScore != null && trendScore >= 7.0) &&
                (conversionPotential != null && conversionPotential >= 7.0) &&
                (analysisConfidence != null && analysisConfidence >= 0.8);
    }

    /**
     * Generate Horoshop compatible icons based on analysis
     */
    public List<String> generateHoroshopIcons() {
        List<String> icons = new ArrayList<>();

        if (trendScore != null && trendScore >= 8.0) {
            icons.add("Хит");
        }

        if (hasHighCommercialPotential()) {
            icons.add("Рекомендуем");
        }

        if (seasonalRelevance != null && seasonalRelevance) {
            icons.add("Сезонный");
        }

        if ("преміум".equals(priceCategory) || "премиум".equals(priceCategory)) {
            icons.add("Премиум");
        }

        if (conversionPotential != null && conversionPotential >= 8.0) {
            icons.add("Популярный");
        }

        return icons;
    }

    /**
     * Generate marketplace export list based on product analysis
     */
    public List<String> generateMarketplaceExports() {
        List<String> exports = new ArrayList<>();

        // Base exports for all products
        exports.add("facebook");

        // High quality products go to more marketplaces
        if (hasHighCommercialPotential()) {
            exports.add("google");
            exports.add("rozetka");
        }

        // Premium products get additional exposure
        if ("преміум".equals(priceCategory) || "премиум".equals(priceCategory)) {
            exports.add("prom");
            exports.add("allo");
        }

        return exports;
    }
}