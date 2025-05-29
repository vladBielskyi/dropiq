package com.dropiq.engine.integration.ai;

import com.dropiq.engine.integration.ai.model.FeatureProductAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class GPT4MiniClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.max-tokens:3000}")
    private int maxTokens;

    public GPT4MiniClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public String getProviderName() {
        return "GPT-4-Mini-Horoshop";
    }

    public boolean supportsVision() {
        return true;
    }

    public int getMaxTextLength() {
        return 10000;
    }

    public double getCostPerRequest() {
        return 0.002;
    }

    /**
     * Comprehensive product analysis for Horoshop integration
     */
    public FeatureProductAnalysisResult analyzeProductForHoroshop(String productName, String description,
                                                                   String categoryName, Double price, String sourceType) {
        try {
            log.info("GPT-4 Mini: Comprehensive Horoshop analysis for: {}", productName);

            String prompt = buildHoroshopAnalysisPrompt(productName, description, categoryName, price, sourceType);
            String response = callGPTAPI(prompt, getHoroshopSystemPrompt());

            return parseHoroshopAnalysisResponse(response, productName, price);

        } catch (Exception e) {
            log.error("GPT-4 Mini Horoshop analysis failed: {}", e.getMessage());
            return createFallbackHoroshopResult(productName, categoryName, price);
        }
    }

    /**
     * Vision-based analysis with image for complete product understanding
     */
    public FeatureProductAnalysisResult analyzeProductWithImageForHoroshop(String productName, String description,
                                                                           String imageUrl, Double price, String sourceType) {
        try {
            log.info("GPT-4 Mini Vision: Analyzing product image for Horoshop: {}", productName);

            String prompt = buildHoroshopVisionPrompt(productName, description, price, sourceType);
            String response = callGPTVisionAPI(prompt, imageUrl);

            return parseHoroshopAnalysisResponse(response, productName, price);

        } catch (Exception e) {
            log.error("GPT-4 Mini vision analysis failed: {}", e.getMessage());
            return analyzeProductForHoroshop(productName, description, "Товари", price, sourceType);
        }
    }

    /**
     * Expert prompt for comprehensive Horoshop product analysis
     */
    private String buildHoroshopAnalysisPrompt(String name, String description, String category, Double price, String sourceType) {
        return String.format("""
            Ти експерт e-commerce копірайтер та маркетолог для української платформи Horoshop. Твоя задача - створити повний комерційний аналіз товару.
            
            ВАЖЛИВО: Назва товару може бути технічною (наприклад "3124", "SKU-456") - НЕ використовуй такі назви в комерційному контенті. Створи НОВУ привабливу назву на основі опису та категорії.
            
            ВХІДНІ ДАНІ:
            Початкова назва: %s
            Опис: %s
            Категорія: %s
            Ціна: %.0f ₴
            Джерело: %s
            
            СТВОРИ ПОВНИЙ JSON ДЛЯ HOROSHOP:
            {
              "commercialTitle": "Привабливий комерційний заголовок 30-60 символів (ІГНОРУЙ технічні назви як 3124)",
              "seoTitle": "SEO заголовок з ключовими словами 50-70 символів",
              "h1Title": "H1 заголовок для сторінки товару",
              
              "descriptionUa": "Детальний український опис 250-400 слів з емоційними тригерами",
              "descriptionRu": "Детальний російський опис 250-400 слів",
              "descriptionEn": "English description 200-300 words",
              
              "shortDescriptionUa": "Короткий український опис 60-100 слів",
              "shortDescriptionRu": "Короткий російський опис 60-100 слів", 
              "shortDescriptionEn": "Short English description 50-80 words",
              
              "metaDescriptionUa": "Meta опис українською 140-160 символів з CTA",
              "metaDescriptionRu": "Meta описание на русском 140-160 символов",
              "metaDescriptionEn": "Meta description in English 140-160 characters",
              
              "primaryKeywordsUa": ["українські", "ключові", "слова"],
              "primaryKeywordsRu": ["русские", "ключевые", "слова"],
              "primaryKeywordsEn": ["english", "keywords"],
              
              "longTailKeywordsUa": ["довгий хвіст українською"],
              "longTailKeywordsRu": ["длинный хвост на русском"],
              "longTailKeywordsEn": ["long tail english"],
              
              "tagsUa": ["теги", "українською"],
              "tagsRu": ["теги", "на", "русском"],
              "tagsEn": ["english", "tags"],
              
              "mainCategory": "Головна категорія для Horoshop",
              "subCategory": "Підкатегорія",
              "microCategory": "Мікрокатегорія",
              "categoryPathUa": "Повний шлях категорії українською",
              "categoryPathRu": "Полный путь категории на русском",
              
              "brandName": "Назва бренду або null",
              "modelName": "Назва моделі або null",
              "detectedGender": "чоловічий/жіночий/унісекс/null",
              "color": "основний колір або null",
              "material": "матеріал або null",
              "style": "стиль або null",
              "season": "сезон або null",
              "occasion": "випадок використання або null",
              
              "attributes": {
                "розмір": "якщо відомо",
                "країна_виробника": "якщо відомо",
                "гарантія": "якщо відомо",
                "додаткові_властивості": "value"
              },
              
              "sellingPoints": ["топ-5", "переваг", "товару", "для", "продажів"],
              "targetAudience": "Детальний опис цільової аудиторії",
              "uniqueSellingPoint": "Унікальна торгова пропозиція",
              "emotionalTrigger": "Емоційний тригер для покупки",
              "urgencyMessage": "Повідомлення про терміновість",
              
              "careInstructions": "Інструкції по догляду якщо актуально",
              "usageInstructions": "Інструкції по використанню",
              "sizeGuide": "Поради щодо вибору розміру",
              "stylingTips": "Поради зі стилізації",
              
              "trendScore": 7.5,
              "conversionPotential": 8.0,
              "seasonalRelevance": true,
              "priceCategory": "бюджетний/середній/преміум",
              "competitiveAdvantage": "Головна конкурентна перевага",
              
              "crossSellCategories": ["супутні", "категорії"],
              "relatedKeywords": ["пов'язані", "ключові", "слова"],
              
              "horoshopPresence": "В наличии/Под заказ/Нет в наличии",
              "horoshopIcons": ["іконки", "для", "товару"],
              "marketplaceExport": ["facebook", "google", "rozetka"],
              
              "qualityScore": 8.5,
              "analysisConfidence": 0.92
            }
            
            ВИМОГИ:
            1. ЗАВЖДИ створюй НОВУ привабливу назву товару, ігноруючи технічні коди
            2. Усі тексти мають бути унікальними та продаючими
            3. Використовуй емоційні тригери та FOMO
            4. Адаптуй контент під українську аудиторію Horoshop
            5. Враховуй психологію онлайн покупок
            6. Оптимізуй під SEO без переспаму
            7. Поверни ТІЛЬКИ ВАЛІДНИЙ JSON без коментарів
            """,
                name != null ? name : "Товар",
                description != null ? description : "Якісний товар",
                category != null ? category : "Загальні товари",
                price != null ? price : 0,
                sourceType != null ? sourceType : "Unknown"
        );
    }

    /**
     * Vision analysis prompt for image-based product understanding
     */
    private String buildHoroshopVisionPrompt(String name, String description, Double price, String sourceType) {
        return String.format("""
            Ти експерт-аналітик товарів для Horoshop з 10+ років досвіду. Проаналізуй це зображення товару як професіонал.
            
            КОНТЕКСТ:
            Початкова назва: %s (може бути технічною - створи НОВУ назву!)
            Опис: %s
            Ціна: %.0f ₴
            Джерело: %s
            
            ЗАВДАННЯ: Детальний аналіз зображення товару для створення повного профілю в Horoshop.
            
            ВАЖЛИВО: Якщо початкова назва виглядає як код (3124, SKU-456) - ігноруй її та створи привабливу комерційну назву на основі того, що бачиш на фото.
            
            ПРОАНАЛІЗУЙ ТА ПОВЕРНИ ПОВНИЙ JSON:
            {
              "visualAnalysis": {
                "productType": "що це за товар на основі фото",
                "detectedBrand": "бренд якщо видно або null",
                "primaryColor": "основний колір",
                "secondaryColors": ["додаткові", "кольори"],
                "material": "матеріал якщо можна визначити",
                "style": "стиль товару",
                "condition": "новий/вживаний",
                "packaging": "тип упаковки",
                "visualQuality": 8.5
              },
              
              "commercialContent": {
                "commercialTitle": "НОВА приваблива назва на основі фото (НЕ 3124!)",
                "seoTitle": "SEO заголовок з ключовими словами",
                "h1Title": "H1 заголовок",
                
                "descriptionUa": "Український опис на основі візуального аналізу",
                "descriptionRu": "Русский описания на основе фото",
                "descriptionEn": "English description based on image",
                
                "shortDescriptionUa": "Короткий опис українською",
                "shortDescriptionRu": "Краткое описание",
                "shortDescriptionEn": "Short description",
                
                "metaDescriptionUa": "Meta українською",
                "metaDescriptionRu": "Meta на русском", 
                "metaDescriptionEn": "English meta"
              },
              
              "categorization": {
                "mainCategory": "Головна категорія",
                "subCategory": "Підкатегорія", 
                "microCategory": "Мікрокатегорія",
                "categoryPathUa": "Шлях українською",
                "categoryPathRu": "Путь на русском"
              },
              
              "seoOptimization": {
                "primaryKeywordsUa": ["ключові", "слова", "українською"],
                "primaryKeywordsRu": ["ключевые", "слова", "русские"],
                "primaryKeywordsEn": ["english", "keywords"],
                "longTailKeywordsUa": ["довгий хвіст"],
                "longTailKeywordsRu": ["длинный хвост"], 
                "longTailKeywordsEn": ["long tail"],
                "altText": "ALT текст для зображення"
              },
              
              "productAttributes": {
                "detectedGender": "стать або null",
                "season": "сезон або null",
                "occasion": "випадок використання",
                "ageGroup": "вікова група",
                "sizeInfo": "інформація про розмір",
                "additionalFeatures": ["особливості", "товару"]
              },
              
              "marketingData": {
                "sellingPoints": ["переваги", "на", "основі", "фото"],
                "targetAudience": "цільова аудиторія",
                "emotionalTrigger": "емоційний тригер",
                "uniqueSellingPoint": "УТП",
                "trendScore": 8.0,
                "conversionPotential": 7.5
              },
              
              "horoshopIntegration": {
                "horoshopPresence": "В наличии",
                "horoshopIcons": ["іконки", "товару"],
                "recommendedCategories": ["рекомендовані", "категорії"],
                "crossSellItems": ["супутні", "товари"],
                "priceCategory": "категорія ціни"
              },
              
              "qualityAssessment": {
                "imageQuality": 9.0,
                "analysisConfidence": 0.95,
                "recommendationsForImprovement": ["рекомендації"]
              }
            }
            
            КРИТЕРІЇ:
            - Створи НОВУ комерційну назву, ігноруючи коди
            - Базуйся на тому, що БАЧИШ на фото
            - Не використовуй Розміри, Ціни, інші назви в описах
            - Не викорситовуй дивні назви та слова. Все повинно бути Human like
            - Використовуй професійну термінологію  
            - Адаптуй під українську аудиторію
            - Поверни ТІЛЬКИ JSON без пояснень
            """,
                name != null ? name : "Товар",
                description != null ? description : "Товар з фото",
                price != null ? price : 0,
                sourceType != null ? sourceType : "Unknown"
        );
    }

    private String getHoroshopSystemPrompt() {
        return """
            Ти провідний експерт e-commerce та копірайтер для української платформи Horoshop з 15+ років досвіду.
            
            ТВОЯ ЕКСПЕРТИЗА:
            - Глибоке розуміння українського ринку e-commerce
            - Експерт з психології онлайн покупок
            - Спеціаліст з SEO для українських сайтів
            - Досвід роботи з Horoshop API та вимогами
            - Знання трендів та поведінки покупців в Україні
            
            КЛЮЧОВІ ПРИНЦИПИ:
            1. ЗАВЖДИ створюй нові привабливі назви замість технічних кодів
            2. Фокусуйся на емоціях та продажах
            3. Використовуй українські SEO практики
            4. Створюй контент що конвертує
            5. Враховуй специфіку Horoshop платформи
            6. Адаптуй під українську ментальність покупців
            
            ЗАВЖДИ ПОВЕРТАЙ ТІЛЬКИ ВАЛІДНИЙ JSON БЕЗ ДОДАТКОВОГО ТЕКСТУ.
            """;
    }

    private String callGPTAPI(String prompt, String systemPrompt) {
        HttpHeaders headers = createHeaders();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", 0.3); // Balanced creativity
        requestBody.put("top_p", 0.9);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            return extractContentFromResponse(response.getBody());
        } catch (Exception e) {
            log.error("GPT API call failed: {}", e.getMessage());
            throw new RuntimeException("GPT API call failed", e);
        }
    }

    private String callGPTVisionAPI(String prompt, String imageUrl) {
        HttpHeaders headers = createHeaders();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", getHoroshopSystemPrompt()));

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl, "detail", "high")));

        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", 0.2); // Lower for vision accuracy
        requestBody.put("top_p", 0.95);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            return extractContentFromResponse(response.getBody());
        } catch (Exception e) {
            log.error("GPT Vision API call failed: {}", e.getMessage());
            throw new RuntimeException("GPT Vision API call failed", e);
        }
    }

    private FeatureProductAnalysisResult parseHoroshopAnalysisResponse(String response, String originalName, Double price) {
        try {
            String jsonStr = extractJsonFromResponse(response);
            JsonNode rootNode = objectMapper.readTree(jsonStr);

            FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

            // Handle both flat and nested JSON structures
            JsonNode commercialNode = rootNode.has("commercialContent") ? rootNode.get("commercialContent") : rootNode;
            JsonNode visualNode = rootNode.get("visualAnalysis");
            JsonNode categorizationNode = rootNode.get("categorization");
            JsonNode seoNode = rootNode.get("seoOptimization");
            JsonNode attributesNode = rootNode.get("productAttributes");
            JsonNode marketingNode = rootNode.get("marketingData");
            JsonNode horoshopNode = rootNode.get("horoshopIntegration");

            result.setVisualQuality(getJsonDouble(visualNode != null ? visualNode : rootNode, "visualQuality",
                    5.0));
            result.setMaterial(getJsonString(visualNode != null ? visualNode : rootNode, "material"));

            // Basic product info
            result.setCommercialTitle(getJsonString(commercialNode != null ? commercialNode : rootNode, "commercialTitle"));
            result.setSeoTitle(getJsonString(commercialNode != null ? commercialNode : rootNode, "seoTitle"));
            result.setH1Title(getJsonString(commercialNode != null ? commercialNode : rootNode, "h1Title"));

            // Multilingual descriptions
            result.setDescriptionUa(getJsonString(commercialNode != null ? commercialNode : rootNode, "descriptionUa"));
            result.setDescriptionRu(getJsonString(commercialNode != null ? commercialNode : rootNode, "descriptionRu"));
            result.setDescriptionEn(getJsonString(commercialNode != null ? commercialNode : rootNode, "descriptionEn"));

            result.setShortDescriptionUa(getJsonString(commercialNode != null ? commercialNode : rootNode, "shortDescriptionUa"));
            result.setShortDescriptionRu(getJsonString(commercialNode != null ? commercialNode : rootNode, "shortDescriptionRu"));
            result.setShortDescriptionEn(getJsonString(commercialNode != null ? commercialNode : rootNode, "shortDescriptionEn"));

            result.setMetaDescriptionUa(getJsonString(commercialNode != null ? commercialNode : rootNode, "metaDescriptionUa"));
            result.setMetaDescriptionRu(getJsonString(commercialNode != null ? commercialNode : rootNode, "metaDescriptionRu"));
            result.setMetaDescriptionEn(getJsonString(commercialNode != null ? commercialNode : rootNode, "metaDescriptionEn"));

            // Keywords
            JsonNode keywordsSource = seoNode != null ? seoNode : rootNode;
            result.setPrimaryKeywordsUa(getJsonStringList(keywordsSource, "primaryKeywordsUa"));
            result.setPrimaryKeywordsRu(getJsonStringList(keywordsSource, "primaryKeywordsRu"));
            result.setPrimaryKeywordsEn(getJsonStringList(keywordsSource, "primaryKeywordsEn"));

            result.setLongTailKeywordsUa(getJsonStringList(keywordsSource, "longTailKeywordsUa"));
            result.setLongTailKeywordsRu(getJsonStringList(keywordsSource, "longTailKeywordsRu"));
            result.setLongTailKeywordsEn(getJsonStringList(keywordsSource, "longTailKeywordsEn"));

            result.setTagsUa(getJsonStringList(keywordsSource, "tagsUa"));
            result.setTagsRu(getJsonStringList(keywordsSource, "tagsRu"));
            result.setTagsEn(getJsonStringList(keywordsSource, "tagsEn"));

            // Categories
            JsonNode categorySource = categorizationNode != null ? categorizationNode : rootNode;
            result.setMainCategory(getJsonString(categorySource, "mainCategory"));
            result.setSubCategory(getJsonString(categorySource, "subCategory"));
            result.setMicroCategory(getJsonString(categorySource, "microCategory"));
            result.setCategoryPathUa(getJsonString(categorySource, "categoryPathUa"));
            result.setCategoryPathRu(getJsonString(categorySource, "categoryPathRu"));

            // Product attributes
            JsonNode attrSource = attributesNode != null ? attributesNode : rootNode;
            result.setBrandName(getJsonString(attrSource, "brandName"));
            result.setModelName(getJsonString(attrSource, "modelName"));
            result.setDetectedGender(getJsonString(attrSource, "detectedGender"));
            result.setColor(getJsonString(visualNode, "primaryColor"));
            result.setMaterial(getJsonString(visualNode, "material"));
            result.setStyle(getJsonString(visualNode, "style"));
            result.setSeason(getJsonString(attrSource, "season"));
            result.setOccasion(getJsonString(attrSource, "occasion"));

            // Additional attributes
            if (rootNode.has("attributes")) {
                Map<String, String> attributes = new HashMap<>();
                JsonNode attrsNode = rootNode.get("attributes");
                attrsNode.fieldNames().forEachRemaining(fieldName ->
                        attributes.put(fieldName, attrsNode.get(fieldName).asText()));
                result.setAttributes(attributes);
            }

            // Marketing data
            JsonNode marketingSource = marketingNode != null ? marketingNode : rootNode;
            result.setSellingPoints(getJsonStringList(marketingSource, "sellingPoints"));
            result.setTargetAudience(getJsonString(marketingSource, "targetAudience"));
            result.setUniqueSellingPoint(getJsonString(marketingSource, "uniqueSellingPoint"));
            result.setEmotionalTrigger(getJsonString(marketingSource, "emotionalTrigger"));
            result.setUrgencyMessage(getJsonString(marketingSource, "urgencyMessage"));

            // Care and usage
            result.setCareInstructions(getJsonString(rootNode, "careInstructions"));
            result.setUsageInstructions(getJsonString(rootNode, "usageInstructions"));
            result.setSizeGuide(getJsonString(rootNode, "sizeGuide"));
            result.setStylingTips(getJsonString(rootNode, "stylingTips"));

            // Analytics
            result.setTrendScore(getJsonDouble(marketingSource, "trendScore", 5.0));
            result.setConversionPotential(getJsonDouble(marketingSource, "conversionPotential", 5.0));
            result.setSeasonalRelevance(getJsonBoolean(rootNode, "seasonalRelevance"));
            result.setPriceCategory(getJsonString(rootNode, "priceCategory"));
            result.setCompetitiveAdvantage(getJsonString(rootNode, "competitiveAdvantage"));

            // Horoshop specific
            JsonNode horoshopSource = horoshopNode != null ? horoshopNode : rootNode;
            result.setHoroshopPresence(getJsonString(horoshopSource, "horoshopPresence"));
            result.setHoroshopIcons(getJsonStringList(horoshopSource, "horoshopIcons"));
            result.setMarketplaceExport(getJsonStringList(horoshopSource, "marketplaceExport"));
            result.setCrossSellCategories(getJsonStringList(horoshopSource, "crossSellCategories"));

            // Quality metrics
            result.setQualityScore(getJsonDouble(rootNode, "qualityScore", 7.0));
            result.setAnalysisConfidence(getJsonDouble(rootNode, "analysisConfidence", 0.8));

            return result;

        } catch (Exception e) {
            log.error("Error parsing Horoshop analysis response: {}", e.getMessage());
            return createFallbackHoroshopResult(originalName, null, price);
        }
    }

    private FeatureProductAnalysisResult createFallbackHoroshopResult(String originalName, String category, Double price) {
        FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

        // Generate commercial name if original is technical
        String commercialName = generateCommercialName(originalName, category);

        result.setCommercialTitle(commercialName);
        result.setSeoTitle("Купити " + commercialName + " | Інтернет-магазин");
        result.setH1Title(commercialName + " - якість за доступною ціною");

        result.setDescriptionUa("Якісний " + commercialName.toLowerCase() + " за найкращою ціною. " +
                "Швидка доставка по Україні, гарантія якості, найкращий сервіс.");
        result.setDescriptionRu("Качественный " + commercialName.toLowerCase() + " по лучшей цене. " +
                "Быстрая доставка по Украине, гарантия качества.");

        result.setShortDescriptionUa("Якісний " + commercialName.toLowerCase() + " за доступною ціною");
        result.setShortDescriptionRu("Качественный " + commercialName.toLowerCase() + " по доступной цене");

        result.setMetaDescriptionUa("Купити " + commercialName.toLowerCase() + " недорого ✓ Швидка доставка ✓ Гарантія якості");
        result.setMetaDescriptionRu("Купить " + commercialName.toLowerCase() + " недорого ✓ Быстрая доставка ✓ Гарантия качества");

        result.setMainCategory(category != null ? category : "Товари");
        result.setSubCategory("Загальні");

        result.setPrimaryKeywordsUa(Arrays.asList("купити " + commercialName.toLowerCase(), commercialName.toLowerCase() + " україна"));
        result.setPrimaryKeywordsRu(Arrays.asList("купить " + commercialName.toLowerCase(), commercialName.toLowerCase() + " украина"));

        result.setSellingPoints(Arrays.asList("Якісні матеріали", "Доступна ціна", "Швидка доставка", "Гарантія якості"));
        result.setTargetAudience("Люди, які шукають якісні товари за доступними цінами");
        result.setTrendScore(5.0);
        result.setConversionPotential(6.0);
        result.setPriceCategory(determinePriceCategory(price));
        result.setHoroshopPresence("В наличии");
        result.setAnalysisConfidence(0.6);

        return result;
    }

    private String generateCommercialName(String originalName, String category) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "Якісний товар";
        }

        // Check if name looks technical (numbers, codes, etc.)
        if (originalName.matches("^[0-9A-Z\\-_]+$") || originalName.length() < 5) {
            if (category != null && !category.trim().isEmpty()) {
                return "Якісний " + category.toLowerCase();
            }
            return "Стильний товар";
        }

        return originalName;
    }

    private String determinePriceCategory(Double price) {
        if (price == null) return "середній";

        double priceValue = price.doubleValue();
        if (priceValue < 300) return "бюджетний";
        if (priceValue > 1500) return "преміум";
        return "середній";
    }

    // Helper methods
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private String extractContentFromResponse(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract content from GPT response", e);
        }
    }

    private String extractJsonFromResponse(String response) {
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}') + 1;

        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd);
        }

        return response;
    }

    private String getJsonString(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
    }

    private Double getJsonDouble(JsonNode node, String field, Double defaultValue) {
        if (node == null) return defaultValue;
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asDouble() : defaultValue;
    }

    private Boolean getJsonBoolean(JsonNode node, String field) {
        if (node == null) return false;
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asBoolean() : false;
    }

    private List<String> getJsonStringList(JsonNode node, String field) {
        if (node == null) return new ArrayList<>();
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && fieldNode.isArray()) {
            List<String> result = new ArrayList<>();
            fieldNode.forEach(item -> result.add(item.asText()));
            return result;
        }
        return new ArrayList<>();
    }
}