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

    @Value("${ai.analysis.batch-size:3}")
    private int batchSize;

    @Value("${ai.analysis.enable-vision:true}")
    private boolean enableVision;

    @Value("${ai.analysis.delay-between-requests:2000}")
    private long delayBetweenRequests;

    // Кеш для результатів аналізу по групах товарів
    private final Map<String, FeatureProductAnalysisResult> analysisCache = new ConcurrentHashMap<>();

    /**
     * Головний метод аналізу продукту одягу для Horoshop
     */
    public Product analyzeFashionProduct(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            log.info("Starting fashion analysis for: {} (Group: {})",
                    product.getExternalName(), product.getExternalGroupId());

            // Перевіряємо кеш
            String cacheKey = generateCacheKey(product);
            FeatureProductAnalysisResult cachedResult = analysisCache.get(cacheKey);
            if (cachedResult != null) {
                log.info("Using cached analysis for group: {}", cacheKey);
                applyAnalysisToProduct(product, cachedResult);
                return productRepository.save(product);
            }

            // Перевіряємо чи є аналіз у групі
            Optional<Product> analyzedGroupProduct = findAnalyzedProductInGroup(product);
            if (analyzedGroupProduct.isPresent()) {
                log.info("Copying analysis from group member");
                copyAnalysisFromProduct(analyzedGroupProduct.get(), product);
                return productRepository.save(product);
            }

            // Виконуємо новий аналіз
            FeatureProductAnalysisResult analysis = performFashionAnalysis(product);

            // Валідуємо результат
            if (!analysis.isValid()) {
                log.warn("Analysis result invalid, using fallback for: {}", product.getExternalName());
                analysis = createFallbackAnalysis(product);
            }

            // Кешуємо та застосовуємо
            analysisCache.put(cacheKey, analysis);
            applyAnalysisToProduct(product, analysis);
            product = productRepository.save(product);

            // Поширюємо на групу
            shareAnalysisWithGroup(product, analysis);

            log.info("Fashion analysis completed for: {}", product.getExternalName());
            return product;

        } catch (Exception e) {
            log.error("Error analyzing fashion product {}: {}", productId, e.getMessage(), e);
            throw  new RuntimeException("Fashion analysis failed", e);
        }
    }

    /**
     * Batch аналіз всіх продуктів одягу в датасеті
     */

    public Integer analyzeFashionDataset(Long datasetId) {
        try {
            DataSet dataset = dataSetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));

            log.info("Starting batch fashion analysis for dataset: {} ({} products)",
                    dataset.getName(), dataset.getTotalProducts());

            List<Product> productsToAnalyze = dataset.getProducts().stream()
                    .filter(p -> p.getAiAnalysisDate() != null)
                    .collect(Collectors.toList());

            if (productsToAnalyze.isEmpty()) {
                log.info("No products need analysis in dataset: {}", dataset.getName());
                return 0;
            }

            // Групуємо по варіантах для ефективності
            Map<String, List<Product>> productGroups = productsToAnalyze.stream()
                    .collect(Collectors.groupingBy(this::generateCacheKey));

            int totalAnalyzed = 0;
            int processedGroups = 0;

            log.info("Processing {} product groups for analysis", productGroups.size());

            // Аналізуємо по групах з затримкою
            for (Map.Entry<String, List<Product>> entry : productGroups.entrySet()) {
                List<Product> groupProducts = entry.getValue();
                Product representative = selectBestRepresentative(groupProducts);

                try {
                    log.info("Analyzing group {}/{}: {} ({} products)",
                            ++processedGroups, productGroups.size(),
                            entry.getKey(), groupProducts.size());

                    // Аналізуємо представника групи
                    FeatureProductAnalysisResult analysis = performFashionAnalysis(representative);

                    if (analysis.isValid()) {
                        // Застосовуємо до всіх продуктів групи
                        for (Product product : groupProducts) {
                            applyAnalysisToProduct(product, analysis);
                            productRepository.save(product);
                            totalAnalyzed++;
                        }

                        // Кешуємо результат
                        analysisCache.put(entry.getKey(), analysis);

                        log.info("Successfully analyzed group: {} ({} products)",
                                entry.getKey(), groupProducts.size());
                    } else {
                        log.warn("Invalid analysis for group: {}, skipping", entry.getKey());
                    }

                    // Затримка між групами для уникнення rate limit
                    if (processedGroups < productGroups.size()) {
                        Thread.sleep(delayBetweenRequests);
                    }

                } catch (Exception e) {
                    log.error("Failed to analyze group {}: {}", entry.getKey(), e.getMessage());
                    // Продовжуємо з наступною групою
                }
            }

            // Будуємо дерево категорій після аналізу
            try {
                categoryService.buildCategoryTreeForDataset(dataset);
                log.info("Category tree built for dataset: {}", dataset.getName());
            } catch (Exception e) {
                log.warn("Failed to build category tree: {}", e.getMessage());
            }

            log.info("Batch fashion analysis completed. Total analyzed: {}/{}",
                    totalAnalyzed, productsToAnalyze.size());
            return totalAnalyzed;

        } catch (Exception e) {
            log.error("Batch fashion analysis failed: {}", e.getMessage(), e);
           throw new RuntimeException("Batch analysis failed", e);
        }
    }

    /**
     * Виконання аналізу одягу з використанням GPT-4 Mini
     */
    private FeatureProductAnalysisResult performFashionAnalysis(Product product) {
        log.info("Performing GPT-4 Mini fashion analysis for: {}", product.getExternalName());

        try {

            FeatureProductAnalysisResult result;

            // Вибираємо метод аналізу залежно від наявності зображень
            if (enableVision && gptProvider.supportsVision() && hasQualityImages(product)) {
                String imageUrl = selectBestImage(product);
                log.info("Using vision analysis with image: {}", imageUrl);

                result = gptProvider.analyzeProductWithImage(
                        product.getExternalName(),
                        cleanDescription(product.getExternalDescription()),
                        imageUrl,
                        product.getOriginalPrice() != null ? product.getOriginalPrice().doubleValue() : null
                );
            } else {
                log.info("Using text-based analysis");

                result = gptProvider.analyzeProduct(
                        product.getExternalName(),
                        cleanDescription(product.getExternalDescription()),
                        product.getExternalCategoryName(),
                        product.getOriginalPrice() != null ? product.getOriginalPrice().doubleValue() : null
                );
            }

            // Додаткове SEO покращення для популярних товарів
            if (result.getTrendScore() != null && result.getTrendScore() >= 9) {
                enhanceWithSEOContent(result, product);
            }

            return result;

        } catch (Exception e) {
            log.warn("GPT analysis failed for {}, creating fallback: {}", product.getExternalName(), e.getMessage());
            return createFallbackAnalysis(product);
        }
    }

    /**
     * Застосування результатів аналізу до продукту (оптимізовано для Horoshop)
     */
    private void applyAnalysisToProduct(Product product, FeatureProductAnalysisResult analysis) {
        // AI статус
        product.setAiAnalysisDate(LocalDateTime.now());

        // Основний SEO контент
        if (analysis.getSeoTitle() != null) {
            product.setSeoTitleUa(truncateText(analysis.getSeoTitle(), 200));
        }

        if (analysis.getDescription() != null) {
            product.setDescriptionUa(analysis.getDescription());
        }

        if (analysis.getMetaDescription() != null) {
            product.setMetaDescriptionUa(truncateText(analysis.getMetaDescription(), 500));
        }

        // Теги
        if (analysis.getTags() != null && !analysis.getTags().isEmpty()) {
            Set<String> tags = new HashSet<>(analysis.getTags());
            product.setTags(tags.stream().limit(8).collect(Collectors.toSet()));
        } else if (analysis.getPrimaryKeywords() != null) {
            Set<String> tags = new HashSet<>(analysis.getPrimaryKeywords());
            product.setTags(tags.stream().limit(8).collect(Collectors.toSet()));
        }

        // Trend score
        if (analysis.getTrendScore() != null) {
            product.setTrendScore(BigDecimal.valueOf(analysis.getTrendScore())
                    .setScale(1, java.math.RoundingMode.HALF_UP));
        }

        // Основні атрибути одягу
        extractFashionAttributes(product, analysis);

        // Присвоєння категорії
        assignProductCategory(product, analysis);

        log.debug("Applied fashion analysis to: {}", product.getExternalName());
    }

    /**
     * Витягнення атрибутів одягу
     */
    private void extractFashionAttributes(Product product, FeatureProductAnalysisResult analysis) {
        // Основні атрибути
        if (analysis.getBrandName() != null) {
            product.setDetectedBrandName(analysis.getBrandName());
        }

        if (analysis.getPrimaryColor() != null) {
            product.setColor(analysis.getPrimaryColor());
        }

        if (analysis.getMaterialType() != null) {
            product.setMaterial(analysis.getMaterialType());
        }

        // Додаткові атрибути в мапу
        Map<String, String> attributes = product.getAttributes();

        if (analysis.getGender() != null) {
            attributes.put("gender", analysis.getGender());
        }

        if (analysis.getStyle() != null) {
            attributes.put("style", analysis.getStyle());
        }

        if (analysis.getSeason() != null) {
            attributes.put("season", analysis.getSeason());
        }

        if (analysis.getFitType() != null) {
            attributes.put("fit", analysis.getFitType());
        }

        if (analysis.getPattern() != null) {
            attributes.put("pattern", analysis.getPattern());
        }

        if (analysis.getOccasionDescription() != null) {
            attributes.put("occasion", analysis.getOccasionDescription());
        }
    }

    /**
     * Присвоєння категорії продукту
     */
    private void assignProductCategory(Product product, FeatureProductAnalysisResult analysis) {
        if (product.getDatasets().isEmpty()) return;

        DataSet dataset = product.getDatasets().iterator().next();

        String categoryName = analysis.getMainCategory();
        if (categoryName == null) {
            categoryName = analysis.getClothingCategory();
        }
        if (categoryName == null) {
            categoryName = "Одяг";
        }

        try {
//            DatasetCategory category = categoryService.findOrCreateCategory(
//                    dataset,
//                    categoryName,
//                    categoryName,
//                    categoryName,
//                    analysis.getSubCategory(),
//                    analysis.getSubCategory(),
//                    analysis.getSubCategory()
//            );
//
//            if (category != null) {
//                product.setCategory(category);
//                category.addProduct(product);
//            }
        } catch (Exception e) {
            log.warn("Failed to assign category for {}: {}", product.getExternalName(), e.getMessage());
        }
    }

    /**
     * Покращення SEO контенту для популярних товарів
     */
    private void enhanceWithSEOContent(FeatureProductAnalysisResult result, Product product) {
        try {
            FeatureProductAnalysisResult seoResult = gptProvider.generateSEOContent(
                    product.getExternalName(),
                    result.getDescription(),
                    result.getMainCategory(),
                    product.getSellingPrice() != null ? product.getSellingPrice().doubleValue() : null
            );

            // Покращуємо основний результат
            if (seoResult.getH1Title() != null) {
                result.setH1Title(seoResult.getH1Title());
            }

            if (seoResult.getLongTailKeywords() != null) {
                result.setLongTailKeywords(seoResult.getLongTailKeywords());
            }

        } catch (Exception e) {
            log.warn("Failed to enhance SEO content: {}", e.getMessage());
        }
    }

    /**
     * Створення резервного аналізу
     */
    private FeatureProductAnalysisResult createFallbackAnalysis(Product product) {
        FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

        String productName = product.getExternalName();
        String categoryName = product.getExternalCategoryName();

        // Основний контент
        result.setSeoTitle("Купити " + productName + " | Стильний одяг онлайн");
        result.setProductTitle(productName);
        result.setDescription(generateFallbackDescription(product));
        result.setShortDescription("Якісний " + productName + " за доступною ціною");
        result.setMetaDescription("Купити " + productName.toLowerCase() +
                " недорого ✓ Швидка доставка ✓ Гарантія якості");

        // Категорії
        result.setMainCategory(detectMainCategory(categoryName, productName));
        result.setSubCategory(categoryName);

        // Базові атрибути
        result.setTrendScore(5.0);
        result.setConversionScore(6.0);
        result.setPriceCategory(determinePriceCategory(product.getOriginalPrice()));
        result.setAnalysisConfidence(0.6);

        // Ключові слова
        result.setPrimaryKeywords(generateBasicKeywords(productName));
        result.setSellingPoints(Arrays.asList("Якісні матеріали", "Стильний дизайн", "Доступна ціна"));
        result.setTargetAudience("Люди, які цінують стиль та якість");

        return result;
    }

    // ===== ДОПОМІЖНІ МЕТОДИ =====

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
        target.setDescriptionUa(source.getDescriptionUa());
        target.setMetaDescriptionUa(source.getMetaDescriptionUa());
        target.setMetaDescriptionUa(source.getMetaDescriptionUa());
        target.setTags(new HashSet<>(source.getTags()));
        target.setTrendScore(source.getTrendScore());
        target.setDetectedBrandName(source.getDetectedBrandName());
        target.setColor(source.getColor());
        target.setMaterial(source.getMaterial());
        target.setCategory(source.getCategory());

        // Копіюємо атрибути
        target.getAttributes().putAll(source.getAttributes());
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

    private Product selectBestRepresentative(List<Product> products) {
        // Вибираємо продукт з найкращими зображеннями або найповнішим описом
        return products.stream()
                .max(Comparator.comparing(p ->
                        (p.getImageUrls().size() * 10) +
                                (p.getExternalDescription() != null ? p.getExternalDescription().length() : 0)))
                .orElse(products.get(0));
    }

    private boolean hasQualityImages(Product product) {
        return product.getImageUrls() != null &&
                !product.getImageUrls().isEmpty() &&
                product.getImageUrls().stream()
                        .anyMatch(url -> url.contains("http") &&
                                (url.contains(".jpg") || url.contains(".jpeg") ||
                                        url.contains(".png") || url.contains(".webp")));
    }

    private String selectBestImage(Product product) {
        if (product.getImageUrls() == null || product.getImageUrls().isEmpty()) {
            return null;
        }

        // Повертаємо перше зображення (зазвичай основне)
        return product.getImageUrls().get(0);
    }

    private String cleanDescription(String description) {
        if (description == null) return null;

        return description
                .replaceAll("<[^>]+>", "") // Видаляємо HTML теги
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ") // Множинні пробіли
                .trim();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() <= maxLength) return text;

        return text.substring(0, maxLength - 3) + "...";
    }

    private String generateFallbackDescription(Product product) {
        StringBuilder desc = new StringBuilder();

        desc.append("Якісний ").append(product.getExternalName().toLowerCase())
                .append(" преміум класу за доступною ціною. ");

        if (product.getExternalDescription() != null && !product.getExternalDescription().isEmpty()) {
            String cleaned = cleanDescription(product.getExternalDescription());
            if (cleaned.length() > 100) {
                desc.append(cleaned.substring(0, 100)).append("... ");
            } else {
                desc.append(cleaned).append(" ");
            }
        }

        desc.append("Сучасний дизайн, комфортні матеріали, швидка доставка по Україні. ")
                .append("Ідеально підходить для створення стільного образу. ")
                .append("Гарантія якості та найкращі ціни в Україні.");

        return desc.toString();
    }

    private String detectMainCategory(String categoryName, String productName) {
        if (categoryName == null && productName == null) return "Одяг";

        String text = (categoryName + " " + productName).toLowerCase();

        // Жіночий одяг
        if (text.contains("жіноч") || text.contains("женск") ||
                text.contains("сукн") || text.contains("блузк") || text.contains("спідниц")) {
            return "Жіночий одяг";
        }

        // Чоловічий одяг
        if (text.contains("чолов") || text.contains("мужск") ||
                text.contains("сорочк") && !text.contains("жіноч")) {
            return "Чоловічий одяг";
        }

        // Взуття
        if (text.contains("взутт") || text.contains("обув") ||
                text.contains("черевик") || text.contains("туфл") ||
                text.contains("кросівк") || text.contains("босоніжк")) {
            return "Взуття";
        }

        // Аксесуари
        if (text.contains("сумк") || text.contains("аксесуар") ||
                text.contains("ремен") || text.contains("окуляр") || text.contains("годинник")) {
            return "Аксесуари";
        }

        return "Одяг";
    }

    private String determinePriceCategory(BigDecimal price) {
        if (price == null) return "середній";

        double priceValue = price.doubleValue();
        if (priceValue < 500) return "бюджетний";
        if (priceValue > 2000) return "преміум";
        return "середній";
    }

    private List<String> generateBasicKeywords(String productName) {
        List<String> keywords = new ArrayList<>();

        keywords.add(productName.toLowerCase());
        keywords.add("купити " + productName.toLowerCase());
        keywords.add(productName.toLowerCase() + " україна");
        keywords.add(productName.toLowerCase() + " недорого");
        keywords.add("якісний " + productName.toLowerCase());

        return keywords;
    }

    /**
     * Очищення кешу аналізу
     */
    public void clearAnalysisCache() {
        int size = analysisCache.size();
        analysisCache.clear();
        log.info("Analysis cache cleared ({} entries removed)", size);
    }

    /**
     * Статистика роботи AI сервісу
     */
    public Map<String, Object> getAnalysisStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("cacheSize", analysisCache.size());
        stats.put("aiProviderName", gptProvider.getProviderName());
        stats.put("visionEnabled", enableVision);
        stats.put("visionSupported", gptProvider.supportsVision());
        stats.put("batchSize", batchSize);
        stats.put("delayBetweenRequests", delayBetweenRequests);
        stats.put("maxTextLength", gptProvider.getMaxTextLength());
        stats.put("costPerRequest", gptProvider.getCostPerRequest());

        return stats;
    }

    /**
     * Повторний аналіз продуктів з низьким трендом
     */
    @Async
    public CompletableFuture<Integer> reanalyzeLowTrendProducts(Long datasetId) {
        try {
            DataSet dataset = dataSetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));

            List<Product> lowTrendProducts = dataset.getProducts().stream()
                    .filter(p -> p.getTrendScore() != null &&
                            p.getTrendScore().compareTo(BigDecimal.valueOf(4.0)) < 0)
                    .limit(10) // Обмежуємо для контролю витрат
                    .collect(Collectors.toList());

            log.info("Re-analyzing {} low-trend products in dataset: {}",
                    lowTrendProducts.size(), dataset.getName());

            int reanalyzed = 0;
            for (Product product : lowTrendProducts) {
                try {
                    // Очищуємо кеш для повторного аналізу
                    String cacheKey = generateCacheKey(product);
                    analysisCache.remove(cacheKey);

                    // Виконуємо новий аналіз
                    FeatureProductAnalysisResult analysis = performFashionAnalysis(product);
                    if (analysis.isValid()) {
                        applyAnalysisToProduct(product, analysis);
                        productRepository.save(product);
                        reanalyzed++;
                    }

                    Thread.sleep(delayBetweenRequests); // Затримка між запитами

                } catch (Exception e) {
                    log.error("Failed to re-analyze product {}: {}", product.getId(), e.getMessage());
                }
            }

            log.info("Re-analysis completed: {}/{} products improved", reanalyzed, lowTrendProducts.size());
            return CompletableFuture.completedFuture(reanalyzed);

        } catch (Exception e) {
            log.error("Re-analysis failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Re-analysis failed", e));
        }
    }

    /**
     * Покращення контенту для популярних товарів
     */
    @Async
    public CompletableFuture<Integer> enhancePopularProducts(Long datasetId) {
        try {
            DataSet dataset = dataSetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));

            List<Product> popularProducts = dataset.getProducts().stream()
                    .filter(p -> p.getTrendScore() != null &&
                            p.getTrendScore().compareTo(BigDecimal.valueOf(7.0)) >= 0)
                    .limit(15) // Обмеження для контролю витрат API
                    .collect(Collectors.toList());

            log.info("Enhancing {} popular products with additional SEO in dataset: {}",
                    popularProducts.size(), dataset.getName());

            int enhanced = 0;
            for (Product product : popularProducts) {
                try {
                    // Генеруємо додатковий SEO контент
                    FeatureProductAnalysisResult seoResult = gptProvider.generateSEOContent(
                            product.getExternalName(),
                            product.getDescriptionUa(),
                            product.getCategory() != null ? product.getCategory().getNameUk() : null,
                            product.getSellingPrice() != null ? product.getSellingPrice().doubleValue() : null
                    );

                    // Оновлюємо контент якщо він кращий
                    boolean updated = false;
                    if (seoResult.getSeoTitle() != null && seoResult.getSeoTitle().length() > 20) {
                        product.setSeoTitleUa(seoResult.getSeoTitle());
                        updated = true;
                    }

                    if (seoResult.getMetaDescription() != null && seoResult.getMetaDescription().length() > 50) {
                        product.setMetaDescriptionUa(seoResult.getMetaDescription());
                        updated = true;
                    }

                    if (updated) {
                        productRepository.save(product);
                        enhanced++;
                    }

                    Thread.sleep(delayBetweenRequests * 2); // Більша затримка для SEO запитів

                } catch (Exception e) {
                    log.error("Failed to enhance product {}: {}", product.getId(), e.getMessage());
                }
            }

            log.info("Enhancement completed: {}/{} products enhanced", enhanced, popularProducts.size());
            return CompletableFuture.completedFuture(enhanced);

        } catch (Exception e) {
            log.error("Enhancement failed: {}", e.getMessage());
            return CompletableFuture.failedFuture(new RuntimeException("Enhancement failed", e));
        }
    }
}
