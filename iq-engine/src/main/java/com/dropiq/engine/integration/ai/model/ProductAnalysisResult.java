package com.dropiq.engine.integration.ai.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProductAnalysisResult {
    // Vision analysis fields
    private String productType;
    private String mainCategory;
    private String subcategory;
    private List<String> mainFeatures;
    private List<String> colors;
    private String style;
    private Map<String, String> targetAudience;
    private String priceRange;
    private Double visualQuality;
    private Boolean brandVisible;

    // Category fields
    private String categoryUk;
    private String categoryRu;
    private String categoryEn;
    private String subcategoryUk;
    private String subcategoryRu;
    private String subcategoryEn;

    // Multilingual content
    private Map<String, String> seoTitles;
    private Map<String, String> descriptions;
    private Map<String, String> metaDescriptions;
    private Map<String, List<String>> tags;

    // Analysis metadata
    private Double trendScore;
    private String predictedPriceRange;
    private String styleTags;
    private Double confidence;
    private String marketingAngles;
    private String competitiveAdvantage;
    private String urgencyTriggers;
}
