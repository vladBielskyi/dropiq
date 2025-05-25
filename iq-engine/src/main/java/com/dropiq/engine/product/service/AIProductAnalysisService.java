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
import java.util.*;
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
     * ОСНОВНИЙ МЕТОД: Аналіз продукту з логікою групового поділу
     */
    @Async
    public CompletableFuture<Product> analyzeProduct(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            log.info("🚀 Починаємо AI аналіз для: {} (Група: {})",
                    product.getName(), product.getGroupId());

            // КРОК 1: Перевіряємо чи є готовий аналіз для цієї групи
            Optional<Product> existingAnalyzedProduct = findExistingAnalysis(product);

            if (existingAnalyzedProduct.isPresent()) {
                log.info("✅ Знайдено готовий аналіз для групи, копіюємо");
                copyAiAnalysis(existingAnalyzedProduct.get(), product);
                return CompletableFuture.completedFuture(productRepository.save(product));
            }

            // КРОК 2: Виконуємо новий аналіз
            ProductAnalysisResult analysis = performSmartAnalysis(product);

            // КРОК 3: Знаходимо/створюємо категорію
            DatasetCategory category = categoryService.findOrCreateCategory(
                    product.getDatasets().iterator().next(),
                    analysis.getCategoryUk(),
                    analysis.getCategoryRu(),
                    analysis.getCategoryEn(),
                    null, null, null // Убираємо підкатегорії для простоти
            );

            // КРОК 4: Оновлюємо товар з результатами аналізу
            updateProductWithAnalysis(product, analysis, category);
            product = productRepository.save(product);

            // КРОК 5: Поділяємося аналізом з іншими товарами в групі
            shareAnalysisWithGroup(product);

            log.info("✅ AI аналіз завершено для: {}", product.getName());
            return CompletableFuture.completedFuture(product);

        } catch (Exception e) {
            log.error("❌ Помилка аналізу товару {}: {}", productId, e.getMessage(), e);
            throw new RuntimeException("AI аналіз не вдався: " + e.getMessage(), e);
        }
    }

    private Optional<Product> findExistingAnalysis(Product product) {
        if (product.getGroupId() == null || product.getGroupId().trim().isEmpty()) {
            return Optional.empty();
        }

        return productRepository.findByGroupIdAndSourceTypeAndAiAnalyzedTrue(
                product.getGroupId(),
                product.getSourceType()
        ).stream().findFirst();
    }

    /**
     * РОЗУМНИЙ АНАЛІЗ - коротко і по суті
     */
    private ProductAnalysisResult performSmartAnalysis(Product product) {
        log.info("🧠 Виконуємо розумний аналіз товару: {}", product.getName());

        // Аналіз зображення (якщо є)
        ProductAnalysisResult visionResult = null;
        if (!product.getImageUrls().isEmpty()) {
            visionResult = ollamaClient.analyzeProductImage(
                    product.getImageUrls().get(0),
                    createSmartVisionPrompt()
            );
        }

        // Генерація SEO контенту
        ProductAnalysisResult textResult = ollamaClient.generateMultilingualContent(
                createSmartTextPrompt(product, visionResult)
        );

        // Об'єднуємо результати
        if (visionResult != null) {
            textResult.setMainFeatures(visionResult.getMainFeatures());
            textResult.setColors(visionResult.getColors());
            textResult.setStyle(visionResult.getStyle());
            textResult.setVisualQuality(visionResult.getVisualQuality());
        }

        return textResult;
    }

    /**
     * КОРОТКИЙ І ТОЧНИЙ ПРОМПТ ДЛЯ ЗОБРАЖЕНЬ
     */
    private String createSmartVisionPrompt() {
        return """
            Проаналізуй це зображення товару як експерт e-commerce. Дай короткий і точний опис.
            
            Поверни ТІЛЬКИ JSON:
            {
              "product_type": "точний тип товару (макс 3 слова)",
              "main_features": ["ключова особливість 1", "особливість 2"],
              "colors": ["основний колір"],
              "style": "стиль (modern/classic/sporty/elegant)",
              "visual_quality": 8.0
            }
            
            ВАЖЛИВО:
            - Будь конкретним, не розмивчастим
            - Зосередься на тому, що впливає на покупку
            - Максимум 2-3 особливості
            """;
    }

    /**
     * ОПТИМІЗОВАНИЙ ПРОМПТ ДЛЯ SEO КОНТЕНТУ
     */
    private String createSmartTextPrompt(Product product, ProductAnalysisResult visionResult) {
        String baseInfo = String.format(
                "Товар: %s\nОпис: %s\nЦіна: %s грн\nКатегорія: %s",
                product.getName(),
                cleanDescription(product.getOriginalDescription()),
                formatPrice(product.getOriginalPrice()),
                product.getExternalCategoryName() != null ? product.getExternalCategoryName() : "Без категорії"
        );

        String visionInfo = "";
        if (visionResult != null && visionResult.getMainFeatures() != null) {
            visionInfo = "\nОсобливості з фото: " + String.join(", ", visionResult.getMainFeatures());
        }

        return String.format("""
            Ти топ копірайтер української e-commerce. Створи продаючий контент для цього товару.
            
            ТОВАР: %s%s
            
            Створи JSON з коротким і продаючим контентом:
            
            {
              "categories": {
                "main_uk": "чітка категорія українською (макс 2-3 слова)",
                "main_ru": "четкая категория русским (макс 2-3 слова)",
                "main_en": "clear category English (max 2-3 words)"
              },
              "seo_titles": {
                "uk": "Продаючий заголовок українською (40-50 символів, з ключовими словами)",
                "ru": "Продающий заголовок русским (40-50 символов, с ключевыми словами)",
                "en": "Selling title English (40-50 characters, with keywords)"
              },
              "descriptions": {
                "uk": "Короткий опис українською що продає. Чому варто купити? Які переваги? 50-80 слів максимум.",
                "ru": "Короткое описание русским которое продает. Почему стоит купить? Какие преимущества? 50-80 слов максимум.",
                "en": "Short selling description English. Why buy? What benefits? 50-80 words maximum."
              },
              "meta_descriptions": {
                "uk": "Короткий мета-опис українською (120-140 символів)",
                "ru": "Короткое мета-описание русским (120-140 символов)", 
                "en": "Short meta description English (120-140 characters)"
              },
              "tags": {
                "uk": ["тег1", "тег2", "тег3"],
                "ru": ["тег1", "тег2", "тег3"],
                "en": ["tag1", "tag2", "tag3"]
              },
              "target_audiences": {
                "uk": "Хто купить цей товар? (1 речення)",
                "ru": "Кто купит этот товар? (1 предложение)",
                "en": "Who will buy this product? (1 sentence)"
              },
              "trend_score": 7.5,
              "predicted_price_range": "mid-range",
              "style_tags": "modern, quality",
              "competitive_advantage": "Головна перевага над конкурентами (1 речення)",
              "urgency_triggers": "Чому треба купити зараз (1 речення)"
            }
            
            ПРАВИЛА:
            1. Пиши КОРОТКО і ПО СУТІ - без води
            2. Фокусуйся на ПЕРЕВАГАХ, не характеристиках  
            3. Використовуй прості українські слова
            4. Заголовки мають ПРОДАВАТИ, не просто описувати
            5. trend_score: 1-10 (реальна популярність товару в 2025)
            6. НЕ використовуй складні терміни
            7. Все має звучати ПРИРОДНО
            """, baseInfo, visionInfo);
    }

    private void copyAiAnalysis(Product source, Product target) {
        target.setAiAnalyzed(true);
        target.setAiAnalysisDate(LocalDateTime.now());
        target.setAiConfidenceScore(source.getAiConfidenceScore());
        target.setCategory(source.getCategory());

        // Копіюємо SEO контент
        target.setSeoTitleUk(source.getSeoTitleUk());
        target.setSeoTitleRu(source.getSeoTitleRu());
        target.setSeoTitleEn(source.getSeoTitleEn());

        target.setDescriptionUk(source.getDescriptionUk());
        target.setDescriptionRu(source.getDescriptionRu());
        target.setDescriptionEn(source.getDescriptionEn());

        target.setMetaDescriptionUk(source.getMetaDescriptionUk());
        target.setMetaDescriptionRu(source.getMetaDescriptionRu());
        target.setMetaDescriptionEn(source.getMetaDescriptionEn());

        // Копіюємо теги
        target.setTagsUk(new HashSet<>(source.getTagsUk()));
        target.setTagsRu(new HashSet<>(source.getTagsRu()));
        target.setTagsEn(new HashSet<>(source.getTagsEn()));

        // Копіюємо AI атрибути
        target.setTrendScore(source.getTrendScore());
        target.setPredictedPriceRange(source.getPredictedPriceRange());
        target.setTargetAudienceUk(source.getTargetAudienceUk());
        target.setTargetAudienceRu(source.getTargetAudienceRu());
        target.setTargetAudienceEn(source.getTargetAudienceEn());
        target.setStyleTags(source.getStyleTags());
        target.setMainFeatures(source.getMainFeatures());
    }

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

        log.info("📤 Поділився аналізом з {} товарів в групі", groupProducts.size());
    }

    private void updateProductWithAnalysis(Product product, ProductAnalysisResult analysis, DatasetCategory category) {
        product.setAiAnalyzed(true);
        product.setAiAnalysisDate(LocalDateTime.now());
        product.setAiConfidenceScore(calculateSmartConfidence(analysis));

        // Встановлюємо категорію
        product.setCategory(category);
        if (category != null) {
            category.addProduct(product);
        }

        // SEO контент
        if (analysis.getSeoTitles() != null) {
            product.setSeoTitleUk(cleanText(analysis.getSeoTitles().get("uk")));
            product.setSeoTitleRu(cleanText(analysis.getSeoTitles().get("ru")));
            product.setSeoTitleEn(cleanText(analysis.getSeoTitles().get("en")));
        }

        if (analysis.getDescriptions() != null) {
            product.setDescriptionUk(cleanText(analysis.getDescriptions().get("uk")));
            product.setDescriptionRu(cleanText(analysis.getDescriptions().get("ru")));
            product.setDescriptionEn(cleanText(analysis.getDescriptions().get("en")));
        }

        if (analysis.getMetaDescriptions() != null) {
            product.setMetaDescriptionUk(cleanText(analysis.getMetaDescriptions().get("uk")));
            product.setMetaDescriptionRu(cleanText(analysis.getMetaDescriptions().get("ru")));
            product.setMetaDescriptionEn(cleanText(analysis.getMetaDescriptions().get("en")));
        }

        if (analysis.getTags() != null) {
            product.setTagsUk(cleanTags(analysis.getTags().get("uk")));
            product.setTagsRu(cleanTags(analysis.getTags().get("ru")));
            product.setTagsEn(cleanTags(analysis.getTags().get("en")));
        }

        if (analysis.getTargetAudience() != null) {
            product.setTargetAudienceUk(cleanText(analysis.getTargetAudience().get("uk")));
            product.setTargetAudienceRu(cleanText(analysis.getTargetAudience().get("ru")));
            product.setTargetAudienceEn(cleanText(analysis.getTargetAudience().get("en")));
        }

        // AI атрибути
        product.setTrendScore(analysis.getTrendScore() != null ?
                BigDecimal.valueOf(Math.min(10.0, Math.max(1.0, analysis.getTrendScore()))) : null);
        product.setPredictedPriceRange(analysis.getPredictedPriceRange());
        product.setStyleTags(cleanText(analysis.getStyleTags()));

        if (analysis.getMainFeatures() != null && !analysis.getMainFeatures().isEmpty()) {
            product.setMainFeatures(String.join(", ", analysis.getMainFeatures()));
        }

        if (analysis.getColors() != null && !analysis.getColors().isEmpty()) {
            product.setColorAnalysis(String.join(", ", analysis.getColors()));
        }
    }

    // ДОПОМІЖНІ МЕТОДИ ДЛЯ ОЧИЩЕННЯ ТЕКСТУ
    private String cleanText(String text) {
        if (text == null) return null;
        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\.$", "");     // Прибираємо крапку в кінці
    }

    private Set<String> cleanTags(List<String> tags) {
        if (tags == null) return new HashSet<>();
        return new HashSet<>(tags.stream()
                .filter(Objects::nonNull)
                .map(this::cleanText)
                .filter(tag -> tag != null && !tag.isEmpty())
                .limit(5) // Максимум 5 тегів
                .toList());
    }

    private String cleanDescription(String description) {
        if (description == null) return "Немає опису";
        return description.length() > 200 ? description.substring(0, 200) + "..." : description;
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "Уточнити ціну";
        return String.format("%.0f", price);
    }

    private Double calculateSmartConfidence(ProductAnalysisResult analysis) {
        double score = 5.0; // Базовий рівень

        if (analysis.getTrendScore() != null && analysis.getTrendScore() > 7) score += 1.0;
        if (analysis.getSeoTitles() != null && !analysis.getSeoTitles().isEmpty()) score += 1.0;
        if (analysis.getMainFeatures() != null && !analysis.getMainFeatures().isEmpty()) score += 1.0;
        if (analysis.getVisualQuality() != null && analysis.getVisualQuality() > 7) score += 1.0;

        return Math.min(10.0, score);
    }
}