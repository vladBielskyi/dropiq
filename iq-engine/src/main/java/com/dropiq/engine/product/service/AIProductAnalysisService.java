package com.dropiq.engine.product.service;

import com.dropiq.engine.integration.ai.OllamaClient;
import com.dropiq.engine.integration.ai.model.ProductAnalysisResult;
import com.dropiq.engine.product.entity.DatasetCategory;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.repository.DatasetCategoryRepository;
import com.dropiq.engine.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AIProductAnalysisService {

    private final OllamaClient ollamaClient;
    private final ProductRepository productRepository;
    private final DatasetCategoryRepository categoryRepository;
    private final DynamicCategoryService categoryService;

    // Maximum category depth and children limits
    private static final int MAX_CATEGORY_DEPTH = 3;
    private static final int MAX_CHILDREN_PER_CATEGORY = 8;
    private static final int MAX_CATEGORIES_PER_DATASET = 50;

    /**
     * Analyze single product with AI
     */
    @Async
    public CompletableFuture<Product> analyzeProduct(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            log.info("Starting AI analysis for product: {}", product.getName());

            // Step 1: Analyze product with vision model if has images
            ProductAnalysisResult visionResult = null;
            if (!product.getImageUrls().isEmpty()) {
                visionResult = ollamaClient.analyzeProductImage(
                        product.getImageUrls().get(0),
                        createVisionPrompt()
                );
            }

            // Step 2: Generate multilingual content with text model
            ProductAnalysisResult textResult = ollamaClient.generateMultilingualContent(
                    createTextPrompt(product, visionResult)
            );

            // Step 3: Find or create appropriate category
            DatasetCategory category = categoryService.findOrCreateCategory(
                    product.getDatasets().iterator().next(), // Get first dataset
                    textResult.getCategoryUk(),
                    textResult.getCategoryRu(),
                    textResult.getCategoryEn(),
                    textResult.getSubcategoryUk(),
                    textResult.getSubcategoryRu(),
                    textResult.getSubcategoryEn()
            );

            // Step 4: Update product with analysis results
            updateProductWithAnalysis(product, visionResult, textResult, category);

            product = productRepository.save(product);
            log.info("AI analysis completed for product: {}", product.getName());

            return CompletableFuture.completedFuture(product);

        } catch (Exception e) {
            log.error("Error analyzing product {}: {}", productId, e.getMessage(), e);
            throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze all products in dataset
     */
    @Async
    public CompletableFuture<DataSet> analyzeDatasetProducts(DataSet dataset) {
        log.info("Starting AI analysis for dataset: {} ({} products)",
                dataset.getName(), dataset.getProducts().size());

        List<CompletableFuture<Product>> analysisfutures = dataset.getProducts().stream()
                .filter(product -> !product.getAiAnalyzed())
                .map(product -> analyzeProduct(product.getId()))
                .collect(Collectors.toList());

        // Wait for all analyses to complete
        CompletableFuture<Void> allAnalyses = CompletableFuture.allOf(
                analysisfutures.toArray(new CompletableFuture[0])
        );

        return allAnalyses.thenApply(v -> {
            log.info("Completed AI analysis for dataset: {}", dataset.getName());
            return dataset;
        });
    }

    /**
     * Batch analyze products with rate limiting
     */
    @Async
    public CompletableFuture<List<Product>> batchAnalyzeProducts(List<Long> productIds, int batchSize) {
        List<Product> analyzedProducts = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, productIds.size());
            List<Long> batch = productIds.subList(i, endIndex);

            List<CompletableFuture<Product>> batchFutures = batch.stream()
                    .map(this::analyzeProduct)
                    .collect(Collectors.toList());

            // Wait for batch completion
            CompletableFuture<Void> batchComplete = CompletableFuture.allOf(
                    batchFutures.toArray(new CompletableFuture[0])
            );

            try {
                batchComplete.get();
                batchFutures.forEach(future -> {
                    try {
                        analyzedProducts.add(future.get());
                    } catch (Exception e) {
                        log.error("Error in batch analysis: {}", e.getMessage());
                    }
                });

                // Rate limiting - pause between batches
                if (endIndex < productIds.size()) {
                    Thread.sleep(2000); // 2 second pause between batches
                }
            } catch (Exception e) {
                log.error("Batch analysis error: {}", e.getMessage());
            }
        }

        return CompletableFuture.completedFuture(analyzedProducts);
    }

    private String createVisionPrompt() {
        return """
            Analyze this product image and extract the following information in JSON format:
            
            {
              "product_type": "specific product type in English",
              "main_category": "broad category like 'Electronics', 'Clothing', etc.",
              "subcategory": "more specific subcategory",
              "main_features": ["feature1", "feature2", "feature3"],
              "colors": ["primary_color", "secondary_color"],
              "style": "modern/vintage/classic/minimalist/etc",
              "target_audience": "target demographic",
              "price_range": "budget/mid-range/premium/luxury",
              "visual_quality": "product photo quality 1-10",
              "brand_visible": true/false,
              "background_type": "white/lifestyle/studio/other"
            }
            
            Focus only on what you can clearly see in the image.
            """;
    }

    private String createTextPrompt(Product product, ProductAnalysisResult visionResult) {
        String baseInfo = String.format(
                "Product: %s\nDescription: %s\nPrice: %.2f\nCategory: %s",
                product.getName(),
                product.getDescription(),
                product.getOriginalPrice(),
                product.getExternalCategoryName()
        );

        String visionInfo = "";
        if (visionResult != null) {
            visionInfo = String.format(
                    "\nVisual Analysis: Type: %s, Style: %s, Colors: %s, Features: %s",
                    visionResult.getProductType(),
                    visionResult.getStyle(),
                    String.join(", ", visionResult.getColors()),
                    String.join(", ", visionResult.getMainFeatures())
            );
        }

        return String.format("""
            Based on this product information: %s%s
            
            Create comprehensive e-commerce content in JSON format:
            
            {
              "categories": {
                "main_uk": "основна категорія українською",
                "main_ru": "основная категория на русском",
                "main_en": "main category in English",
                "sub_uk": "підкатегорія українською",
                "sub_ru": "подкатегория на русском", 
                "sub_en": "subcategory in English"
              },
              "seo_titles": {
                "uk": "SEO заголовок українською (50-60 символів)",
                "ru": "SEO заголовок на русском (50-60 символов)",
                "en": "SEO title in English (50-60 characters)"
              },
              "descriptions": {
                "uk": "Детальний опис товару українською (150-200 слів)",
                "ru": "Подробное описание товара на русском (150-200 слов)",
                "en": "Detailed product description in English (150-200 words)"
              },
              "meta_descriptions": {
                "uk": "Мета опис українською (150-160 символів)",
                "ru": "Мета описание на русском (150-160 символов)",
                "en": "Meta description in English (150-160 characters)"
              },
              "tags": {
                "uk": ["тег1", "тег2", "тег3", "тег4", "тег5"],
                "ru": ["тег1", "тег2", "тег3", "тег4", "тег5"],
                "en": ["tag1", "tag2", "tag3", "tag4", "tag5"]
              },
              "target_audience": {
                "uk": "цільова аудиторія українською",
                "ru": "целевая аудитория на русском",
                "en": "target audience in English"
              },
              "trend_score": 8,
              "predicted_price_range": "budget/mid-range/premium",
              "style_tags": "modern, minimalist, trendy",
              "main_features": "key feature 1, key feature 2, key feature 3"
            }
            
            Make all content SEO-optimized, engaging, and conversion-focused.
            """, baseInfo, visionInfo);
    }

    private void updateProductWithAnalysis(Product product, ProductAnalysisResult visionResult,
                                           ProductAnalysisResult textResult, DatasetCategory category) {
        // Set AI analysis metadata
        product.setAiAnalyzed(true);
        product.setAiAnalysisDate(LocalDateTime.now());
        product.setAiConfidenceScore(calculateConfidenceScore(visionResult, textResult));

        // Set category
        product.setCategory(category);
        category.addProduct(product);

        // Set multilingual SEO content
        if (textResult.getSeoTitles() != null) {
            product.setSeoTitleUk(textResult.getSeoTitles().get("uk"));
            product.setSeoTitleRu(textResult.getSeoTitles().get("ru"));
            product.setSeoTitleEn(textResult.getSeoTitles().get("en"));
        }

        if (textResult.getDescriptions() != null) {
            product.setDescriptionUk(textResult.getDescriptions().get("uk"));
            product.setDescriptionRu(textResult.getDescriptions().get("ru"));
            product.setDescriptionEn(textResult.getDescriptions().get("en"));
        }

        if (textResult.getMetaDescriptions() != null) {
            product.setMetaDescriptionUk(textResult.getMetaDescriptions().get("uk"));
            product.setMetaDescriptionRu(textResult.getMetaDescriptions().get("ru"));
            product.setMetaDescriptionEn(textResult.getMetaDescriptions().get("en"));
        }

        if (textResult.getTags() != null) {
            product.setTagsUk(new HashSet<>(textResult.getTags().get("uk")));
            product.setTagsRu(new HashSet<>(textResult.getTags().get("ru")));
            product.setTagsEn(new HashSet<>(textResult.getTags().get("en")));
        }

        if (textResult.getTargetAudience() != null) {
            product.setTargetAudienceUk(textResult.getTargetAudience().get("uk"));
            product.setTargetAudienceRu(textResult.getTargetAudience().get("ru"));
            product.setTargetAudienceEn(textResult.getTargetAudience().get("en"));
        }

        // Set additional AI-generated fields
        product.setTrendScore(textResult.getTrendScore() != null ?
                BigDecimal.valueOf(textResult.getTrendScore()) : null);
        product.setPredictedPriceRange(textResult.getPredictedPriceRange());
        product.setStyleTags(textResult.getStyleTags());
        product.setMainFeatures(textResult.getMainFeatures());

        // Set vision analysis results
        if (visionResult != null) {
            product.setColorAnalysis(String.join(", ", visionResult.getColors()));
        }
    }

    private Double calculateConfidenceScore(ProductAnalysisResult visionResult, ProductAnalysisResult textResult) {
        double score = 0.0;
        int factors = 0;

        // Vision confidence factors
        if (visionResult != null) {
            if (visionResult.getVisualQuality() != null && visionResult.getVisualQuality() > 7) {
                score += 0.3;
            }
            if (visionResult.getBrandVisible() != null && visionResult.getBrandVisible()) {
                score += 0.2;
            }
            factors += 2;
        }

        // Text confidence factors
        if (textResult != null) {
            if (textResult.getTrendScore() != null && textResult.getTrendScore() > 6) {
                score += 0.3;
            }
            if (textResult.getSeoTitles() != null && !textResult.getSeoTitles().isEmpty()) {
                score += 0.2;
            }
            factors += 2;
        }

        return factors > 0 ? score / factors * 10 : 5.0; // Scale to 0-10
    }
}
