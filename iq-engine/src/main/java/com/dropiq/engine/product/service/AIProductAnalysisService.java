package com.dropiq.engine.product.service;

import com.dropiq.engine.integration.ai.OllamaClient;
import com.dropiq.engine.integration.ai.model.ProductAnalysisResult;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.DatasetCategory;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.repository.DataSetRepository;
import com.dropiq.engine.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIProductAnalysisService {

    private final OllamaClient ollamaClient;
    private final ProductRepository productRepository;
    private final DataSetRepository dataSetRepository;
    private final DynamicCategoryService categoryService;

    @Value("${ai.analysis.batch-size:5}")
    private int batchSize;

    @Value("${ai.analysis.max-concurrent:3}")
    private int maxConcurrent;

    // Cache for analysis results by group
    private final Map<String, ProductAnalysisResult> analysisCache = new ConcurrentHashMap<>();

    /**
     * Main method: Analyze product with group sharing logic
     */
    @Async
    public CompletableFuture<Product> analyzeProduct(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            log.info("Starting AI analysis for product: {} (ID: {}, Group: {})",
                    product.getName(), productId, product.getGroupId());

            // Check cache first
            String cacheKey = generateCacheKey(product);
            ProductAnalysisResult cachedResult = analysisCache.get(cacheKey);
            if (cachedResult != null) {
                log.info("Using cached analysis for product group: {}", cacheKey);
                applyAnalysisToProduct(product, cachedResult);
                return CompletableFuture.completedFuture(productRepository.save(product));
            }

            // Check if another product in group already has analysis
            Optional<Product> analyzedGroupProduct = findAnalyzedProductInGroup(product);
            if (analyzedGroupProduct.isPresent()) {
                log.info("Found existing analysis in product group, copying results");
                copyAnalysisFromProduct(analyzedGroupProduct.get(), product);
                return CompletableFuture.completedFuture(productRepository.save(product));
            }

            // Perform new analysis
            ProductAnalysisResult analysis = performProductAnalysis(product);

            // Cache the result
            analysisCache.put(cacheKey, analysis);

            // Apply analysis to product
            applyAnalysisToProduct(product, analysis);
            product = productRepository.save(product);

            // Share analysis with group
            shareAnalysisWithGroup(product, analysis);

            log.info("AI analysis completed for product: {} (ID: {})", product.getName(), productId);
            return CompletableFuture.completedFuture(product);

        } catch (Exception e) {
            log.error("Error analyzing product {}: {}", productId, e.getMessage(), e);
            throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Batch analyze products in a dataset
     */
    @Async
    public CompletableFuture<Integer> analyzeDatasetProducts(Long datasetId) {
        try {
            DataSet dataset = dataSetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

            log.info("Starting batch AI analysis for dataset: {} (Products: {})",
                    dataset.getName(), dataset.getTotalProducts());

            List<Product> productsToAnalyze = dataset.getProducts().stream()
                    .filter(p -> !p.getAiAnalyzed())
                    .toList();

            int totalAnalyzed = 0;

            // Group products by their group ID for efficient analysis
            Map<String, List<Product>> productGroups = productsToAnalyze.stream()
                    .collect(Collectors.groupingBy(p ->
                            p.getGroupId() != null ? p.getGroupId() : "single_" + p.getId()));

            for (Map.Entry<String, List<Product>> entry : productGroups.entrySet()) {
                List<Product> groupProducts = entry.getValue();

                // Analyze first product in group
                Product firstProduct = groupProducts.getFirst();
                ProductAnalysisResult analysis = performProductAnalysis(firstProduct);

                // Apply to all products in group
                for (Product product : groupProducts) {
                    applyAnalysisToProduct(product, analysis);
                    productRepository.save(product);
                    totalAnalyzed++;
                }

                log.info("Analyzed product group: {} ({} products)", entry.getKey(), groupProducts.size());
            }

            log.info("Batch analysis completed for dataset: {}. Total analyzed: {}",
                    dataset.getName(), totalAnalyzed);
            return CompletableFuture.completedFuture(totalAnalyzed);

        } catch (Exception e) {
            log.error("Error in batch analysis for dataset {}: {}", datasetId, e.getMessage(), e);
            throw new RuntimeException("Batch analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Perform complete product analysis
     */
    private ProductAnalysisResult performProductAnalysis(Product product) {
        log.info("Performing complete AI analysis for product: {}", product.getName());

        // Step 1: Analyze product image
        ProductAnalysisResult visionResult = null;
        if (!product.getImageUrls().isEmpty()) {
            visionResult = ollamaClient.analyzeProductImage(
                    product.getImageUrls().get(0),
                    product.getName()
            );
            log.info("Vision analysis completed. Product type: {}, Model: {}, Brand: {}",
                    visionResult.getProductType(), visionResult.getModelName(), visionResult.getBrandDetected());
        }

        // Step 2: Generate multilingual SEO content
        String productInfo = buildProductInfo(product);
        ProductAnalysisResult textResult = ollamaClient.generateMultilingualContent(productInfo, visionResult);

        // Step 3: Merge results
        if (visionResult != null) {
            mergeAnalysisResults(textResult, visionResult);
        }

        // Step 4: Enhance with business logic
        enhanceWithBusinessLogic(textResult, product);

        return textResult;
    }

    /**
     * Apply analysis results to product
     */
    private void applyAnalysisToProduct(Product product, ProductAnalysisResult analysis) {
        // Set AI flags
        product.setAiAnalyzed(true);
        product.setAiAnalysisDate(LocalDateTime.now());
       // product.setAiConfidenceScore(calculateConfidenceScore(analysis));

        // Set SEO optimized name (if available)
        if (analysis.getSeoOptimizedName() != null) {
            // Store in platform specific data for now
           // product.getPlatformSpecificData().put("seo_name", analysis.getSeoOptimizedName());
        }

        // Set category hierarchy
//        if (product.getDatasets() != null && !product.getDatasets().isEmpty()) {
//            DataSet dataset = product.getDatasets().iterator().next();
//            DatasetCategory category = categoryService.findOrCreateCategory(
//                    dataset,
//                    analysis.getCategoryUk(),
//                    analysis.getCategoryRu(),
//                    analysis.getCategoryEn(),
//                    analysis.getSubcategoryUk(),
//                    analysis.getSubcategoryRu(),
//                    analysis.getSubcategoryEn()
//            );
//            product.setCategory(category);
//        }

        // Set multilingual SEO content
//        if (analysis.getSeoTitles() != null) {
//            product.setSeoTitleUk(analysis.getSeoTitles().get("uk"));
//            product.setSeoTitleRu(analysis.getSeoTitles().get("ru"));
//            product.setSeoTitleEn(analysis.getSeoTitles().get("en"));
//        }
//
//        if (analysis.getDescriptions() != null) {
//            product.setDescriptionUk(analysis.getDescriptions().get("uk"));
//            product.setDescriptionRu(analysis.getDescriptions().get("ru"));
//            product.setDescriptionEn(analysis.getDescriptions().get("en"));
//        }
//
//        if (analysis.getMetaDescriptions() != null) {
//            product.setMetaDescriptionUk(analysis.getMetaDescriptions().get("uk"));
//            product.setMetaDescriptionRu(analysis.getMetaDescriptions().get("ru"));
//            product.setMetaDescriptionEn(analysis.getMetaDescriptions().get("en"));
//        }
//
//        // Set tags
//        if (analysis.getTags() != null) {
//            product.setTagsUk(new HashSet<>(analysis.getTags().getOrDefault("uk", new ArrayList<>())));
//            product.setTagsRu(new HashSet<>(analysis.getTags().getOrDefault("ru", new ArrayList<>())));
//            product.setTagsEn(new HashSet<>(analysis.getTags().getOrDefault("en", new ArrayList<>())));
//        }
//
//        // Set target audience
//        if (analysis.getTargetAudience() != null) {
//            product.setTargetAudienceUk(analysis.getTargetAudience().get("uk"));
//            product.setTargetAudienceRu(analysis.getTargetAudience().get("ru"));
//            product.setTargetAudienceEn(analysis.getTargetAudience().get("en"));
//        }
//
//        // Set AI-generated attributes
//        product.setTrendScore(BigDecimal.valueOf(analysis.getTrendScore() != null ? analysis.getTrendScore() : 5.0));
//        product.setPredictedPriceRange(analysis.getPredictedPriceRange());
//        product.setStyleTags(analysis.getStyle());
//
//        // Set additional attributes
//        if (analysis.getMainFeatures() != null && !analysis.getMainFeatures().isEmpty()) {
//            product.setMainFeatures(String.join(", ", analysis.getMainFeatures()));
//        }
//
//        if (analysis.getColors() != null && !analysis.getColors().isEmpty()) {
//            product.setColorAnalysis(String.join(", ", analysis.getColors()));
//        }
//
//        // Store extended analysis data
//        if (analysis.getSellingPoints() != null) {
//            product.getPlatformSpecificData().put("selling_points", String.join("; ", analysis.getSellingPoints()));
//        }
//
//        if (analysis.getCompetitiveAdvantage() != null) {
//            product.getPlatformSpecificData().put("competitive_advantage", analysis.getCompetitiveAdvantage());
//        }
//
//        if (analysis.getUrgencyTriggers() != null) {
//            product.getPlatformSpecificData().put("urgency_triggers", analysis.getUrgencyTriggers());
//        }
//
//        if (analysis.getBrandDetected() != null) {
//            product.getPlatformSpecificData().put("detected_brand", analysis.getBrandDetected());
//        }
//
//        if (analysis.getModelName() != null) {
//            product.getPlatformSpecificData().put("model_name", analysis.getModelName());
//        }
    }

    /**
     * Build product info string for analysis
     */
    private String buildProductInfo(Product product) {
        StringBuilder info = new StringBuilder();

        info.append("Name: ").append(product.getName()).append("\n");

        if (product.getOriginalDescription() != null) {
            String cleanDesc = product.getOriginalDescription()
                    .replaceAll("<[^>]+>", "") // Remove HTML
                    .replaceAll("\\s+", " ")    // Normalize whitespace
                    .trim();
            if (cleanDesc.length() > 500) {
                cleanDesc = cleanDesc.substring(0, 500) + "...";
            }
            info.append("Description: ").append(cleanDesc).append("\n");
        }

        info.append("Price: ").append(product.getOriginalPrice()).append(" UAH\n");

        if (product.getExternalCategoryName() != null) {
            info.append("Category: ").append(product.getExternalCategoryName()).append("\n");
        }

        if (product.getAttributes() != null && !product.getAttributes().isEmpty()) {
            info.append("Attributes: ");
            product.getAttributes().forEach((key, value) ->
                    info.append(key).append(": ").append(value).append(", "));
            info.append("\n");
        }

        if (product.getStock() != null) {
            info.append("Stock: ").append(product.getStock()).append(" units\n");
        }

        info.append("Source: ").append(product.getSourceType()).append("\n");

        return info.toString();
    }

    /**
     * Merge vision and text analysis results
     */
    private void mergeAnalysisResults(ProductAnalysisResult target, ProductAnalysisResult vision) {
        if (vision.getProductType() != null) target.setProductType(vision.getProductType());
        if (vision.getModelName() != null) target.setModelName(vision.getModelName());
        if (vision.getBrandDetected() != null) target.setBrandDetected(vision.getBrandDetected());
        if (vision.getMainFeatures() != null) target.setMainFeatures(vision.getMainFeatures());
        if (vision.getMaterials() != null) target.setMaterials(vision.getMaterials());
        if (vision.getColors() != null) target.setColors(vision.getColors());
        if (vision.getStyle() != null) target.setStyle(vision.getStyle());
        if (vision.getVisualQuality() != null) target.setVisualQuality(vision.getVisualQuality());
        if (vision.getPriceRange() != null) target.setPriceRange(vision.getPriceRange());
        if (vision.getTargetDemographic() != null) target.setTargetDemographic(vision.getTargetDemographic());
        if (vision.getQualityIndicators() != null) target.setQualityIndicators(vision.getQualityIndicators());
        if (vision.getSeason() != null) target.setSeason(vision.getSeason());
        if (vision.getUseCases() != null) target.setUseCases(vision.getUseCases());
    }

    /**
     * Enhance analysis with business logic
     */
    private void enhanceWithBusinessLogic(ProductAnalysisResult analysis, Product product) {
        // Adjust trend score based on price and stock
        if (analysis.getTrendScore() != null) {
            double score = analysis.getTrendScore();

            // Higher stock might indicate popular product
            if (product.getStock() != null && product.getStock() > 50) {
                score += 0.5;
            }

            // Premium products often have higher conversion
            if ("premium".equals(analysis.getPriceRange()) || "luxury".equals(analysis.getPriceRange())) {
                score += 0.5;
            }

            analysis.setTrendScore(Math.min(9.5, score));
        }

        // Add seasonal adjustments
        if (analysis.getSeason() != null) {
            Calendar cal = Calendar.getInstance();
            int month = cal.get(Calendar.MONTH);

            // Boost seasonal products
            if (analysis.getSeason().contains("winter") && (month >= 10 || month <= 2)) {
                analysis.setTrendScore(Math.min(9.5, analysis.getTrendScore() + 1.0));
            } else if (analysis.getSeason().contains("summer") && (month >= 4 && month <= 8)) {
                analysis.setTrendScore(Math.min(9.5, analysis.getTrendScore() + 1.0));
            }
        }

        // Add urgency based on stock
        if (product.getStock() != null && product.getStock() < 10) {
            analysis.setUrgencyTriggers("Only " + product.getStock() + " left in stock!");
        }
    }

    /**
     * Calculate confidence score for analysis
     */
    private Double calculateConfidenceScore(ProductAnalysisResult analysis) {
        double score = 5.0; // Base score

        // Add points for completeness
        if (analysis.getProductType() != null) score += 0.5;
        if (analysis.getModelName() != null) score += 0.5;
        if (analysis.getBrandDetected() != null) score += 1.0;
        if (analysis.getMainFeatures() != null && analysis.getMainFeatures().size() >= 3) score += 1.0;
        if (analysis.getSeoTitles() != null && analysis.getSeoTitles().size() == 3) score += 1.0;
        if (analysis.getDescriptions() != null && analysis.getDescriptions().size() == 3) score += 1.0;
        if (analysis.getTags() != null && analysis.getTags().size() == 3) score += 0.5;

        return Math.min(10.0, score);
    }

    /**
     * Generate cache key for product
     */
    private String generateCacheKey(Product product) {
        if (product.getGroupId() != null && !product.getGroupId().trim().isEmpty()) {
            return product.getSourceType() + ":" + product.getGroupId();
        }
        return product.getSourceType() + ":single:" + product.getExternalId();
    }

    /**
     * Find analyzed product in the same group
     */
    private Optional<Product> findAnalyzedProductInGroup(Product product) {
        if (product.getGroupId() == null || product.getGroupId().trim().isEmpty()) {
            return Optional.empty();
        }

        return productRepository.findByGroupIdAndSourceTypeAndAiAnalyzedTrue(
                product.getGroupId(),
                product.getSourceType()
        ).stream().findFirst();
    }

    /**
     * Copy analysis from one product to another
     */
    private void copyAnalysisFromProduct(Product source, Product target) {
        target.setAiAnalyzed(true);
        target.setAiAnalysisDate(LocalDateTime.now());
       // target.setAiConfidenceScore(source.getAiConfidenceScore());
        target.setCategory(source.getCategory());

        // Copy all multilingual content
//        target.setSeoTitleUk(source.getSeoTitleUk());
//        target.setSeoTitleRu(source.getSeoTitleRu());
//        target.setSeoTitleEn(source.getSeoTitleEn());
//
//        target.setDescriptionUk(source.getDescriptionUk());
//        target.setDescriptionRu(source.getDescriptionRu());
//        target.setDescriptionEn(source.getDescriptionEn());
//
//        target.setMetaDescriptionUk(source.getMetaDescriptionUk());
//        target.setMetaDescriptionRu(source.getMetaDescriptionRu());
//        target.setMetaDescriptionEn(source.getMetaDescriptionEn());
//
//        target.setTagsUk(new HashSet<>(source.getTagsUk()));
//        target.setTagsRu(new HashSet<>(source.getTagsRu()));
//        target.setTagsEn(new HashSet<>(source.getTagsEn()));
//
//        target.setTargetAudienceUk(source.getTargetAudienceUk());
//        target.setTargetAudienceRu(source.getTargetAudienceRu());
//        target.setTargetAudienceEn(source.getTargetAudienceEn());
//
//        // Copy AI attributes
//        target.setTrendScore(source.getTrendScore());
//        target.setPredictedPriceRange(source.getPredictedPriceRange());
//        target.setStyleTags(source.getStyleTags());
//        target.setMainFeatures(source.getMainFeatures());
//        target.setColorAnalysis(source.getColorAnalysis());
//
//        // Copy platform specific data
//        source.getPlatformSpecificData().forEach((key, value) -> {
//            if (key.startsWith("seo_") || key.startsWith("selling_") || key.startsWith("competitive_")) {
//                target.getPlatformSpecificData().put(key, value);
//            }
//        });
    }

    /**
     * Share analysis with other products in the group
     */
    private void shareAnalysisWithGroup(Product analyzedProduct, ProductAnalysisResult analysis) {
        if (analyzedProduct.getGroupId() == null) return;

        List<Product> groupProducts = productRepository.findByGroupIdAndSourceTypeAndAiAnalyzedFalse(
                analyzedProduct.getGroupId(),
                analyzedProduct.getSourceType()
        );

        for (Product product : groupProducts) {
            if (!product.getId().equals(analyzedProduct.getId())) {
                copyAnalysisFromProduct(analyzedProduct, product);
                productRepository.save(product);
            }
        }

        log.info("Shared analysis with {} products in group: {}",
                groupProducts.size(), analyzedProduct.getGroupId());
    }

    /**
     * Clear analysis cache
     */
    public void clearAnalysisCache() {
        analysisCache.clear();
        log.info("Analysis cache cleared");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", analysisCache.size());
        stats.put("cacheKeys", analysisCache.keySet());
        return stats;
    }
}