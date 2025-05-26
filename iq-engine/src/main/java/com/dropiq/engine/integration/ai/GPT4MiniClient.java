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

    @Value("${openai.max-tokens:2000}")
    private int maxTokens;

    public GPT4MiniClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }


    public String getProviderName() {
        return "GPT-4-Mini-Fashion";
    }


    public boolean supportsVision() {
        return true; // GPT-4o-mini підтримує зображення
    }


    public int getMaxTextLength() {
        return 8000;
    }


    public double getCostPerRequest() {
        return 0.002; // Приблизна вартість
    }


    public FeatureProductAnalysisResult analyzeProduct(String productName, String description,
                                                String categoryName, Double price) {
        try {
            log.info("GPT-4 Mini: Text analysis for clothing: {}", productName);

            String prompt = buildFashionTextPrompt(productName, description, categoryName, price);
            String response = callGPTAPI(prompt, null);

            return parseFashionResponse(response);

        } catch (Exception e) {
            log.error("GPT-4 Mini text analysis failed: {}", e.getMessage());
            return createFallbackResult(productName, categoryName, price);
        }
    }


    public FeatureProductAnalysisResult analyzeProductWithImage(String productName, String description,
                                                         String imageUrl, Double price) {
        try {
            log.info("GPT-4 Mini Vision: Analyzing clothing image for: {}", productName);

            String prompt = buildFashionVisionPrompt(productName, description, price);
            String response = callGPTVisionAPI(prompt, imageUrl);

            return parseFashionVisionResponse(response);

        } catch (Exception e) {
            log.error("GPT-4 Mini vision analysis failed: {}", e.getMessage());
            return analyzeProduct(productName, description, "Одяг", price);
        }
    }


    public FeatureProductAnalysisResult generateSEOContent(String productName, String description,
                                                    String category, Double price) {
        try {
            log.info("GPT-4 Mini: Generating fashion SEO for: {}", productName);

            String prompt = buildFashionSEOPrompt(productName, description, category, price);
            String response = callGPTAPI(prompt, null);

            return parseFashionSEOResponse(response);

        } catch (Exception e) {
            log.error("GPT-4 Mini SEO generation failed: {}", e.getMessage());
            return createFallbackSEOResult(productName, category);
        }
    }

    /**
     * Експертний промпт для аналізу зображення одягу
     */
    private String buildFashionVisionPrompt(String name, String description, Double price) {
        return String.format("""
            Ти провідний fashion-експерт та стиліст для українського ринку та SEO спеціаліст. Проаналізуй це зображення одягу як професіонал.
           \s
            КОНТЕКСТ:
            Товар: %s
            Опис: %s
            Ціна: %.0f ₴
           \s
            ЗАВДАННЯ: Детальний аналіз одягу на фото для створення професійного опису в інтернет-магазині.
           \s
            ПРОАНАЛІЗУЙ ТА ПОВЕРНИ JSON:
            {
              "product_type": "точний тип одягу (сукня, сорочка, джинси тощо)",
              "gender": "жіночий/чоловічий/унісекс",
              "clothing_category": "категорія (верхній одяг, штани, сукні, взуття тощо)",
              "primary_color": "основний колір українською",
              "secondary_color": "додатковий колір або null",
              "pattern": "тип принту (однотонний, клітка, горошок, квітмковий тощо)",
              "style": "стиль (casual, formal, sport, elegant, vintage тощо)",
              "season": "сезон (весна, літо, осінь, зима, демісезон)",
              "material_type": "тип матеріалу (бавовна, поліестер, шовк тощо)",
              "visual_quality": 8.5,
              "fit_type": "тип посадки (облягаючий, вільний, прямий)",
              "length": "довжина (коротка, середня, довга) або null",
             \s
              "seo_title": "SEO заголовок 50-60 символів українською",
              "product_title": "Комерційна назва товару 30-50 символів",
              "description": "Детальний професійний опис 200-350 слів",
              "short_description": "Короткий опис 50-80 слів",
              "meta_description": "Meta опис 140-160 символів",
              "h1_title": "H1 заголовок для сторінки",
             \s
              "main_category": "Головна категорія (Жіночий одяг, Чоловічий одяг тощо)",
              "sub_category": "Підкategорія (Сукні, Сорочки, Штани тощо)",
              "micro_category": "Мікрокатегорія (Літні сукні, Класичні сорочки тощо)",
              "category_path": "Повний шлях категорії",
             \s
              "primary_keywords": ["основні", "ключові", "слова"],
              "long_tail_keywords": ["довгий", "хвіст", "ключових", "слів"],
              "tags": ["теги", "для", "фільтрації"],
              "style_keywords": ["стільові", "ключові", "слова"],
             \s
              "target_audience": "Цільова аудиторія детально",
              "selling_points": ["топ-3", "переваги", "товару"],
              "occasion_description": "Для яких випадків підходить",
              "styling_tips": "Поради зі стилізації та комбінування",
              "care_instructions": "Рекомендації по догляду",
              "size_guide": "Поради щодо вибору розміру",
             \s
              "trend_score": 8.5,
              "conversion_score": 7.8,
              "price_category": "бюджетний/середній/преміум",
              "seasonal_relevance": true,
              "competitive_advantage": "Головна конкурентна перевага",
             \s
              "brand_name": "Назва бренду якщо видно або null",
              "model_name": "Назва моделі якщо є або null",
              "available_sizes": ["XS", "S", "M", "L", "XL"],
              "fabric_composition": "Склад тканини якщо відомий",
             \s
              "unique_selling_point": "Унікальна торгова пропозиція",
              "emotional_trigger": "Емоційний тригер для покупки",
              "urgency_message": "Повідомлення про терміновість",
              "cross_sell_items": ["супутні", "товари"],
             \s
              "analysis_confidence": 0.95
            }
           \s
            ВИМОГИ:
            - Українська мова (сучасна, не застаріла)
            - Емоційний та переконливий контент
            - Професійна fashion термінологія
            - SEO-оптимізація без переспаму
            - Фокус на продажах та конверсії
            - Створення FOMO (страх пропустити)
           \s""",
                name != null ? name : "Стильний одяг",
                description != null ? description : "Модний одяг преміум якості",
                price != null ? price : 0);
    }

    /**
     * Промпт для текстового аналізу одягу
     */
    private String buildFashionTextPrompt(String name, String description, String category, Double price) {
        return String.format("""
            Ти експерт fashion e-commerce для українського ринку. Створи професійний контент для одягу.
            
            ТОВАР:
            Назва: %s
            Опис: %s
            Категорія: %s
            Ціна: %.0f ₴
            
            СТВОРИ ПРОФЕСІЙНИЙ КОНТЕНТ У JSON:
            {
              "product_type": "тип одягу на основі назви та опису",
              "gender": "стать на основі аналізу",
              "seo_title": "SEO заголовок 50-60 символів",
              "description": "Детальний опис 200-350 слів",
              "meta_description": "Meta опис 140-160 символів",  
              "main_category": "Головна категорія",
              "sub_category": "Підкатегорія",
              "primary_keywords": ["ключові", "слова"],
              "selling_points": ["переваги", "товару"],
              "target_audience": "Цільова аудиторія",
              "trend_score": 7.5,
              "competitive_advantage": "Конкурентна перевага"
            }
            """, name, description != null ? description : "", category != null ? category : "", price != null ? price : 0);
    }

    /**
     * SEO промпт для одягу
     */
    private String buildFashionSEOPrompt(String name, String description, String category, Double price) {
        return String.format("""
            Створи професійний SEO-контент для fashion e-commerce в Україні.
            
            ТОВАР: %s
            КАТЕГОРІЯ: %s
            ЦІНА: %.0f ₴
            
            ПОВЕРНИ ПОВНИЙ SEO-ПАКЕТ У JSON:
            {
              "seo_title": "SEO заголовок з ключовими словами",
              "h1_title": "H1 заголовок сторінки",
              "meta_description": "Meta опис з CTA",
              "primary_keywords": ["основні", "ключові", "слова"],
              "long_tail_keywords": ["довгий", "хвіст"],
              "structured_description": "Структурований опис з підзаголовками",
              "alt_text": "ALT текст для зображень",
              "breadcrumbs": "Хлібні крихти",
              "related_products": ["пов'язані", "товари"]
            }
            """, name, category != null ? category : "Одяг", price != null ? price : 0);
    }

    /**
     * Виклик GPT API без зображення
     */
    private String callGPTAPI(String prompt, String systemPrompt) {
        HttpHeaders headers = createHeaders();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : getFashionSystemPrompt()));
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", 0.2);
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

    /**
     * Виклик GPT Vision API з зображенням
     */
    private String callGPTVisionAPI(String prompt, String imageUrl) {
        HttpHeaders headers = createHeaders();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", getFashionVisionSystemPrompt()));

        // Повідомлення з текстом та зображенням
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl, "detail", "high")));

        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", 0.1); // Низька для точності vision
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

    /**
     * Парсинг відповіді vision аналізу
     */
    private FeatureProductAnalysisResult parseFashionVisionResponse(String response) {
        try {
            // Витягуємо JSON з відповіді
            String jsonStr = extractJsonFromResponse(response);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

            // Vision аналіз
            result.setProductType(getJsonString(jsonNode, "product_type"));
            result.setGender(getJsonString(jsonNode, "gender"));
            result.setClothingCategory(getJsonString(jsonNode, "clothing_category"));
            result.setPrimaryColor(getJsonString(jsonNode, "primary_color"));
            result.setSecondaryColor(getJsonString(jsonNode, "secondary_color"));
            result.setPattern(getJsonString(jsonNode, "pattern"));
            result.setStyle(getJsonString(jsonNode, "style"));
            result.setSeason(getJsonString(jsonNode, "season"));
            result.setMaterialType(getJsonString(jsonNode, "material_type"));
            result.setVisualQuality(getJsonDouble(jsonNode, "visual_quality", 7.0));
            result.setFitType(getJsonString(jsonNode, "fit_type"));
            result.setLength(getJsonString(jsonNode, "length"));

            // SEO контент
            result.setSeoTitle(getJsonString(jsonNode, "seo_title"));
            result.setProductTitle(getJsonString(jsonNode, "product_title"));
            result.setDescription(getJsonString(jsonNode, "description"));
            result.setShortDescription(getJsonString(jsonNode, "short_description"));
            result.setMetaDescription(getJsonString(jsonNode, "meta_description"));
            result.setH1Title(getJsonString(jsonNode, "h1_title"));

            // Категорії
            result.setMainCategory(getJsonString(jsonNode, "main_category"));
            result.setSubCategory(getJsonString(jsonNode, "sub_category"));
            result.setMicroCategory(getJsonString(jsonNode, "micro_category"));
            result.setCategoryPath(getJsonString(jsonNode, "category_path"));

            // Ключові слова
            result.setPrimaryKeywords(getJsonStringList(jsonNode, "primary_keywords"));
            result.setLongTailKeywords(getJsonStringList(jsonNode, "long_tail_keywords"));
            result.setTags(getJsonStringList(jsonNode, "tags"));
            result.setStyleKeywords(getJsonStringList(jsonNode, "style_keywords"));

            // Комерційний контент
            result.setTargetAudience(getJsonString(jsonNode, "target_audience"));
            result.setSellingPoints(getJsonStringList(jsonNode, "selling_points"));
            result.setOccasionDescription(getJsonString(jsonNode, "occasion_description"));
            result.setStylingTips(getJsonString(jsonNode, "styling_tips"));
            result.setCareInstructions(getJsonString(jsonNode, "care_instructions"));
            result.setSizeGuide(getJsonString(jsonNode, "size_guide"));

            // Аналітика
            result.setTrendScore(getJsonDouble(jsonNode, "trend_score", 5.0));
            result.setConversionScore(getJsonDouble(jsonNode, "conversion_score", 5.0));
            result.setPriceCategory(getJsonString(jsonNode, "price_category"));
            result.setSeasonalRelevance(getJsonBoolean(jsonNode, "seasonal_relevance"));
            result.setCompetitiveAdvantage(getJsonString(jsonNode, "competitive_advantage"));

            // Додаткові дані
            result.setBrandName(getJsonString(jsonNode, "brand_name"));
            result.setModelName(getJsonString(jsonNode, "model_name"));
            result.setAvailableSizes(getJsonStringList(jsonNode, "available_sizes"));
            result.setFabricComposition(getJsonString(jsonNode, "fabric_composition"));

            // Marketing
            result.setUniqueSellingPoint(getJsonString(jsonNode, "unique_selling_point"));
            result.setEmotionalTrigger(getJsonString(jsonNode, "emotional_trigger"));
            result.setUrgencyMessage(getJsonString(jsonNode, "urgency_message"));
            result.setCrossSellItems(getJsonStringList(jsonNode, "cross_sell_items"));

            result.setAnalysisConfidence(getJsonDouble(jsonNode, "analysis_confidence", 0.8));

            return result;

        } catch (Exception e) {
            log.error("Error parsing fashion vision response: {}", e.getMessage());
            return createFallbackResult("", "", 0.0);
        }
    }

    // Допоміжні методи
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    private String getFashionSystemPrompt() {
        return """
            Ти провідний fashion-експерт та копірайтер для українського e-commerce ринку одягу.
            
            ТВОЯ ЕКСПЕРТИЗА:
            - 15+ років в fashion індустрії
            - Глибоке розуміння українських fashion трендів
            - Експерт з SEO для fashion сайтів
            - Знання психології покупок одягу
            - Досвід роботи з Horoshop платформою
            
            ПРИНЦИПИ РОБОТИ:
            1. Створюй контент, який продає
            2. Використовуй емоційні тригери
            3. Підкреслюй стиль та якість
            4. Оптимізуй для пошукових систем
            5. Говори мовою цільової аудиторії
            6. Створюй FOMO та urgency
            
            ЗАВЖДИ ПОВЕРТАЙ ТІЛЬКИ ВАЛІДНИЙ JSON.
            """;
    }

    private String getFashionVisionSystemPrompt() {
        return """
            Ти професійний fashion-стиліст та експерт з аналізу зображень одягу.
            
            ТВОЇ НАВИЧКИ:
            - Визначення типів одягу та стилів
            - Аналіз кольорів та матеріалів
            - Розуміння fashion трендів
            - Створення комерційних описів
            - SEO оптимізація для одягу
            
            АНАЛІЗУЙ ЗОБРАЖЕННЯ ДЕТАЛЬНО ТА ПРОФЕСІЙНО.
            ПОВЕРНИ ТІЛЬКИ ВАЛІДНИЙ JSON БЕЗ ДОДАТКОВОГО ТЕКСТУ.
            """;
    }

    private FeatureProductAnalysisResult parseFashionResponse(String response) {
        try {
            String jsonStr = extractJsonFromResponse(response);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

            // Базові поля
            result.setProductType(getJsonString(jsonNode, "product_type"));
            result.setGender(getJsonString(jsonNode, "gender"));
            result.setSeoTitle(getJsonString(jsonNode, "seo_title"));
            result.setDescription(getJsonString(jsonNode, "description"));
            result.setMetaDescription(getJsonString(jsonNode, "meta_description"));
            result.setMainCategory(getJsonString(jsonNode, "main_category"));
            result.setSubCategory(getJsonString(jsonNode, "sub_category"));
            result.setPrimaryKeywords(getJsonStringList(jsonNode, "primary_keywords"));
            result.setSellingPoints(getJsonStringList(jsonNode, "selling_points"));
            result.setTargetAudience(getJsonString(jsonNode, "target_audience"));
            result.setTrendScore(getJsonDouble(jsonNode, "trend_score", 5.0));
            result.setCompetitiveAdvantage(getJsonString(jsonNode, "competitive_advantage"));

            return result;

        } catch (Exception e) {
            log.error("Error parsing fashion response: {}", e.getMessage());
            return createFallbackResult("", "", 0.0);
        }
    }

    private FeatureProductAnalysisResult parseFashionSEOResponse(String response) {
        try {
            String jsonStr = extractJsonFromResponse(response);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

            result.setSeoTitle(getJsonString(jsonNode, "seo_title"));
            result.setH1Title(getJsonString(jsonNode, "h1_title"));
            result.setMetaDescription(getJsonString(jsonNode, "meta_description"));
            result.setPrimaryKeywords(getJsonStringList(jsonNode, "primary_keywords"));
            result.setLongTailKeywords(getJsonStringList(jsonNode, "long_tail_keywords"));

            return result;

        } catch (Exception e) {
            log.error("Error parsing SEO response: {}", e.getMessage());
            return createFallbackSEOResult("", "");
        }
    }

    // Утиліти
    private String extractJsonFromResponse(String response) {
        // Знаходимо JSON в відповіді
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}') + 1;

        if (jsonStart != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd);
        }

        return response; // Якщо вся відповідь це JSON
    }

    private String extractContentFromResponse(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract content from GPT response", e);
        }
    }

    private String getJsonString(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
    }

    private Double getJsonDouble(JsonNode node, String field, Double defaultValue) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asDouble() : defaultValue;
    }

    private Boolean getJsonBoolean(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asBoolean() : false;
    }

    private List<String> getJsonStringList(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && fieldNode.isArray()) {
            List<String> result = new ArrayList<>();
            fieldNode.forEach(item -> result.add(item.asText()));
            return result;
        }
        return new ArrayList<>();
    }

    private FeatureProductAnalysisResult createFallbackResult(String name, String category, Double price) {
        FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

        result.setProductType(name != null ? name : "Стильний одяг");
        result.setGender("унісекс");
        result.setSeoTitle("Купити " + (name != null ? name : "одяг") + " | Модний стиль онлайн");
        result.setDescription("Якісний та стильний одяг преміум класу за доступною ціною. " +
                "Сучасний дизайн, комфортні матеріали, швидка доставка по Україні. " +
                "Ідеально підходить для створення модного образу на кожен день.");
        result.setShortDescription("Стильний одяг преміум якості за доступною ціною");
        result.setMetaDescription("Купити " + (name != null ? name.toLowerCase() : "одяг") +
                " недорого ✓ Якісні матеріали ✓ Швидка доставка ✓ Гарантія якості");
        result.setMainCategory(category != null ? category : "Одяг");
        result.setSubCategory("Загальний");
        result.setPrimaryKeywords(Arrays.asList("одяг", "купити", "стиль", "мода", "якість"));
        result.setSellingPoints(Arrays.asList("Преміум якість", "Сучасний дизайн", "Доступна ціна"));
        result.setTargetAudience("Стильні люди, які цінують якість та комфорт");
        result.setTrendScore(6.0);
        result.setConversionScore(7.0);
        result.setPriceCategory(price != null && price > 1500 ? "преміум" : "доступний");
        result.setCompetitiveAdvantage("Унікальне поєднання стилю, якості та доступної ціни");
        result.setAnalysisConfidence(0.6);

        return result;
    }

    private FeatureProductAnalysisResult createFallbackSEOResult(String name, String category) {
        FeatureProductAnalysisResult result = new FeatureProductAnalysisResult();

        result.setSeoTitle("Купити " + name + " | Стильний одяг онлайн в Україні");
        result.setH1Title("Стильний " + name + " - преміум якість за доступною ціною");
        result.setMetaDescription("Купити " + name + " недорого з доставкою по Україні ✓ " +
                "Великий вибір ✓ Гарантія якості ✓ Швидка доставка");
        result.setPrimaryKeywords(Arrays.asList("купити " + name, name + " україна",
                name + " недорого", name + " онлайн"));
        result.setLongTailKeywords(Arrays.asList(name + " з доставкою",
                "модний " + name + " 2024",
                name + " преміум якості"));

        return result;
    }
}