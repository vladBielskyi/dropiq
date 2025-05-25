package com.dropiq.engine.product.service;

import com.dropiq.engine.integration.ai.OllamaClient;
import com.dropiq.engine.integration.ai.model.ProductAnalysisResult;
import com.dropiq.engine.product.entity.DatasetCategory;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AIProductAnalysisService {

    private final OllamaClient ollamaClient;
    private final ProductRepository productRepository;
    private final DynamicCategoryService categoryService;

    /**
     * CORE METHOD: Analyze product with group sharing logic
     */
    @Async
    public CompletableFuture<Product> analyzeProduct(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            log.info("Starting AI analysis for product: {} (Group: {}, Source: {})",
                    product.getName(), product.getGroupId(), product.getSourceType());

            // STEP 1: Check if analysis already exists for this group+source
            Optional<Product> existingAnalyzedProduct = findExistingAnalysis(product);

            if (existingAnalyzedProduct.isPresent()) {
                log.info("Found existing AI analysis for group {}, copying to product {}",
                        product.getAiAnalysisKey(), product.getId());
                copyAiAnalysis(existingAnalyzedProduct.get(), product);
                return CompletableFuture.completedFuture(productRepository.save(product));
            }

            // STEP 2: No existing analysis - perform new AI analysis
            ProductAnalysisResult analysis = performAiAnalysis(product);

            // STEP 3: Find or create category
            DatasetCategory category = categoryService.findOrCreateCategory(
                    product.getDatasets().iterator().next(),
                    analysis.getCategoryUk(),
                    analysis.getCategoryRu(),
                    analysis.getCategoryEn(),
                    analysis.getSubcategoryUk(),
                    analysis.getSubcategoryRu(),
                    analysis.getSubcategoryEn()
            );

            // STEP 4: Apply analysis to product
            updateProductWithAnalysis(product, analysis, category);
            product = productRepository.save(product);

            // STEP 5: Share analysis with other products in same group+source
            shareAnalysisWithGroup(product);

            log.info("AI analysis completed and shared for product group: {}", product.getAiAnalysisKey());
            return CompletableFuture.completedFuture(product);

        } catch (Exception e) {
            log.error("Error analyzing product {}: {}", productId, e.getMessage(), e);
            throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Find existing AI analysis for same group+source
     */
    private Optional<Product> findExistingAnalysis(Product product) {
        if (product.getGroupId() == null || product.getGroupId().trim().isEmpty()) {
            return Optional.empty(); // No group sharing for products without groupId
        }

        return productRepository.findByGroupIdAndSourceTypeAndAiAnalyzedTrue(
                product.getGroupId(),
                product.getSourceType()
        ).stream().findFirst();
    }

    /**
     * Perform actual AI analysis
     */
    private ProductAnalysisResult performAiAnalysis(Product product) {
        ProductAnalysisResult visionResult = null;

        // Step 1: Vision analysis if images available
        if (!product.getImageUrls().isEmpty()) {
            visionResult = ollamaClient.analyzeProductImage(
                    product.getImageUrls().get(0),
                    createVisionPrompt()
            );
        }

        // Step 2: Text analysis for multilingual content
        ProductAnalysisResult textResult = ollamaClient.generateMultilingualContent(
                createTextPrompt(product, visionResult)
        );

        // Combine results
        if (visionResult != null) {
            textResult.setMainFeatures(visionResult.getMainFeatures());
            textResult.setColors(visionResult.getColors());
            textResult.setStyle(visionResult.getStyle());
            textResult.setVisualQuality(visionResult.getVisualQuality());
        }

        return textResult;
    }

    /**
     * Copy AI analysis from one product to another
     */
    private void copyAiAnalysis(Product source, Product target) {
        target.setAiAnalyzed(true);
        target.setAiAnalysisDate(LocalDateTime.now());
        target.setAiConfidenceScore(source.getAiConfidenceScore());

        // Copy category
        target.setCategory(source.getCategory());

        // Copy multilingual SEO content
        target.setSeoTitleUk(source.getSeoTitleUk());
        target.setSeoTitleRu(source.getSeoTitleRu());
        target.setSeoTitleEn(source.getSeoTitleEn());

        target.setDescriptionUk(source.getDescriptionUk());
        target.setDescriptionRu(source.getDescriptionRu());
        target.setDescriptionEn(source.getDescriptionEn());

        target.setMetaDescriptionUk(source.getMetaDescriptionUk());
        target.setMetaDescriptionRu(source.getMetaDescriptionRu());
        target.setMetaDescriptionEn(source.getMetaDescriptionEn());

        // Copy tags
        target.setTagsUk(new HashSet<>(source.getTagsUk()));
        target.setTagsRu(new HashSet<>(source.getTagsRu()));
        target.setTagsEn(new HashSet<>(source.getTagsEn()));

        // Copy AI attributes
        target.setTrendScore(source.getTrendScore());
        target.setPredictedPriceRange(source.getPredictedPriceRange());
        target.setTargetAudienceUk(source.getTargetAudienceUk());
        target.setTargetAudienceRu(source.getTargetAudienceRu());
        target.setTargetAudienceEn(source.getTargetAudienceEn());
        target.setStyleTags(source.getStyleTags());
        target.setColorAnalysis(source.getColorAnalysis());
        target.setMainFeatures(source.getMainFeatures());
    }

    /**
     * Share analysis with other products in same group
     */
    private void shareAnalysisWithGroup(Product analyzedProduct) {
        if (analyzedProduct.getGroupId() == null) return;

        List<Product> groupProducts = productRepository.findByGroupIdAndSourceTypeAndAiAnalyzedFalse(
                analyzedProduct.getGroupId(),
                analyzedProduct.getSourceType()
        );

        for (Product product : groupProducts) {
            copyAiAnalysis(analyzedProduct, product);
            productRepository.save(product);
        }

        log.info("Shared AI analysis with {} products in group {}",
                groupProducts.size(), analyzedProduct.getAiAnalysisKey());
    }

    /**
     * Update product with fresh AI analysis
     */
    private void updateProductWithAnalysis(Product product, ProductAnalysisResult analysis, DatasetCategory category) {
        product.setAiAnalyzed(true);
        product.setAiAnalysisDate(LocalDateTime.now());
        product.setAiConfidenceScore(calculateConfidenceScore(analysis));

        // Set category
        product.setCategory(category);
        if (category != null) {
            category.addProduct(product);
        }

        // Set multilingual SEO content
        if (analysis.getSeoTitles() != null) {
            product.setSeoTitleUk(analysis.getSeoTitles().get("uk"));
            product.setSeoTitleRu(analysis.getSeoTitles().get("ru"));
            product.setSeoTitleEn(analysis.getSeoTitles().get("en"));
        }

        if (analysis.getDescriptions() != null) {
            product.setDescriptionUk(analysis.getDescriptions().get("uk"));
            product.setDescriptionRu(analysis.getDescriptions().get("ru"));
            product.setDescriptionEn(analysis.getDescriptions().get("en"));
        }

        if (analysis.getMetaDescriptions() != null) {
            product.setMetaDescriptionUk(analysis.getMetaDescriptions().get("uk"));
            product.setMetaDescriptionRu(analysis.getMetaDescriptions().get("ru"));
            product.setMetaDescriptionEn(analysis.getMetaDescriptions().get("en"));
        }

        if (analysis.getTags() != null) {
            product.setTagsUk(new HashSet<>(analysis.getTags().get("uk")));
            product.setTagsRu(new HashSet<>(analysis.getTags().get("ru")));
            product.setTagsEn(new HashSet<>(analysis.getTags().get("en")));
        }

        if (analysis.getTargetAudience() != null) {
            product.setTargetAudienceUk(analysis.getTargetAudience().get("uk"));
            product.setTargetAudienceRu(analysis.getTargetAudience().get("ru"));
            product.setTargetAudienceEn(analysis.getTargetAudience().get("en"));
        }

        // Set AI attributes
        product.setTrendScore(analysis.getTrendScore() != null ?
                BigDecimal.valueOf(analysis.getTrendScore()) : null);
        product.setPredictedPriceRange(analysis.getPredictedPriceRange());
        product.setStyleTags(analysis.getStyleTags());
        product.setMainFeatures(String.join(", ", analysis.getMainFeatures()));

        if (analysis.getColors() != null) {
            product.setColorAnalysis(String.join(", ", analysis.getColors()));
        }
    }

    private Double calculateConfidenceScore(ProductAnalysisResult analysis) {
        double score = 0.0;
        int factors = 0;

        if (analysis.getTrendScore() != null && analysis.getTrendScore() > 6) {
            score += 0.3;
        }
        if (analysis.getSeoTitles() != null && !analysis.getSeoTitles().isEmpty()) {
            score += 0.3;
        }
        if (analysis.getMainFeatures() != null && !analysis.getMainFeatures().isEmpty()) {
            score += 0.4;
        }
        factors = 3;

        return factors > 0 ? (score / factors * 10) : 5.0;
    }

    private String createVisionPrompt() {
        return """
            Analyze this product image as a professional e-commerce expert. Focus on what customers see and buy.
            
            Return ONLY JSON:
            {
              "product_type": "specific product type (e.g. 'wireless headphones', 'leather handbag', 'running shoes')",
              "main_features": ["key feature 1", "key feature 2", "key feature 3"],
              "colors": ["primary color", "secondary color"],
              "style": "design style (modern/vintage/minimalist/luxury/sporty/casual/professional)",
              "visual_quality": 8.5,
              "brand_visible": true,
            }
            
            Focus on:
            - What makes this product visually appealing to buyers
            - Key features that customers notice first
            - Style that influences purchase decisions
            - Image quality for marketing potential
            """;
    }

    private String createTextPrompt(Product product, ProductAnalysisResult visionResult) {
        String baseInfo = String.format(
                "Product: %s\nOriginal Description: %s\nPrice: %.2f UAH\nOriginal Category: %s",
                product.getName(),
                product.getOriginalDescription() != null ? product.getOriginalDescription() : "No description",
                product.getOriginalPrice() != null ? product.getOriginalPrice() : 0.0,
                product.getExternalCategoryName() != null ? product.getExternalCategoryName() : "Unknown"
        );

        String visionInfo = "";
        if (visionResult != null) {
            visionInfo = String.format(
                    "\n\nVisual Analysis Results:\n- Product Type: %s\n- Style: %s\n- Colors: %s\n- Key Features: %s",
                    visionResult.getProductType(),
                    visionResult.getStyle(),
                    visionResult.getColors() != null ? String.join(", ", visionResult.getColors()) : "Unknown",
                    visionResult.getMainFeatures() != null ? String.join(", ", visionResult.getMainFeatures()) : "None identified"
            );
        }

        return String.format("""
            You are a world-class e-commerce copywriter and SEO specialist. Your job is to create high-converting product content that sells fast.
            
            Product Information: %s%s
            
            Create compelling, sales-focused content in JSON format:
            
            {
              "categories": {
                "main_uk": "чітка категорія українською (напр. 'Електроніка та гаджети')",
                "main_ru": "четкая категория на русском (напр. 'Электроника и гаджеты')",
                "main_en": "clear category in English (e.g. 'Electronics & Gadgets')",
                "sub_uk": "підкатегорія українською (напр. 'Бездротові навушники')",
                "sub_ru": "подкатегория на русском (напр. 'Беспроводные наушники')",
                "sub_en": "subcategory in English (e.g. 'Wireless Headphones')"
              },
              "seo_titles": {
                "uk": "Продаючий SEO заголовок українською з ключовими словами (50-60 символів)",
                "ru": "Продающий SEO заголовок на русском с ключевыми словами (50-60 символов)",
                "en": "High-converting SEO title in English with keywords (50-60 characters)"
              },
              "descriptions": {
                "uk": "Переконливий опис українською з фокусом на вигоди покупця, емоційні тригери та соціальні докази. Включи ключові слова природно. 150-200 слів.",
                "ru": "Убедительное описание на русском с фокусом на выгоды покупателя, эмоциональные триггеры и социальные доказательства. Включи ключевые слова естественно. 150-200 слов.",
                "en": "Compelling description in English focusing on customer benefits, emotional triggers, and social proof. Include keywords naturally. 150-200 words."
              },
              "meta_descriptions": {
                "uk": "Цепляючий мета-опис українською, що спонукає до кліку (150-160 символів)",
                "ru": "Цепляющее мета-описание на русском, побуждающее к клику (150-160 символов)",
                "en": "Click-worthy meta description in English that drives action (150-160 characters)"
              },
              "tags": {
                "uk": ["популярний тег", "ключове слово", "тренд", "вигода", "емоція"],
                "ru": ["популярный тег", "ключевое слово", "тренд", "выгода", "эмоция"],
                "en": ["popular tag", "keyword", "trend", "benefit", "emotion"]
              },
              "target_audiences": {
                "uk": "Детальний портрет цільової аудиторії українською з болями та потребами",
                "ru": "Детальный портрет целевой аудитории на русском с болями и потребностями", 
                "en": "Detailed target audience portrait in English with pain points and needs"
              },
              "trend_score": 8.5,
              "predicted_price_range": "mid-range",
              "style_tags": "trendy, modern, premium, must-have",
              "marketing_angles": "Ключові продаючі кути: зручність, якість, стиль, економія часу",
              "competitive_advantage": "Унікальна пропозиція цінності та переваги над конкурентами",
              "urgency_triggers": "Тригери терміновості: обмежена кількість, знижка, тренд"
            }
            
            CRITICAL REQUIREMENTS:
            1. Write to SELL, not just describe - focus on benefits over features
            2. Use emotional triggers and urgency in copy
            3. Include trending keywords that people actually search for
            4. Make titles and descriptions click-worthy and conversion-focused
            5. Consider current market trends and customer psychology
            6. Trend score should reflect 2025 market demand (1-10 scale)
            7. All content must be natural, engaging, and sales-oriented
            8. Target impulse buyers and gift purchasers where relevant
            """, baseInfo, visionInfo);
    }
}