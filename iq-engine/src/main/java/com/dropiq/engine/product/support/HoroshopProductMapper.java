package com.dropiq.engine.product.support;

import com.dropiq.engine.integration.imp.horoshop.model.HoroshopCharacteristic;
import com.dropiq.engine.integration.imp.horoshop.model.HoroshopImages;
import com.dropiq.engine.integration.imp.horoshop.model.HoroshopProduct;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced HoroshopProductMapper fully compliant with current Horoshop API
 * - Complete multilingual support
 * - Full product data extraction
 * - Optimized for e-commerce conversion
 * - AI-enhanced content integration
 */
@Slf4j
@Component
public class HoroshopProductMapper {

    private static final DateTimeFormatter HOROSHOP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert Product to HoroshopProduct with complete data mapping
     */
    public HoroshopProduct toHoroshopProduct(Product product, DataSet dataset) {
        log.debug("Converting product {} to Horoshop format", product.getExternalName());

        // Prepare product for export
        product.prepareForHoroshopExport();

        HoroshopProduct horoshopProduct = new HoroshopProduct();

        // ===== MARKETPLACE EXPORT =====
        horoshopProduct.setExportToMarketplace(generateMarketplaceExportList(product));

        // Individual marketplace flags
        horoshopProduct.setFacebookExport(true);
        horoshopProduct.setGoogleExport(product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 6.0);
        horoshopProduct.setRozetkaExport(product.isPopular());
        horoshopProduct.setPromExport(product.getSellingPrice() != null && product.getSellingPrice().doubleValue() > 500);

        // ===== SEO META DATA =====
        setupSEOMetaData(product, horoshopProduct);

        // ===== GUARANTEES AND PROMOTIONS =====
        setupAdvancedPromotions(product, horoshopProduct);

        // ===== SHIPPING INFO =====
        setupShippingInfo(product, horoshopProduct);

        // ===== PRODUCT STATUS =====
        horoshopProduct.setStatus("active");
        horoshopProduct.setVisible(true);
        horoshopProduct.setFeaturedProduct(product.isPopular());

        // ===== AI OPTIMIZATION FLAGS =====
        horoshopProduct.setAiOptimized(product.getAiAnalysisDate() != null);
        horoshopProduct.setAutoSeo(true);
        horoshopProduct.setAutoTranslate(false);

        // ===== ANALYTICS =====
        horoshopProduct.setConversionTracking(true);
        horoshopProduct.setAnalyticsEnabled(true);
        horoshopProduct.setEnableReviews(true);

        // ===== EXTERNAL IDS =====
        Map<String, String> externalIds = new HashMap<>();
        externalIds.put("source_type", product.getSourceType() != null ? product.getSourceType().name() : "UNKNOWN");
        externalIds.put("external_id", product.getExternalId());
        if (product.getExternalGroupId() != null) {
            externalIds.put("external_group_id", product.getExternalGroupId());
        }
        horoshopProduct.setExternalIds(externalIds);

        // ===== TIMESTAMPS =====
        horoshopProduct.setCreatedAt(product.getCreatedAt() != null ?
                product.getCreatedAt().format(HOROSHOP_DATE_FORMAT) : null);
        horoshopProduct.setUpdatedAt(product.getUpdatedAt() != null ?
                product.getUpdatedAt().format(HOROSHOP_DATE_FORMAT) : null);

        // Calculate discount if applicable
        horoshopProduct.calculateDiscount();

        log.debug("Successfully converted product {} to Horoshop format", product.getExternalName());
        return horoshopProduct;
    }

    /**
     * Build characteristics optimized for simple structure
     */
    private List<HoroshopCharacteristic> buildSimpleCharacteristics(Product product) {
        List<HoroshopCharacteristic> characteristics = new ArrayList<>();

        // ===== BASIC PRODUCT ATTRIBUTES =====
        addCharacteristicIfPresent(characteristics, "Бренд", product.getDetectedBrandName());
        addCharacteristicIfPresent(characteristics, "Колір", product.getColor());
        addCharacteristicIfPresent(characteristics, "Матеріал", product.getMaterial());

        if (product.getGender() != null) {
            addCharacteristicIfPresent(characteristics, "Стать", translateGender(product.getGender()));
        }

        if (product.getSeason() != null) {
            addCharacteristicIfPresent(characteristics, "Сезон", translateSeason(product.getSeason()));
        }

        addCharacteristicIfPresent(characteristics, "Стиль", product.getStyle());
        addCharacteristicIfPresent(characteristics, "Призначення", product.getOccasion());

        // ===== SIZE INFORMATION =====
        if (product.getNormalizedSize() != null) {
            addCharacteristicIfPresent(characteristics, "Розмір", product.getNormalizedSize());
        }

        // ===== AI ENHANCED CHARACTERISTICS =====
        if (product.getHoroshopCharacteristics() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> aiChars = objectMapper.readValue(
                        product.getHoroshopCharacteristics(), Map.class);

                aiChars.forEach((key, value) -> {
                    if (value != null && !value.toString().trim().isEmpty()) {
                        String translatedKey = translateAttributeName(key);
                        String valueStr = value.toString();

                        if (value instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> listValue = (List<String>) value;
                            valueStr = String.join(", ", listValue);
                        }

                        addCharacteristicIfPresent(characteristics, translatedKey, valueStr);
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to parse AI characteristics for product {}: {}",
                        product.getId(), e.getMessage());
            }
        }

        // ===== ADDITIONAL ATTRIBUTES =====
        product.getAttributes().forEach((key, value) -> {
            if (value != null && !value.trim().isEmpty()) {
                String translatedKey = translateAttributeName(key);
                addCharacteristicIfPresent(characteristics, translatedKey, value);
            }
        });

        // ===== QUALITY INDICATORS =====
        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 8.0) {
            addCharacteristicIfPresent(characteristics, "Рейтинг популярності", "Високий");
        }

        if (product.isNew()) {
            addCharacteristicIfPresent(characteristics, "Новинка", "Так");
        }

        if (product.getAiAnalysisDate() != null) {
            addCharacteristicIfPresent(characteristics, "AI оптимізація", "Так");
        }

        // ===== COMMERCE INDICATORS =====
        if (product.getConversionPotential() != null &&
                product.getConversionPotential().doubleValue() >= 7.0) {
            addCharacteristicIfPresent(characteristics, "Рекомендований", "Так");
        }

        return characteristics.stream().limit(20).collect(Collectors.toList());
    }

    /**
     * Helper method to add characteristic if value is present
     */
    private void addCharacteristicIfPresent(List<HoroshopCharacteristic> characteristics,
                                            String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            HoroshopCharacteristic characteristic = new HoroshopCharacteristic();
            characteristic.setName(name);
            characteristic.setValue(value);
            characteristics.add(characteristic);
        }
    }

    /**
     * Helper method to add characteristic with unit
     */
    private void addCharacteristicWithUnit(List<HoroshopCharacteristic> characteristics,
                                           String name, String value, String unit) {
        if (value != null && !value.trim().isEmpty()) {
            HoroshopCharacteristic characteristic = new HoroshopCharacteristic();
            characteristic.setName(name);
            characteristic.setValue(value);
            characteristic.setUnit(unit);
            characteristics.add(characteristic);
        }
    }

    /**
     * Setup SEO meta data
     */
    private void setupSEOMetaData(Product product, HoroshopProduct horoshopProduct) {
        // Meta titles
        Map<String, String> metaTitles = new HashMap<>();
        if (product.getSeoTitleUa() != null) {
            metaTitles.put("ua", product.getSeoTitleUa());
        }
        if (product.getSeoTitleRu() != null) {
            metaTitles.put("ru", product.getSeoTitleRu());
        }
        horoshopProduct.setMetaTitle(metaTitles);

        // Meta descriptions
        Map<String, String> metaDescriptions = new HashMap<>();
        if (product.getMetaDescriptionUa() != null) {
            metaDescriptions.put("ua", product.getMetaDescriptionUa());
        }
        if (product.getMetaDescriptionRu() != null) {
            metaDescriptions.put("ru", product.getMetaDescriptionRu());
        }
        horoshopProduct.setMetaDescription(metaDescriptions);

        // Meta keywords
        Map<String, String> metaKeywords = new HashMap<>();
        if (!product.getKeywordsUa().isEmpty()) {
            metaKeywords.put("ua", String.join(", ", product.getKeywordsUa()));
        }
        if (!product.getKeywordsRu().isEmpty()) {
            metaKeywords.put("ru", String.join(", ", product.getKeywordsRu()));
        }
        horoshopProduct.setMetaKeywords(metaKeywords);
    }

    /**
     * Setup shipping information
     */
    private void setupShippingInfo(Product product, HoroshopProduct horoshopProduct) {
        // Estimate shipping weight based on category or attributes
        if (product.getAttributes().containsKey("weight")) {
            String weightStr = product.getAttributes().get("weight");
            try {
                horoshopProduct.setShippingWeight(Double.parseDouble(weightStr));
            } catch (NumberFormatException e) {
                log.debug("Could not parse weight: {}", weightStr);
            }
        }

        // Default delivery time based on stock
        if (product.getStock() != null && product.getStock() > 0) {
            horoshopProduct.setDeliveryTime("1-3 дня");
        } else {
            horoshopProduct.setDeliveryTime("під замовлення 5-7 днів");
        }

        // Free shipping for higher-priced items
        if (product.getSellingPrice() != null && product.getSellingPrice().doubleValue() > 1000) {
            horoshopProduct.setFreeShipping(true);
        }
    }

    /**
     * Generate marketing icons
     */
    private List<String> generateMarketingIcons(Product product) {
        List<String> icons = new ArrayList<>();

        if (product.isPopular()) {
            icons.add("Хіт");
        }

        if (product.isNew()) {
            icons.add("Новинка");
        }

        if (product.getOriginalPrice() != null && product.getSellingPrice() != null &&
                product.getOriginalPrice().compareTo(product.getSellingPrice()) > 0) {
            icons.add("Знижка");
        }

        if (product.hasLowStock()) {
            icons.add("Останні");
        }

        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 8.0) {
            icons.add("Топ");
        }

//        if (product.getAiAnalysisDate() != null) {
//            icons.add("AI");
//        }

        return icons.stream().distinct().limit(6).collect(Collectors.toList());
    }

    /**
     * Update Product from HoroshopProduct (reverse mapping)
     */
    public void updateFromHoroshopProduct(Product product, HoroshopProduct horoshopProduct) {
        log.debug("Updating product {} from Horoshop data", product.getExternalName());

        // Update inventory
        if (horoshopProduct.getQuantity() != null) {
            product.setStock(horoshopProduct.getQuantity());
        }

        if (horoshopProduct.getPresence() != null) {
            product.setPresence(horoshopProduct.getPresence());
            product.setAvailable(!"Нет в наличии".equals(horoshopProduct.getPresence()) &&
                    !"Нема в наявності".equals(horoshopProduct.getPresence()));
        }

        // Update pricing
        if (horoshopProduct.getPrice() != null) {
            product.setSellingPrice(BigDecimal.valueOf(horoshopProduct.getPrice()));
        }

        if (horoshopProduct.getPriceOld() != null) {
            product.setOriginalPrice(BigDecimal.valueOf(horoshopProduct.getPriceOld()));
        }

        // Update images
        if (horoshopProduct.getImages() != null &&
                horoshopProduct.getImages().getLinks() != null &&
                !horoshopProduct.getImages().getLinks().isEmpty()) {

            if (Boolean.TRUE.equals(horoshopProduct.getImages().getOverride())) {
                product.getImageUrls().clear();
            }

            // Add new images
            horoshopProduct.getImages().getLinks().forEach(imageUrl -> {
                if (isValidImageUrl(imageUrl) && !product.getImageUrls().contains(imageUrl)) {
                    product.getImageUrls().add(imageUrl);
                }
            });

            product.updateMainImageUrl();
        }

        // Update sync status
        product.setLastSync(LocalDateTime.now());
        product.updateHoroshopExportStatus(true, "Successfully updated from Horoshop");

        log.debug("Successfully updated product {} from Horoshop data", product.getExternalName());
    }

    /**
     * Build comprehensive product characteristics for Horoshop
     */
    private List<HoroshopCharacteristic> buildComprehensiveCharacteristics(Product product) {
        List<HoroshopCharacteristic> characteristics = new ArrayList<>();

        // ===== BASIC PRODUCT INFO =====
        if (product.getDetectedBrandName() != null) {
            characteristics.add(new HoroshopCharacteristic("Бренд", product.getDetectedBrandName()));
        }

        if (product.getColor() != null) {
            characteristics.add(new HoroshopCharacteristic("Колір", product.getColor()));
        }

        if (product.getMaterial() != null) {
            characteristics.add(new HoroshopCharacteristic("Матеріал", product.getMaterial()));
        }

        if (product.getGender() != null) {
            String genderText = translateGender(product.getGender());
            characteristics.add(new HoroshopCharacteristic("Стать", genderText));
        }

        if (product.getSeason() != null) {
            String seasonText = translateSeason(product.getSeason());
            characteristics.add(new HoroshopCharacteristic("Сезон", seasonText));
        }

        if (product.getStyle() != null) {
            characteristics.add(new HoroshopCharacteristic("Стиль", product.getStyle()));
        }

        if (product.getOccasion() != null) {
            characteristics.add(new HoroshopCharacteristic("Призначення", product.getOccasion()));
        }

        // ===== SIZE INFORMATION =====
        if (product.getNormalizedSize() != null) {
            characteristics.add(new HoroshopCharacteristic("Розмір", product.getNormalizedSize()));
        }

        if (product.getSizeType() != null) {
            characteristics.add(new HoroshopCharacteristic("Тип розміру", product.getSizeType()));
        }

        // ===== AI ENHANCED CHARACTERISTICS =====
        if (product.getHoroshopCharacteristics() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> aiChars = objectMapper.readValue(
                        product.getHoroshopCharacteristics(), Map.class);

                aiChars.forEach((key, value) -> {
                    if (value != null && !value.toString().trim().isEmpty()) {
                        String translatedKey = translateAttributeName(key);
                        String valueStr = value.toString();

                        // Handle list values
                        if (value instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> listValue = (List<String>) value;
                            valueStr = String.join(", ", listValue);
                        }

                        characteristics.add(new HoroshopCharacteristic(translatedKey, valueStr));
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to parse AI characteristics for product {}: {}",
                        product.getId(), e.getMessage());
            }
        }

        // ===== ADDITIONAL ATTRIBUTES =====
        product.getAttributes().forEach((key, value) -> {
            if (value != null && !value.trim().isEmpty() && !isBasicAttribute(key)) {
                String translatedKey = translateAttributeName(key);
                characteristics.add(new HoroshopCharacteristic(translatedKey, value));
            }
        });

        // ===== QUALITY INDICATORS =====
        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 8.0) {
            characteristics.add(new HoroshopCharacteristic("Рейтинг", "Високий попит"));
        }

        if (product.isNew()) {
            characteristics.add(new HoroshopCharacteristic("Новинка", "Так"));
        }

        if (product.getAiAnalysisDate() != null) {
            characteristics.add(new HoroshopCharacteristic("AI оптимізація", "Так"));
        }

        // ===== COMMERCE INDICATORS =====
        if (product.getConversionPotential() != null &&
                product.getConversionPotential().doubleValue() >= 7.0) {
            characteristics.add(new HoroshopCharacteristic("Потенціал продажів", "Високий"));
        }

        return characteristics.stream()
                .filter(x -> x.getName() != null && x.getValue() != null)
                .limit(20) // Reasonable limit for Horoshop
                .collect(Collectors.toList());
    }

    /**
     * Generate enhanced marketing icons based on comprehensive product analysis
     */
    private List<String> generateEnhancedMarketingIcons(Product product) {
        List<String> icons = new ArrayList<>();

        // ===== POPULARITY ICONS =====
        if (product.isPopular()) {
            icons.add("Хіт");
            icons.add("Популярне");
        }

        // ===== NEWNESS ICONS =====
        if (product.isNew()) {
            icons.add("Новинка");
        }

        // ===== DISCOUNT ICONS =====
        if (product.getOriginalPrice() != null && product.getSellingPrice() != null &&
                product.getOriginalPrice().compareTo(product.getSellingPrice()) > 0) {

            BigDecimal discount = product.getOriginalPrice().subtract(product.getSellingPrice())
                    .divide(product.getOriginalPrice(), 2, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (discount.doubleValue() >= 20) {
                icons.add("Супер знижка");
            } else if (discount.doubleValue() >= 10) {
                icons.add("Знижка");
            }
        }

        // ===== STOCK ICONS =====
        if (product.hasLowStock()) {
            icons.add("Останні");
            icons.add("Встигни");
        }

        // ===== QUALITY ICONS =====
        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 8.0) {
            icons.add("Преміум");
            icons.add("Топ якість");
        }

        if (product.getDetectedBrandName() != null && product.getBrandDetected()) {
            icons.add("Бренд");
        }

        // ===== AI OPTIMIZATION ICONS =====
        if (product.getAiAnalysisDate() != null) {
            icons.add("AI оптимізовано");
        }

        if (product.getConversionPotential() != null &&
                product.getConversionPotential().doubleValue() >= 8.0) {
            icons.add("Рекомендуємо");
        }

        // ===== SEASONAL ICONS =====
        if (product.getSeasonalityScore() != null &&
                product.getSeasonalityScore().doubleValue() >= 7.0) {
            icons.add("Сезонний хіт");
        }

        return icons.stream().distinct().limit(8).collect(Collectors.toList());
    }

    /**
     * Generate comprehensive marketplace export list
     */
    private String generateMarketplaceExportList(Product product) {
        List<String> marketplaces = new ArrayList<>();

        // Base marketplace for all products
        marketplaces.add("Facebook Feed");

        // Quality-based marketplaces
        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 6.0) {
            marketplaces.add("Google Shopping");
        }

        // Popular products get more exposure
        if (product.isPopular()) {
            marketplaces.add("Rozetka Feed");
            marketplaces.add("Prom.ua Feed");
        }

        // Premium products on additional platforms
        if (product.getSellingPrice() != null && product.getSellingPrice().doubleValue() > 1000) {
            marketplaces.add("Allo.ua Feed");
            marketplaces.add("Epicentr Feed");
        }

        // High conversion products
        if (product.getConversionPotential() != null &&
                product.getConversionPotential().doubleValue() >= 8.0) {
            marketplaces.add("Price.ua Feed");
            marketplaces.add("Hotline.ua Feed");
        }

        return String.join(";", marketplaces);
    }

    /**
     * Setup advanced promotions and guarantees
     */
    private void setupAdvancedPromotions(Product product, HoroshopProduct horoshopProduct) {
        // ===== GUARANTEE SETUP =====
        if (product.getDetectedBrandName() != null && product.getBrandDetected() &&
                !product.getDetectedBrandName().toLowerCase().contains("noname")) {
            horoshopProduct.setGuaranteeShop("Гарантія магазину");
            horoshopProduct.setGuaranteeLength(12); // 12 months
        }

        // ===== PROMOTIONAL TIMERS =====
        if (product.isPopular() && product.hasLowStock()) {
            LocalDateTime endTime = LocalDateTime.now().plusDays(2);
            horoshopProduct.setCountdownEndTime(endTime.format(HOROSHOP_DATE_FORMAT));

            Map<String, String> countdownDesc = new HashMap<>();
            countdownDesc.put("ua", "Встигни! Залишилось мало товару!");
            countdownDesc.put("ru", "Успей! Остается мало товара!");
            horoshopProduct.setCountdownDescription(countdownDesc);
        }

        // ===== SPECIAL OFFERS =====
        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 8.0) {
            Map<String, String> specialOffer = new HashMap<>();
            specialOffer.put("ua", "Ексклюзивна пропозиція! Тільки цього місяця!");
            specialOffer.put("ru", "Эксклюзивное предложение! Только в этом месяце!");
            horoshopProduct.setSpecialOffer(specialOffer);
        }
    }

    /**
     * Setup comprehensive meta data for SEO
     */
    private void setupMetaData(Product product, HoroshopProduct horoshopProduct) {
        Map<String, String> metaData = new HashMap<>();

        // SEO keywords
        if (!product.getKeywordsUa().isEmpty()) {
            metaData.put("keywords_ua", String.join(", ", product.getKeywordsUa()));
        }
        if (!product.getKeywordsRu().isEmpty()) {
            metaData.put("keywords_ru", String.join(", ", product.getKeywordsRu()));
        }

        // Meta descriptions
        if (product.getMetaDescriptionUa() != null) {
            metaData.put("meta_description_ua", product.getMetaDescriptionUa());
        }
        if (product.getMetaDescriptionRu() != null) {
            metaData.put("meta_description_ru", product.getMetaDescriptionRu());
        }

        // AI analysis data
        if (product.getAiAnalysisDate() != null) {
            metaData.put("ai_optimized", "true");
            metaData.put("ai_analysis_date", product.getAiAnalysisDate().toString());
        }

        if (product.getAiConfidenceScore() != null) {
            metaData.put("ai_confidence", product.getAiConfidenceScore().toString());
        }
    }

    // ===== UTILITY METHODS =====

    private String getOptimalTitle(Product product, String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return product.getSeoTitleUa() != null ? product.getSeoTitleUa() :
                        (product.getSeoTitleRu() != null ? product.getSeoTitleRu() :
                                generateFallbackTitle(product));
            case "ru":
                return product.getSeoTitleRu() != null ? product.getSeoTitleRu() :
                        (product.getSeoTitleUa() != null ? product.getSeoTitleUa() :
                                generateFallbackTitle(product));
            case "en":
                return product.getSeoTitleEn() != null ? product.getSeoTitleEn() :
                        product.getSeoTitleUa();
            default:
                return product.getSeoTitleUa() != null ? product.getSeoTitleUa() :
                        generateFallbackTitle(product);
        }
    }

    private String getOptimalDescription(Product product, String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return product.getDescriptionUa() != null ? product.getDescriptionUa() :
                        (product.getDescriptionRu() != null ? product.getDescriptionRu() :
                                product.getExternalDescription());
            case "ru":
                return product.getDescriptionRu() != null ? product.getDescriptionRu() :
                        (product.getDescriptionUa() != null ? product.getDescriptionUa() :
                                product.getExternalDescription());
            case "en":
                return product.getDescriptionEn() != null ? product.getDescriptionEn() :
                        product.getDescriptionUa();
            default:
                return product.getDescriptionUa() != null ? product.getDescriptionUa() :
                        product.getExternalDescription();
        }
    }

    private String getOptimalShortDescription(Product product, String language) {
        switch (language.toLowerCase()) {
            case "ua", "uk":
                return product.getShortDescriptionUa() != null ? product.getShortDescriptionUa() :
                        product.getShortDescriptionRu();
            case "ru":
                return product.getShortDescriptionRu() != null ? product.getShortDescriptionRu() :
                        product.getShortDescriptionUa();
            case "en":
                return product.getShortDescriptionEn() != null ? product.getShortDescriptionEn() :
                        product.getShortDescriptionUa();
            default:
                return product.getShortDescriptionUa();
        }
    }

    private String generateFallbackTitle(Product product) {
        String baseName = product.getExternalName();

        // Check if name looks like technical code
        if (baseName.matches("^[0-9A-Z\\-_]+$")) {
            String category = product.getExternalCategoryName();
            return category != null ? "Якісний " + category.toLowerCase() : "Якісний товар";
        }

        return baseName;
    }

    private String generateOptimalArticle(Product product) {
        if (product.getHoroshopArticle() != null && !product.getHoroshopArticle().trim().isEmpty()) {
            return product.getHoroshopArticle();
        }

        // Generate from external ID with dataset prefix
        String baseArticle = product.getExternalId();
        if (baseArticle.length() > 50) {
            baseArticle = baseArticle.substring(0, 50);
        }

        // Add source type prefix for uniqueness
        if (product.getSourceType() != null) {
            return product.getSourceType().name().substring(0, 2) + "_" + baseArticle;
        }

        return baseArticle;
    }

    private String determinePresenceStatus(Product product) {
        if (!product.getAvailable()) {
            return "Нет в наличии";
        }

        if (product.getStock() != null && product.getStock() > 0) {
            return product.getStock() > 10 ? "В наличии" : "Мало в наличии";
        }

        return "Под заказ";
    }

    private String buildOptimalCategoryPath(Product product) {
        if (product.getHoroshopCategoryPath() != null) {
            return product.getHoroshopCategoryPath();
        }

        if (product.getCategory() != null) {
            return buildCategoryPath(product.getCategory());
        }

        // Fallback based on external category
        if (product.getExternalCategoryName() != null) {
            return "Товари / " + product.getExternalCategoryName();
        }

        return "Товари / Загальні";
    }

    private String buildCategoryPath(com.dropiq.engine.product.entity.DatasetCategory category) {
        if (category.getParent() != null) {
            return buildCategoryPath(category.getParent()) + " / " + category.getNameUk();
        }
        return category.getNameUk();
    }

    private String generateSEOSlug(Product product) {
        String base;

        if (product.getSeoTitleUa() != null) {
            base = product.getSeoTitleUa();
        } else if (product.getExternalName() != null) {
            base = product.getExternalName();
        } else {
            return null;
        }

        String slug = base.toLowerCase()
                .replaceAll("[^a-z0-9а-яё\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (product.getCategory() != null && product.getCategory().getSlug() != null) {
            return product.getCategory().getSlug() + "/" + slug;
        }

        return slug;
    }

    private String cleanHtmlAndTruncate(String html, int maxLength) {
        if (html == null) return "";

        String cleaned = html
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();

        return cleaned.length() > maxLength ?
                cleaned.substring(0, maxLength - 3) + "..." : cleaned;
    }

    private boolean isValidImageUrl(String url) {
        try {
            new java.net.URL(url);
            return url.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|webp).*") &&
                    url.startsWith("http");
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }

    private String translateGender(String gender) {
        if (gender == null) return null;

        return switch (gender.toLowerCase()) {
            case "male", "чоловічий", "мужской" -> "Чоловічий";
            case "female", "жіночий", "женский" -> "Жіночий";
            case "unisex", "унісекс" -> "Унісекс";
            default -> gender;
        };
    }

    private String translateSeason(String season) {
        if (season == null) return null;

        return switch (season.toLowerCase()) {
            case "spring", "весна" -> "Весна";
            case "summer", "літо", "лето" -> "Літо";
            case "autumn", "fall", "осінь", "осень" -> "Осінь";
            case "winter", "зима" -> "Зима";
            case "all-season", "всесезон", "демісезон" -> "Всесезонний";
            default -> season;
        };
    }

    private String translateAttributeName(String attributeName) {
        Map<String, String> translations = Map.of(
                "care_instructions", "Догляд",
                "usage_instructions", "Використання",
                "size_guide", "Розмірна сітка",
                "styling_tips", "Поради стиліста",
                "target_audience", "Цільова аудиторія",
                "selling_points", "Переваги",
                "usp", "Унікальна пропозиція",
                "brand", "Бренд",
                "model", "Модель",
                "country", "Країна виробника"
        );

        return translations.getOrDefault(attributeName.toLowerCase(),
                capitalize(attributeName.replace("_", " ")));
    }

    private boolean isBasicAttribute(String key) {
        Set<String> basicAttributes = Set.of(
                "color", "material", "brand", "model", "gender", "season", "style", "occasion"
        );
        return basicAttributes.contains(key.toLowerCase());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Validate HoroshopProduct before export
     */
    public ValidationResult validateForHoroshopExport(HoroshopProduct product) {
        ValidationResult result = new ValidationResult();

        // ===== REQUIRED FIELDS =====
        if (product.getArticle() == null || product.getArticle().trim().isEmpty()) {
            result.addError("Article (SKU) обов'язковий для Horoshop");
        }

        if (product.getTitle() == null || product.getTitle().isEmpty()) {
            result.addError("Назва товару обов'язкова");
        } else {
            boolean hasValidTitle = product.getTitle().values().stream()
                    .anyMatch(title -> title != null && title.trim().length() >= 10);
            if (!hasValidTitle) {
                result.addError("Потрібна назва довжиною мінімум 10 символів");
            }
        }

        if (product.getPrice() == null || product.getPrice() <= 0) {
            result.addError("Ціна обов'язкова та має бути більше 0");
        }

        // ===== BUSINESS VALIDATION =====
        if (product.getQuantity() != null && product.getQuantity() < 0) {
            result.addError("Кількість не може бути від'ємною");
        }

        if (product.getPrice() != null && product.getPriceOld() != null &&
                product.getPriceOld() <= product.getPrice()) {
            result.addWarning("Стара ціна повинна бути вищою за поточну");
        }

        // ===== CONTENT VALIDATION =====
        if (product.getDescription() != null) {
            product.getDescription().values().forEach(desc -> {
                if (desc != null && desc.length() > 4000) {
                    result.addWarning("Опис перевищує рекомендовану довжину (4000 символів)");
                }
            });
        }

        // ===== IMAGE VALIDATION =====
        if (product.getImages() != null && product.getImages().getLinks() != null) {
            for (String imageUrl : product.getImages().getLinks()) {
                if (!isValidImageUrl(imageUrl)) {
                    result.addWarning("Невалідний URL зображення: " + imageUrl);
                }
            }

            if (product.getImages().getLinks().size() > 10) {
                result.addWarning("Занадто багато зображень (максимум 10 для Horoshop)");
            }
        }

        // ===== SEO VALIDATION =====
        if (product.getSlug() != null && product.getSlug().length() > 100) {
            result.addWarning("SEO slug занадто довгий");
        }

        return result;
    }

    /**
     * Create variant group for related products
     */
    public List<HoroshopProduct> createVariantGroup(List<Product> variants, DataSet dataset) {
        if (variants.isEmpty()) {
            return new ArrayList<>();
        }

        List<HoroshopProduct> result = new ArrayList<>();

        // Find main product (best quality representative)
        Product mainProduct = variants.stream()
                .max(Comparator.comparing(this::calculateProductQuality))
                .orElse(variants.get(0));

        // Create main product
        HoroshopProduct parentProduct = toHoroshopProduct(mainProduct, dataset);
        parentProduct.setParentArticle(mainProduct.getExternalGroupId());
        result.add(parentProduct);

        // Create variants
        for (Product variant : variants) {
            if (!variant.getId().equals(mainProduct.getId())) {
                HoroshopProduct childProduct = toHoroshopProduct(variant, dataset);
                childProduct.setParentArticle(mainProduct.getHoroshopArticleOrDefault());

                // Add variant-specific characteristics
                addVariantSpecificCharacteristics(childProduct, variant, mainProduct);
                result.add(childProduct);
            }
        }

        return result;
    }

    private void addVariantSpecificCharacteristics(HoroshopProduct childProduct,
                                                   Product variant, Product mainProduct) {
        List<HoroshopCharacteristic> variantCharacteristics = new ArrayList<>(childProduct.getCharacteristics());

        // Size differences
        if (variant.getNormalizedSize() != null &&
                !Objects.equals(variant.getNormalizedSize(), mainProduct.getNormalizedSize())) {
            variantCharacteristics.add(new HoroshopCharacteristic("Розмір варіанта", variant.getNormalizedSize()));
        }

        // Color differences
        if (variant.getColor() != null && !Objects.equals(variant.getColor(), mainProduct.getColor())) {
            variantCharacteristics.add(new HoroshopCharacteristic("Колір варіанта", variant.getColor()));
        }

        // Material differences
        if (variant.getMaterial() != null && !Objects.equals(variant.getMaterial(), mainProduct.getMaterial())) {
            variantCharacteristics.add(new HoroshopCharacteristic("Матеріал варіанта", variant.getMaterial()));
        }

        // Additional attribute differences
        variant.getAttributes().forEach((key, value) -> {
            String mainValue = mainProduct.getAttributes().get(key);
            if (value != null && !Objects.equals(value, mainValue)) {
                String translatedKey = translateAttributeName(key) + " варіанта";
                variantCharacteristics.add(new HoroshopCharacteristic(translatedKey, value));
            }
        });

        childProduct.setCharacteristics(variantCharacteristics);
    }

    /**
     * Batch convert products to Horoshop format
     */
    public List<HoroshopProduct> toHoroshopProducts(List<Product> products, DataSet dataset) {
        return products.parallelStream()
                .filter(Product::isReadyForHoroshop)
                .map(product -> {
                    try {
                        return toHoroshopProduct(product, dataset);
                    } catch (Exception e) {
                        log.error("Failed to convert product {} to Horoshop format: {}",
                                product.getId(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Create test product for API validation
     */
    public HoroshopProduct createTestProduct() {
        HoroshopProduct testProduct = new HoroshopProduct();

        testProduct.setArticle("TEST_HOROSHOP_001");
        testProduct.setParentArticle("TEST_GROUP_001");

        // Multilingual titles
        Map<String, String> titles = new HashMap<>();
        titles.put("ua", "Тестовий товар для інтеграції з Horoshop");
        titles.put("ru", "Тестовый товар для интеграции с Horoshop");
        titles.put("en", "Test Product for Horoshop Integration");
        testProduct.setTitle(titles);

        // Multilingual descriptions
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("ua", "Детальний опис тестового товару для перевірки всіх можливостей Horoshop API. " +
                "Цей товар створений для валідації повної функціональності системи.");
        descriptions.put("ru", "Подробное описание тестового товара для проверки всех возможностей Horoshop API. " +
                "Этот товар создан для валидации полной функциональности системы.");
        descriptions.put("en", "Detailed description of test product for validating all Horoshop API capabilities. " +
                "This product is created for full system functionality validation.");
        testProduct.setDescription(descriptions);

        // Short descriptions
        Map<String, String> shortDescriptions = new HashMap<>();
        shortDescriptions.put("ua", "Тестовий товар для валідації Horoshop API");
        shortDescriptions.put("ru", "Тестовый товар для валидации Horoshop API");
        shortDescriptions.put("en", "Test product for Horoshop API validation");
        testProduct.setShortDescription(shortDescriptions);

        // Pricing
        testProduct.setPrice(500.0);
        testProduct.setPriceOld(600.0);

        // Inventory
        testProduct.setQuantity(10);
        testProduct.setPresence("В наличии");

        // Category
        testProduct.setParent("Тестова категорія / Підкатегорія");

        // Images
        HoroshopImages images = new HoroshopImages();
        images.setOverride(false);
        images.setLinks(Arrays.asList(
                "https://via.placeholder.com/400x400/FF0000/FFFFFF?text=TEST1",
                "https://via.placeholder.com/400x400/00FF00/FFFFFF?text=TEST2",
                "https://via.placeholder.com/400x400/0000FF/FFFFFF?text=TEST3"
        ));
        testProduct.setImages(images);

        // Settings
        testProduct.setDisplayInShowcase(true);
        testProduct.setForceAliasUpdate(false);
        testProduct.setPopularity(75);

        // Marketing
        testProduct.setIcons(Arrays.asList("Тест", "Новинка", "API"));
        testProduct.setExportToMarketplace("Facebook Feed;Google Shopping;Test Platform");

        // Characteristics
        List<HoroshopCharacteristic> characteristics = new ArrayList<>();
        characteristics.add(new HoroshopCharacteristic("Тип", "Тестовий товар"));
        characteristics.add(new HoroshopCharacteristic("Призначення", "API валідація"));
        characteristics.add(new HoroshopCharacteristic("Статус", "Активний"));
        characteristics.add(new HoroshopCharacteristic("Версія API", "4.0"));
        testProduct.setCharacteristics(characteristics);

        // SEO
        testProduct.setSlug("test-product-horoshop-api-validation");

        // Meta data
        Map<String, String> metaData = new HashMap<>();
        metaData.put("test_mode", "true");
        metaData.put("api_version", "4.0");
        metaData.put("created_at", LocalDateTime.now().toString());

        return testProduct;
    }

    private int calculateProductQuality(Product product) {
        int score = 0;

        // Image quality and count
        if (product.getImageUrls() != null) {
            score += product.getImageUrls().size() * 5;

            long validImages = product.getImageUrls().stream()
                    .filter(this::isValidImageUrl)
                    .count();
            score += (int) validImages * 5;
        }

        // Description quality
        if (product.getDescriptionUa() != null) {
            score += Math.min(product.getDescriptionUa().length() / 10, 30);
        }
        if (product.getDescriptionRu() != null) {
            score += Math.min(product.getDescriptionRu().length() / 10, 20);
        }

        // AI optimization
        if (product.getAiAnalysisDate() != null) {
            score += 25;
        }

        // SEO optimization
        if (product.getSeoTitleUa() != null && product.getSeoTitleUa().length() > 20) {
            score += 15;
        }

        // Product completeness
        if (product.getDetectedBrandName() != null) score += 10;
        if (product.getColor() != null) score += 5;
        if (product.getMaterial() != null) score += 5;
        if (product.getTrendScore() != null) score += 10;

        // Keywords and tags
        score += Math.min(product.getKeywordsUa().size() * 2, 20);
        score += Math.min(product.getTags().size() * 2, 16);

        return score;
    }

    // ===== VALIDATION RESULT CLASS =====
    @lombok.Data
    public static class ValidationResult {
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public String getSummary() {
            StringBuilder summary = new StringBuilder();

            if (hasErrors()) {
                summary.append("Errors: ").append(String.join("; ", errors));
            }

            if (hasWarnings()) {
                if (!summary.isEmpty()) summary.append(" | ");
                summary.append("Warnings: ").append(String.join("; ", warnings));
            }

            return !summary.isEmpty() ? summary.toString() : "Valid";
        }
    }

    /**
     * Get mapping statistics for monitoring
     */
    public Map<String, Object> getMappingStatistics(List<Product> products) {
        Map<String, Object> stats = new HashMap<>();

        if (products == null || products.isEmpty()) {
            stats.put("totalProducts", 0);
            return stats;
        }

        long readyForHoroshop = products.stream().filter(Product::isReadyForHoroshop).count();
        long withAIAnalysis = products.stream().filter(p -> p.getAiAnalysisDate() != null).count();
        long withImages = products.stream().filter(p -> !p.getImageUrls().isEmpty()).count();
        long withBrands = products.stream().filter(p -> p.getDetectedBrandName() != null).count();

        double avgTrendScore = products.stream()
                .filter(p -> p.getTrendScore() != null)
                .mapToDouble(p -> p.getTrendScore().doubleValue())
                .average()
                .orElse(0.0);

        stats.put("totalProducts", products.size());
        stats.put("readyForHoroshop", readyForHoroshop);
        stats.put("readyPercentage", (readyForHoroshop * 100.0) / products.size());
        stats.put("withAIAnalysis", withAIAnalysis);
        stats.put("aiAnalysisPercentage", (withAIAnalysis * 100.0) / products.size());
        stats.put("withImages", withImages);
        stats.put("imagesPercentage", (withImages * 100.0) / products.size());
        stats.put("withBrands", withBrands);
        stats.put("brandsPercentage", (withBrands * 100.0) / products.size());
        stats.put("averageTrendScore", Math.round(avgTrendScore * 100.0) / 100.0);

        return stats;
    }
}