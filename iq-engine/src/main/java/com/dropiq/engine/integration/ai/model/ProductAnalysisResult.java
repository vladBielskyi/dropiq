package com.dropiq.engine.integration.ai.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProductAnalysisResult {
    // Vision analysis fields
    private String productType;
    private String modelName;
    private String brandDetected;
    private List<String> mainFeatures;
    private List<String> materials;
    private List<String> colors;
    private String style;
    private Map<String, String> targetDemographic;
    private String priceRange;
    private Double visualQuality;
    private Boolean brandVisible;
    private Map<String, String> qualityIndicators;
    private String season;
    private List<String> useCases;

    // SEO optimized name (English only)
    private String seoOptimizedName;

    // Category hierarchy (3 levels)
    private String categoryUk;
    private String categoryRu;
    private String categoryEn;
    private String subcategoryUk;
    private String subcategoryRu;
    private String subcategoryEn;
    private String microCategoryUk;
    private String microCategoryRu;
    private String microCategoryEn;

    // Multilingual content
    private Map<String, String> seoTitles;
    private Map<String, String> descriptions;
    private Map<String, String> metaDescriptions;
    private Map<String, List<String>> tags;
    private Map<String, String> targetAudience;

    // E-commerce optimization
    private List<String> sellingPoints;
    private Double trendScore;
    private Double conversionScore;
    private String searchVolumeEstimate;
    private String predictedPriceRange;
    private String styleTags;
    private Double confidence;
    private String marketingAngles;
    private String competitiveAdvantage;
    private String urgencyTriggers;
    private List<String> crossSellCategories;

    // Additional metadata
    private Map<String, String> attributes;
    private String analysisVersion = "2.0";
    private Long analysisTimestamp = System.currentTimeMillis();
}