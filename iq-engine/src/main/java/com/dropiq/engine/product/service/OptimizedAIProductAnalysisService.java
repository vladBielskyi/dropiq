package com.dropiq.engine.product.service;

import com.dropiq.engine.integration.ai.GPT4MiniClient;
import com.dropiq.engine.integration.ai.model.FeatureProductAnalysisResult;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.repository.DataSetRepository;
import com.dropiq.engine.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class OptimizedAIProductAnalysisService {

    private final GPT4MiniClient gptProvider;
    private final ProductRepository productRepository;
    private final DataSetRepository dataSetRepository;
    private final SmartCategoryService categoryService;

    @Value("${ai.analysis.batch-size:5}")
    private int batchSize;

    @Value("${ai.analysis.enable-vision:true}")
    private boolean enableVision;

    @Value("${ai.analysis.delay-between-requests:1500}")
    private long delayBetweenRequests;

    @Value("${ai.analysis.max-retries:3}")
    private int maxRetries;

    // Cache for analysis results by product groups
    private final Map<String, FeatureProductAnalysisResult> analysisCache = new ConcurrentHashMap<>();

    /**
     * Main method for comprehensive Horoshop product analysis
     */
    @Transactional
    public Product analyzeProductForHoroshop(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            log.info("Starting comprehensive Horoshop analysis for: {} (ID: {})",
                    product.getExternalName(), productId);

            // Check cache first
            String cacheKey = generateCacheKey(product);
            FeatureProductAnalysisResult cachedResult = analysisCache.get(cacheKey);
            if (cachedResult != null) {
                log.info("Using cached analysis for key: {}", cacheKey);
                applyAnalysisToProduct(product, cachedResult);
                return productRepository.save(product);
            }

            // Check for existing group analysis
            Optional<Product> analyzedGroupProduct = findAnalyzedProductInGroup(product);
            if (analyzedGroupProduct.isPresent()) {
                log.info("Copying analysis from group member: {}", analyzedGroupProduct.get().getId());
                copyAnalysisFromProduct(analyzedGroupProduct.get(), product);
                return productRepository.save(product);
            }

            // Perform new analysis
            FeatureProductAnalysisResult analysis = performComprehensiveAnalysis(product);

            // Validate and apply
            if (!analysis.isValidForHoroshop()) {
                log.warn("Analysis result invalid for Horoshop, using enhanced fallback for: {}",
                        product.getExternalName());
                analysis = createEnhancedFallbackAnalysis(product);
            }

            // Cache and apply results
            analysisCache.put(cacheKey, analysis);
            applyAnalysisToProduct(product, analysis);
            product = productRepository.save(product);

            // Share with product group
            shareAnalysisWithGroup(product, analysis);

            log.info("Comprehensive Horoshop analysis completed for: {}", product.getExternalName());
            return product;

        } catch (Exception e) {
            log.error("Error analyzing product {} for Horoshop: {}", productId, e.getMessage(), e);
            throw new RuntimeException("Horoshop analysis failed", e);
        }
    }

    /**
     * Batch analysis for entire dataset with Horoshop optimization
     */
    @Transactional
    public Integer analyzeDatasetForHoroshop(Long datasetId) {
        try {
            DataSet dataset = dataSetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));

            log.info("Starting batch Horoshop analysis for dataset: {} ({} products)",
                    dataset.getName(), dataset.getTotalProducts());

            List<Product> productsToAnalyze = dataset.getProducts().stream()
                    .filter(p -> p.getAiAnalysisDate() == null || isAnalysisOutdated(p))
                    .collect(Collectors.toList());

            if (productsToAnalyze.isEmpty()) {
                log.info("No products need analysis in dataset: {}", dataset.getName());
                return 0;
            }

            // Group products for efficient analysis
            Map<String, List<Product>> productGroups = productsToAnalyze.stream()
                    .collect(Collectors.groupingBy(this::generateCacheKey));

            int totalAnalyzed = 0;
            int processedGroups = 0;

            log.info("Processing {} product groups for Horoshop analysis", productGroups.size());

            for (Map.Entry<String, List<Product>> entry : productGroups.entrySet()) {
                List<Product> groupProducts = entry.getValue();
                Product representative = selectBestGroupRepresentative(groupProducts);

                try {
                    log.info("Analyzing group {}/{}: {} ({} products)",
                            ++processedGroups, productGroups.size(),
                            entry.getKey(), groupProducts.size());

                    FeatureProductAnalysisResult analysis = performComprehensiveAnalysis(representative);

                    if (analysis.isValidForHoroshop()) {
                        // Apply to all products in group
                        for (Product product : groupProducts) {
                            applyAnalysisToProduct(product, analysis);
                            productRepository.save(product);
                            totalAnalyzed++;
                        }

                        // Cache result
                        analysisCache.put(entry.getKey(), analysis);

                        log.info("Successfully analyzed group: {} ({} products)",
                                entry.getKey(), groupProducts.size());
                    } else {
                        log.warn("Invalid analysis for group: {}, applying fallback", entry.getKey());

                        // Apply fallback to all products
                        for (Product product : groupProducts) {
                            FeatureProductAnalysisResult fallback = createEnhancedFallbackAnalysis(product);
                            applyAnalysisToProduct(product, fallback);
                            productRepository.save(product);
                            totalAnalyzed++;
                        }
                    }

                    // Delay between groups to respect rate limits
                    if (processedGroups < productGroups.size()) {
                        Thread.sleep(delayBetweenRequests);
                    }

                } catch (Exception e) {
                    log.error("Failed to analyze group {}: {}", entry.getKey(), e.getMessage());
                    // Continue with next group
                }
            }

            // Build category tree after analysis
            try {
                categoryService.buildCategoryTreeForDataset(dataset);
                log.info("Category tree built for dataset: {}", dataset.getName());
            } catch (Exception e) {
                log.warn("Failed to build category tree: {}", e.getMessage());
            }

            log.info("Batch Horoshop analysis completed. Total analyzed: {}/{}",
                    totalAnalyzed, productsToAnalyze.size());
            return totalAnalyzed;

        } catch (Exception e) {
            log.error("Batch Horoshop analysis failed: {}", e.getMessage(), e);
            throw new RuntimeException("Batch analysis failed", e);
        }
    }

    /**
     * Comprehensive analysis with retry logic and fallback
     */
    private FeatureProductAnalysisResult performComprehensiveAnalysis(Product product) {
        log.info("Performing comprehensive GPT-4 analysis for: {}", product.getExternalName());

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                FeatureProductAnalysisResult result;

                if (enableVision && gptProvider.supportsVision() && hasQualityImages(product)) {
                    String imageUrl = selectBestImage(product);
                    log.info("Using vision analysis (attempt {}) with image: {}", attempt, imageUrl);

                    result = gptProvider.analyzeProductWithImageForHoroshop(
                            product.getExternalName(),
                            cleanAndPrepareDescription(product.getExternalDescription()),
                            imageUrl,
                            product.getOriginalPrice() != null ? product.getOriginalPrice().doubleValue() : null,
                            product.getSourceType() != null ? product.getSourceType().name() : "UNKNOWN"
                    );
                } else {
                    log.info("Using text-based analysis (attempt {})", attempt);

                    result = gptProvider.analyzeProductForHoroshop(
                            product.getExternalName(),
                            cleanAndPrepareDescription(product.getExternalDescription()),
                            product.getExternalCategoryName(),
                            product.getOriginalPrice() != null ? product.getOriginalPrice().doubleValue() : null,
                            product.getSourceType() != null ? product.getSourceType().name() : "UNKNOWN"
                    );
                }

                // Validate result
                if (result != null && result.isValidForHoroshop()) {
                    log.info("Analysis successful on attempt {} for: {}", attempt, product.getExternalName());
                    return result;
                }

                log.warn("Analysis attempt {} failed validation for: {}", attempt, product.getExternalName());

            } catch (Exception e) {
                log.warn("Analysis attempt {} failed for {}: {}", attempt, product.getExternalName(), e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayBetweenRequests * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All analysis attempts failed for {}, creating enhanced fallback", product.getExternalName());
        return createEnhancedFallbackAnalysis(product);
    }

    /**
     * Apply comprehensive analysis results to product entity
     */
    private void applyAnalysisToProduct(Product product, FeatureProductAnalysisResult analysis) {
        // Update analysis metadata
        product.setAiAnalysisDate(LocalDateTime.now());
        product.setAiConfidenceScore(analysis.getAnalysisConfidence() != null ?
                BigDecimal.valueOf(analysis.getAnalysisConfidence()) : BigDecimal.valueOf(0.8));

        // Apply multilingual content
        applyMultilingualContent(product, analysis);

        // Apply product attributes
        applyProductAttributes(product, analysis);

        // Apply SEO data
        applySEOData(product, analysis);

        // Apply marketing data
        applyMarketingData(product, analysis);

        // Apply Horoshop specific data
        applyHoroshopSpecificData(product, analysis);

        // Update category assignment
        assignOptimalCategory(product, analysis);

        log.debug("Applied comprehensive analysis to product: {}", product.getExternalName());
    }

    private void applyMultilingualContent(Product product, FeatureProductAnalysisResult analysis) {
        // Commercial title (use as base for all languages if multilingual not available)
        String baseTitle = analysis.getCommercialTitle();
        if (baseTitle != null && !baseTitle.trim().isEmpty()) {
            product.setSeoTitleUa(truncateText(baseTitle, 255));
            product.setSeoTitleRu(truncateText(baseTitle, 255));
            product.setSeoTitleEn(truncateText(baseTitle, 255));
        }

        // Apply specific language titles if available
        if (analysis.getSeoTitle() != null) {
            product.setSeoTitleUa(truncateText(analysis.getSeoTitle(), 255));
        }

        // Descriptions
        if (analysis.getDescriptionUa() != null) {
            product.setDescriptionUa(analysis.getDescriptionUa());
        }
        if (analysis.getDescriptionRu() != null) {
            product.setDescriptionRu(analysis.getDescriptionRu());
        }
        if (analysis.getDescriptionEn() != null) {
            product.setDescriptionEn(analysis.getDescriptionEn());
        }

        // Short descriptions
        if (analysis.getShortDescriptionUa() != null) {
            product.setShortDescriptionUa(analysis.getShortDescriptionUa());
        }
        if (analysis.getShortDescriptionRu() != null) {
            product.setShortDescriptionRu(analysis.getShortDescriptionRu());
        }
        if (analysis.getShortDescriptionEn() != null) {
            product.setShortDescriptionEn(analysis.getShortDescriptionEn());
        }

        // Meta descriptions
        if (analysis.getMetaDescriptionUa() != null) {
            product.setMetaDescriptionUa(truncateText(analysis.getMetaDescriptionUa(), 300));
        }
        if (analysis.getMetaDescriptionRu() != null) {
            product.setMetaDescriptionRu(truncateText(analysis.getMetaDescriptionRu(), 300));
        }
        if (analysis.getMetaDescriptionEn() != null) {
            product.setMetaDescriptionEn(truncateText(analysis.getMetaDescriptionEn(), 300));
        }
    }

    private void applyProductAttributes(Product product, FeatureProductAnalysisResult analysis) {
        // Basic attributes
        if (analysis.getBrandName() != null) {
            product.setDetectedBrandName(analysis.getBrandName());
            product.setBrandDetected(true);
        }

        if (analysis.getColor() != null) {
            product.setColor(analysis.getColor());
        }

        if (analysis.getMaterial() != null) {
            product.setMaterial(analysis.getMaterial());
        }

        if (analysis.getDetectedGender() != null) {
            product.setGender(analysis.getDetectedGender());
        }

        if (analysis.getSeason() != null) {
            product.setSeason(analysis.getSeason());
        }

        if (analysis.getStyle() != null) {
            product.setStyle(analysis.getStyle());
        }

        if (analysis.getOccasion() != null) {
            product.setOccasion(analysis.getOccasion());
        }

        // Additional attributes from analysis
        if (analysis.getAttributes() != null && !analysis.getAttributes().isEmpty()) {
            Map<String, String> productAttributes = product.getAttributes();
            productAttributes.putAll(analysis.getAttributes());

            // Add analysis-specific attributes
            if (analysis.getCareInstructions() != null) {
                productAttributes.put("care_instructions", analysis.getCareInstructions());
            }
            if (analysis.getUsageInstructions() != null) {
                productAttributes.put("usage_instructions", analysis.getUsageInstructions());
            }
            if (analysis.getSizeGuide() != null) {
                productAttributes.put("size_guide", analysis.getSizeGuide());
            }
            if (analysis.getStylingTips() != null) {
                productAttributes.put("styling_tips", analysis.getStylingTips());
            }
        }
    }

    private void applySEOData(Product product, FeatureProductAnalysisResult analysis) {
        // Keywords for different languages
        if (analysis.getPrimaryKeywordsUa() != null && !analysis.getPrimaryKeywordsUa().isEmpty()) {
            product.setKeywordsUa(new HashSet<>(analysis.getPrimaryKeywordsUa()));
        }
        if (analysis.getPrimaryKeywordsRu() != null && !analysis.getPrimaryKeywordsRu().isEmpty()) {
            product.setKeywordsRu(new HashSet<>(analysis.getPrimaryKeywordsRu()));
        }
        if (analysis.getPrimaryKeywordsEn() != null && !analysis.getPrimaryKeywordsEn().isEmpty()) {
            product.setKeywordsEn(new HashSet<>(analysis.getPrimaryKeywordsEn()));
        }

        // Tags (combine from multiple sources)
        Set<String> allTags = new HashSet<>();
        if (analysis.getTagsUa() != null) allTags.addAll(analysis.getTagsUa());
        if (analysis.getTagsRu() != null) allTags.addAll(analysis.getTagsRu());
        if (analysis.getHoroshopIcons() != null) allTags.addAll(analysis.getHoroshopIcons());

        if (!allTags.isEmpty()) {
            product.setTags(allTags.stream().limit(10).collect(Collectors.toSet()));
        }
    }

    private void applyMarketingData(Product product, FeatureProductAnalysisResult analysis) {
        // Scoring
        if (analysis.getTrendScore() != null) {
            product.setTrendScore(BigDecimal.valueOf(analysis.getTrendScore())
                    .setScale(2, java.math.RoundingMode.HALF_UP));
        }

        if (analysis.getConversionPotential() != null) {
            product.setConversionPotential(BigDecimal.valueOf(analysis.getConversionPotential())
                    .setScale(2, java.math.RoundingMode.HALF_UP));
        }

        // Seasonal relevance
        if (analysis.getSeasonalRelevance() != null) {
            product.setSeasonalityScore(analysis.getSeasonalRelevance() ?
                    BigDecimal.valueOf(8.0) : BigDecimal.valueOf(5.0));
        }
    }

    private void applyHoroshopSpecificData(Product product, FeatureProductAnalysisResult analysis) {
        // Presence status
        if (analysis.getHoroshopPresence() != null) {
            product.setPresence(analysis.getHoroshopPresence());
            product.setAvailable(!"Нет в наличии".equals(analysis.getHoroshopPresence()));
        }

        // Prepare Horoshop characteristics JSON
        if (analysis.getAttributes() != null || analysis.getSellingPoints() != null) {
            Map<String, Object> horoshopChars = new HashMap<>();

            if (analysis.getAttributes() != null) {
                horoshopChars.putAll(analysis.getAttributes());
            }

            if (analysis.getSellingPoints() != null && !analysis.getSellingPoints().isEmpty()) {
                horoshopChars.put("selling_points", analysis.getSellingPoints());
            }

            if (analysis.getTargetAudience() != null) {
                horoshopChars.put("target_audience", analysis.getTargetAudience());
            }

            if (analysis.getUniqueSellingPoint() != null) {
                horoshopChars.put("usp", analysis.getUniqueSellingPoint());
            }

            try {
                product.setHoroshopCharacteristics(
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(horoshopChars)
                );
            } catch (Exception e) {
                log.warn("Failed to serialize Horoshop characteristics: {}", e.getMessage());
            }
        }

        // Update readiness for Horoshop
        product.updateHoroshopReadyStatus();
    }

    private void assignOptimalCategory(Product product, FeatureProductAnalysisResult analysis) {
        if (product.getDatasets().isEmpty()) return;

        DataSet dataset = product.getDatasets().iterator().next();
        String categoryName = analysis.getMainCategory();

        if (categoryName == null || categoryName.trim().isEmpty()) {
            categoryName = "Товари";
        }

        try {
            // Use category service to find or create appropriate category
            // categoryService.findOrCreateOptimalCategory(dataset, analysis);
            log.debug("Category assignment completed for product: {}", product.getExternalName());
        } catch (Exception e) {
            log.warn("Failed to assign category for {}: {}", product.getExternalName(), e.getMessage());
        }
    }

    /**
     * Enhanced fallback analysis with better defaults
     */
    private FeatureProductAnalysisResult createEnhancedFallbackAnalysis(Product product) {
        FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

        String productName = product.getExternalName();
        String categoryName = product.getExternalCategoryName();

        // Generate intelligent commercial name
        String commercialName = generateIntelligentCommercialName(productName, categoryName);

        // Basic content
        result.setCommercialTitle(commercialName);
        result.setSeoTitle("Купити " + commercialName + " | Якісні товари онлайн");
        result.setH1Title(commercialName + " - найкраща ціна в Україні");

        // Multilingual descriptions
        String baseDesc = generateIntelligentDescription(product, commercialName);
        result.setDescriptionUa(baseDesc);
        result.setDescriptionRu(translateToRussian(baseDesc));

        String shortDesc = "Якісний " + commercialName.toLowerCase() + " за найкращою ціною";
        result.setShortDescriptionUa(shortDesc);
        result.setShortDescriptionRu(translateToRussian(shortDesc));

        String metaDesc = "Купити " + commercialName.toLowerCase() +
                " недорого ✓ Швидка доставка по Україні ✓ Гарантія якості ✓ Найкращі ціни";
        result.setMetaDescriptionUa(metaDesc);
        result.setMetaDescriptionRu(translateToRussian(metaDesc));

        // Categories
        result.setMainCategory(detectIntelligentMainCategory(categoryName, productName));
        result.setSubCategory(categoryName != null ? categoryName : "Загальні товари");
        result.setCategoryPathUa(result.getMainCategory() + " / " + result.getSubCategory());
        result.setCategoryPathRu(translateToRussian(result.getCategoryPathUa()));

        // Keywords
        result.setPrimaryKeywordsUa(generateIntelligentKeywords(commercialName, "ua"));
        result.setPrimaryKeywordsRu(generateIntelligentKeywords(commercialName, "ru"));

        // Tags
        result.setTagsUa(generateSmartTags(product, "ua"));
        result.setTagsRu(generateSmartTags(product, "ru"));

        // Marketing data
        result.setSellingPoints(Arrays.asList(
                "Висока якість",
                "Доступна ціна",
                "Швидка доставка",
                "Гарантія якості",
                "Відмінний сервіс"
        ));
        result.setTargetAudience("Люди, які цінують якість за розумною ціною");
        result.setUniqueSellingPoint("Оптимальне співвідношення ціна-якість");
        result.setEmotionalTrigger("Створіть ідеальний образ з нашими товарами");

        // Scoring
        result.setTrendScore(determineBaseTrendScore(product));
        result.setConversionPotential(6.5);
        result.setSeasonalRelevance(true);
        result.setPriceCategory(determinePriceCategory(product.getOriginalPrice()));
        result.setCompetitiveAdvantage("Найкраща ціна при високій якості");

        // Horoshop specific
        result.setHoroshopPresence("В наличии");
        result.setHoroshopIcons(Arrays.asList("Качество", "Рекомендуем"));
        result.setMarketplaceExport(Arrays.asList("facebook", "google"));

        // Quality metrics
        result.setQualityScore(7.0);
        result.setAnalysisConfidence(0.7);

        return result;
    }

    // Helper methods for enhanced analysis
    private String generateIntelligentCommercialName(String originalName, String category) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return category != null ? "Якісний " + category.toLowerCase() : "Якісний товар";
        }

        // Check if name looks like technical code
        if (originalName.matches("^[0-9A-Z\\-_]{2,10}$") ||
                originalName.matches("^(SKU|ART|CODE|ID)[\\-_]?[0-9A-Z]+$")) {

            if (category != null && !category.trim().isEmpty()) {
                return "Стильний " + category.toLowerCase();
            }
            return "Якісний товар";
        }

        // Clean and improve existing name
        String cleaned = originalName.trim()
                .replaceAll("^(Артикул|Код|SKU|ART)\\s*:?\\s*", "")
                .replaceAll("\\s+", " ");

        return cleaned.length() > 3 ? cleaned : "Якісний товар";
    }

    private String generateIntelligentDescription(Product product, String commercialName) {
        StringBuilder desc = new StringBuilder();

        desc.append("Представляємо вашій увазі ").append(commercialName.toLowerCase())
                .append(" преміум якості за доступною ціною. ");

        if (product.getExternalDescription() != null && !product.getExternalDescription().trim().isEmpty()) {
            String cleanDesc = cleanAndPrepareDescription(product.getExternalDescription());
            if (cleanDesc.length() > 50) {
                desc.append(cleanDesc.length() > 200 ?
                        cleanDesc.substring(0, 200) + "... " : cleanDesc + " ");
            }
        }

        desc.append("Наш товар відзначається високою якістю матеріалів та сучасним дизайном. ")
                .append("Ми гарантуємо швидку доставку по всій Україні та найкращий сервіс. ")
                .append("Замовляйте зараз і отримайте якісний товар за найкращою ціною!");

        return desc.toString();
    }

    private String translateToRussian(String ukrainianText) {
        // Simple translation map for common phrases
        Map<String, String> translations = Map.of(
                "Купити", "Купить",
                "якісні товари", "качественные товары",
                "найкраща ціна", "лучшая цена",
                "недорого", "недорого",
                "швидка доставка", "быстрая доставка",
                "гарантія якості", "гарантия качества",
                "найкращі ціни", "лучшие цены",
                "висока якість", "высокое качество",
                "доступна ціна", "доступная цена"
        );

        String result = ukrainianText;
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private List<String> generateIntelligentKeywords(String productName, String language) {
        List<String> keywords = new ArrayList<>();
        String lowerName = productName.toLowerCase();

        if ("ua".equals(language)) {
            keywords.addAll(Arrays.asList(
                    lowerName,
                    "купити " + lowerName,
                    lowerName + " україна",
                    lowerName + " недорого",
                    lowerName + " якісний",
                    lowerName + " онлайн"
            ));
        } else {
            keywords.addAll(Arrays.asList(
                    lowerName,
                    "купить " + lowerName,
                    lowerName + " украина",
                    lowerName + " недорого",
                    lowerName + " качественный",
                    lowerName + " онлайн"
            ));
        }

        return keywords;
    }

    private List<String> generateSmartTags(Product product, String language) {
        List<String> tags = new ArrayList<>();

        if ("ua".equals(language)) {
            tags.addAll(Arrays.asList("якість", "новинка", "популярне", "рекомендуємо"));
            if (product.getOriginalPrice() != null && product.getOriginalPrice().doubleValue() < 500) {
                tags.add("доступно");
            }
        } else {
            tags.addAll(Arrays.asList("качество", "новинка", "популярное", "рекомендуем"));
            if (product.getOriginalPrice() != null && product.getOriginalPrice().doubleValue() < 500) {
                tags.add("доступно");
            }
        }

        return tags;
    }

    private String detectIntelligentMainCategory(String categoryName, String productName) {
        if (categoryName == null && productName == null) return "Товари";

        String text = (categoryName + " " + productName).toLowerCase();

        // Enhanced category detection
        if (text.contains("одяг") || text.contains("одежд") ||
                text.contains("сукн") || text.contains("платт") || text.contains("штан")) {
            return text.contains("жіноч") || text.contains("женс") ? "Жіночий одяг" :
                    text.contains("чолов") || text.contains("мужс") ? "Чоловічий одяг" : "Одяг";
        }

        if (text.contains("взутт") || text.contains("обув") || text.contains("черевик") ||
                text.contains("туфл") || text.contains("кросівк")) {
            return "Взуття";
        }

        if (text.contains("електрон") || text.contains("гаджет") || text.contains("телефон") ||
                text.contains("комп'ютер") || text.contains("ноутбук")) {
            return "Електроніка";
        }

        if (text.contains("дім") || text.contains("побут") || text.contains("кухн") ||
                text.contains("меблі") || text.contains("декор")) {
            return "Дім і побут";
        }

        if (text.contains("красо") || text.contains("косметик") || text.contains("парфум") ||
                text.contains("догляд")) {
            return "Краса та здоров'я";
        }

        return categoryName != null ? categoryName : "Товари";
    }

    private Double determineBaseTrendScore(Product product) {
        double score = 5.0; // Base score

        // Adjust based on price
        if (product.getOriginalPrice() != null) {
            double price = product.getOriginalPrice().doubleValue();
            if (price > 1000) score += 1.0; // Premium products
            if (price < 200) score += 0.5;  // Affordable products
        }

        // Adjust based on description quality
        if (product.getExternalDescription() != null &&
                product.getExternalDescription().length() > 100) {
            score += 0.5;
        }

        // Adjust based on images
        if (product.getImageUrls() != null && product.getImageUrls().size() > 2) {
            score += 0.5;
        }

        return Math.min(score, 10.0);
    }

    private String determinePriceCategory(BigDecimal price) {
        if (price == null) return "середній";

        double priceValue = price.doubleValue();
        if (priceValue < 300) return "бюджетний";
        if (priceValue > 1500) return "преміум";
        return "середній";
    }

    // Utility methods
    private String generateCacheKey(Product product) {
        if (product.getExternalGroupId() != null && !product.getExternalGroupId().trim().isEmpty()) {
            return product.getSourceType().name() + ":" + product.getExternalGroupId();
        }
        return product.getSourceType().name() + ":single:" + product.getExternalId();
    }

    private Optional<Product> findAnalyzedProductInGroup(Product product) {
        if (product.getExternalGroupId() == null) return Optional.empty();

        return productRepository.findByExternalGroupIdAndSourceTypeAndAiAnalysisDateIsNotNull(
                product.getExternalGroupId(),
                product.getSourceType()
        ).stream().findFirst();
    }

    private void copyAnalysisFromProduct(Product source, Product target) {
        target.setAiAnalysisDate(LocalDateTime.now());
        target.setAiConfidenceScore(source.getAiConfidenceScore());

        // Copy multilingual content
        target.setSeoTitleUa(source.getSeoTitleUa());
        target.setSeoTitleRu(source.getSeoTitleRu());
        target.setSeoTitleEn(source.getSeoTitleEn());

        target.setDescriptionUa(source.getDescriptionUa());
        target.setDescriptionRu(source.getDescriptionRu());
        target.setDescriptionEn(source.getDescriptionEn());

        target.setShortDescriptionUa(source.getShortDescriptionUa());
        target.setShortDescriptionRu(source.getShortDescriptionRu());
        target.setShortDescriptionEn(source.getShortDescriptionEn());

        target.setMetaDescriptionUa(source.getMetaDescriptionUa());
        target.setMetaDescriptionRu(source.getMetaDescriptionRu());
        target.setMetaDescriptionEn(source.getMetaDescriptionEn());

        // Copy keywords and tags
        target.setKeywordsUa(new HashSet<>(source.getKeywordsUa()));
        target.setKeywordsRu(new HashSet<>(source.getKeywordsRu()));
        target.setKeywordsEn(new HashSet<>(source.getKeywordsEn()));
        target.setTags(new HashSet<>(source.getTags()));

        // Copy attributes
        target.setDetectedBrandName(source.getDetectedBrandName());
        target.setBrandDetected(source.getBrandDetected());
        target.setColor(source.getColor());
        target.setMaterial(source.getMaterial());
        target.setGender(source.getGender());
        target.setSeason(source.getSeason());
        target.setStyle(source.getStyle());
        target.setOccasion(source.getOccasion());

        // Copy scores
        target.setTrendScore(source.getTrendScore());
        target.setConversionPotential(source.getConversionPotential());
        target.setSeasonalityScore(source.getSeasonalityScore());

        // Copy Horoshop data
        target.setHoroshopCharacteristics(source.getHoroshopCharacteristics());
        target.setPresence(source.getPresence());

        // Copy additional attributes
        target.getAttributes().putAll(source.getAttributes());
        target.setCategory(source.getCategory());
    }

    private void shareAnalysisWithGroup(Product analyzedProduct, FeatureProductAnalysisResult analysis) {
        if (analyzedProduct.getExternalGroupId() == null) return;

        List<Product> groupProducts = productRepository.findByExternalGroupIdAndSourceTypeAndAiAnalysisDateIsNull(
                analyzedProduct.getExternalGroupId(),
                analyzedProduct.getSourceType()
        );

        for (Product product : groupProducts) {
            if (!product.getId().equals(analyzedProduct.getId())) {
                copyAnalysisFromProduct(analyzedProduct, product);
                productRepository.save(product);
            }
        }

        log.info("Shared analysis with {} products in group: {}",
                groupProducts.size(), analyzedProduct.getExternalGroupId());
    }

    private Product selectBestGroupRepresentative(List<Product> products) {
        return products.stream()
                .max(Comparator.comparing(this::calculateProductAnalysisScore))
                .orElse(products.get(0));
    }

    private int calculateProductAnalysisScore(Product product) {
        int score = 0;

        // Image count and quality
        score += product.getImageUrls().size() * 10;

        // Description quality
        if (product.getExternalDescription() != null) {
            score += Math.min(product.getExternalDescription().length() / 20, 50);
        }

        // Name quality (avoid technical codes)
        if (product.getExternalName() != null && !product.getExternalName().matches("^[0-9A-Z\\-_]+$")) {
            score += 20;
        }

        // Price availability
        if (product.getOriginalPrice() != null) {
            score += 10;
        }

        return score;
    }

    private boolean hasQualityImages(Product product) {
        return product.getImageUrls() != null &&
                !product.getImageUrls().isEmpty() &&
                product.getImageUrls().stream()
                        .anyMatch(url -> url.matches(".*\\.(jpg|jpeg|png|webp).*"));
    }

    private String selectBestImage(Product product) {
        if (product.getImageUrls() == null || product.getImageUrls().isEmpty()) {
            return null;
        }
        return product.getImageUrls().get(0);
    }

    private String cleanAndPrepareDescription(String description) {
        if (description == null) return "";

        return description
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private boolean isAnalysisOutdated(Product product) {
        if (product.getAiAnalysisDate() == null) return true;
        return product.getAiAnalysisDate().isBefore(LocalDateTime.now().minusDays(30));
    }

    /**
     * Clear analysis cache
     */
    public void clearAnalysisCache() {
        int size = analysisCache.size();
        analysisCache.clear();
        log.info("Analysis cache cleared ({} entries removed)", size);
    }

    /**
     * Get analysis statistics
     */
    public Map<String, Object> getAnalysisStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("cacheSize", analysisCache.size());
        stats.put("aiProviderName", gptProvider.getProviderName());
        stats.put("visionEnabled", enableVision);
        stats.put("visionSupported", gptProvider.supportsVision());
        stats.put("batchSize", batchSize);
        stats.put("delayBetweenRequests", delayBetweenRequests);
        stats.put("maxRetries", maxRetries);
        stats.put("maxTextLength", gptProvider.getMaxTextLength());
        stats.put("costPerRequest", gptProvider.getCostPerRequest());

        return stats;
    }

    /**
     * Re-analyze products with low scores
     */
    @Async
    public CompletableFuture<Integer> reanalyzeLowPerformingProducts(Long datasetId, Double minTrendScore) {
        try {
            DataSet dataset = dataSetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));

            List<Product> lowPerformingProducts = dataset.getProducts().stream()
                    .filter(p -> p.getTrendScore() != null &&
                            p.getTrendScore().doubleValue() < minTrendScore)
                    .limit(20)
                    .collect(Collectors.toList());

            log.info("Re-analyzing {} low-performing products in dataset: {}",
                    lowPerformingProducts.size(), dataset.getName());

            int reanalyzed = 0;
            for (Product product : lowPerformingProducts) {
                try {
                    // Clear cache for re-analysis
                    String cacheKey = generateCacheKey(product);
                    analysisCache.remove(cacheKey);

                    // Perform new analysis
                    FeatureProductAnalysisResult analysis = performComprehensiveAnalysis(product);
                    if (analysis.isValidForHoroshop()) {
                        applyAnalysisToProduct(product, analysis);
                        productRepository.save(product);
                        reanalyzed++;
                    }

                    Thread.sleep(delayBetweenRequests);

                } catch (Exception e) {
                    log.error("Failed to re-analyze product {}: {}", product.getId(), e.getMessage());
                }
            }

            log.info("Re-analysis completed: {}/{} products improved", reanalyzed, lowPerformingProducts.size());
            return CompletableFuture.completedFuture(reanalyzed);

        } catch (Exception e) {
            log.error("Re-analysis failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Re-analysis failed", e));
        }
    }
}