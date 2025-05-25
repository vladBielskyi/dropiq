package com.dropiq.engine.integration.ai;

import com.dropiq.engine.integration.ai.model.ProductAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Base64;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class OllamaClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ollama.vision.model:llava:7b}")
    private String visionModel;

    @Value("${ollama.text.model}")
    private String textModel;

    @Value("${ollama.timeout:300}")
    private int timeoutSeconds;

    public OllamaClient(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyze product image with vision model
     */
    public ProductAnalysisResult analyzeProductImage(String imageUrl, String prompt) {
        try {
            log.info("Analyzing product image with vision model: {}", visionModel);

            String base64Image = downloadAndEncodeImage(imageUrl);
            String response = callOllamaVision(prompt, base64Image);

            return parseVisionResponse(response);

        } catch (Exception e) {
            log.error("Error analyzing product image: {}", e.getMessage(), e);
            return createFallbackVisionResult();
        }
    }

    /**
     * Generate multilingual content with text model
     */
    public ProductAnalysisResult generateMultilingualContent(String prompt) {
        try {
            log.info("Generating multilingual content with text model: {}", textModel);

            String response = callOllamaText(prompt);
            return parseTextResponse(response);

        } catch (Exception e) {
            log.error("Error generating multilingual content: {}", e.getMessage(), e);
            return createFallbackTextResult();
        }
    }

    private String downloadAndEncodeImage(String imageUrl) throws IOException {
        try {
            URL url = new URL(imageUrl);
            byte[] imageBytes = url.openStream().readAllBytes();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            log.warn("Failed to download image from URL: {}", imageUrl);
            return "";
        }
    }

    private String callOllamaVision(String prompt, String base64Image) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", visionModel);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        if (!base64Image.isEmpty()) {
            requestBody.put("images", List.of(base64Image));
        }

        // ОПТИМІЗОВАНІ ПАРАМЕТРИ ДЛЯ КРАЩОЇ ЯКОСТІ
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.1);        // Менше креативності, більше точності
        options.put("top_p", 0.8);             // Зосередженість на кращих варіантах
        options.put("top_k", 10);              // Обмежуємо вибір токенів
        options.put("num_predict", 500);       // Коротші відповіді
        options.put("repeat_penalty", 1.1);    // Уникаємо повторів
        requestBody.put("options", options);

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private String callOllamaText(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", textModel);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        // СПЕЦІАЛЬНІ ПАРАМЕТРИ ДЛЯ УКРАЇНСЬКОГО ТЕКСТУ
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.3);        // Трохи більше креативності для тексту
        options.put("top_p", 0.9);
        options.put("top_k", 20);
        options.put("num_predict", 800);       // Більше токенів для повного контенту
        options.put("repeat_penalty", 1.2);    // Сильніше уникаємо повторів
        options.put("presence_penalty", 0.1);  // Заохочуємо різноманітність
        requestBody.put("options", options);

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private ProductAnalysisResult parseVisionResponse(String response) {
        try {
            JsonNode responseNode = objectMapper.readTree(response);
            String content = responseNode.get("response").asText();

            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}') + 1;

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                return createFallbackVisionResult();
            }

            String jsonContent = content.substring(jsonStart, jsonEnd);
            JsonNode analysisNode = objectMapper.readTree(jsonContent);

            ProductAnalysisResult result = new ProductAnalysisResult();
            result.setProductType(getJsonString(analysisNode, "product_type"));
            result.setMainFeatures(getJsonStringList(analysisNode, "main_features"));
            result.setColors(getJsonStringList(analysisNode, "colors"));
            result.setStyle(getJsonString(analysisNode, "style"));
            result.setVisualQuality(getJsonDouble(analysisNode, "visual_quality"));

            return result;

        } catch (Exception e) {
            log.error("Error parsing vision response: {}", e.getMessage());
            return createFallbackVisionResult();
        }
    }

    private ProductAnalysisResult parseTextResponse(String response) {
        try {
            JsonNode responseNode = objectMapper.readTree(response);
            String content = responseNode.get("response").asText();

            // Очищуємо відповідь від зайвого тексту
            content = cleanAIResponse(content);

            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}') + 1;

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                log.warn("Не знайдено валідний JSON у відповіді AI");
                return createHighQualityFallback();
            }

            String jsonContent = content.substring(jsonStart, jsonEnd);
            JsonNode analysisNode = objectMapper.readTree(jsonContent);

            ProductAnalysisResult result = new ProductAnalysisResult();

            // Парсимо категорії
            JsonNode categories = analysisNode.get("categories");
            if (categories != null) {
                result.setCategoryUk(cleanJsonString(categories, "main_uk"));
                result.setCategoryRu(cleanJsonString(categories, "main_ru"));
                result.setCategoryEn(cleanJsonString(categories, "main_en"));
            }

            // Парсимо багатомовний контент з очищенням
            result.setSeoTitles(parseAndCleanLanguageMap(analysisNode, "seo_titles", 60));
            result.setDescriptions(parseAndCleanLanguageMap(analysisNode, "descriptions", 500));
            result.setMetaDescriptions(parseAndCleanLanguageMap(analysisNode, "meta_descriptions", 160));
            result.setTargetAudience(parseAndCleanLanguageMap(analysisNode, "target_audiences", 200));

            // Парсимо теги з обмеженням
            JsonNode tags = analysisNode.get("tags");
            if (tags != null) {
                Map<String, List<String>> tagMap = new HashMap<>();
                tagMap.put("uk", getCleanedStringList(tags, "uk", 5));
                tagMap.put("ru", getCleanedStringList(tags, "ru", 5));
                tagMap.put("en", getCleanedStringList(tags, "en", 5));
                result.setTags(tagMap);
            }

            // Парсимо числові значення з валідацією
            result.setTrendScore(getValidatedDouble(analysisNode, "trend_score", 1.0, 10.0));
            result.setPredictedPriceRange(cleanJsonString(analysisNode, "predicted_price_range"));
            result.setStyleTags(cleanJsonString(analysisNode, "style_tags"));
            result.setCompetitiveAdvantage(cleanJsonString(analysisNode, "competitive_advantage"));
            result.setUrgencyTriggers(cleanJsonString(analysisNode, "urgency_triggers"));

            return result;

        } catch (Exception e) {
            log.error("Помилка парсингу AI відповіді: {}", e.getMessage());
            return createHighQualityFallback();
        }
    }

    // МЕТОДИ ДЛЯ ОЧИЩЕННЯ ТА ВАЛІДАЦІЇ
    private String cleanAIResponse(String content) {
        if (content == null) return "";

        return content
                // Прибираємо зайві фрази AI
                .replaceAll("(?i)(ось|here is|here's|вот|это)\\s*(json|відповідь|ответ|answer)[^{]*", "")
                .replaceAll("(?i)(json\\s*:?\\s*)", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
    }

    private String cleanJsonString(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null) return null;

        String value = fieldNode.asText();
        if (value == null) return null;

        return value.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("^[.,:;!?\\s]+|[.,:;!?\\s]+$", "");
    }

    private Map<String, String> parseAndCleanLanguageMap(JsonNode root, String fieldName, int maxLength) {
        Map<String, String> result = new HashMap<>();
        JsonNode node = root.get(fieldName);
        if (node != null) {
            result.put("uk", truncateText(cleanJsonString(node, "uk"), maxLength));
            result.put("ru", truncateText(cleanJsonString(node, "ru"), maxLength));
            result.put("en", truncateText(cleanJsonString(node, "en"), maxLength));
        }
        return result;
    }

    private List<String> getCleanedStringList(JsonNode node, String field, int maxItems) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && fieldNode.isArray()) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < Math.min(fieldNode.size(), maxItems); i++) {
                String tag = cleanJsonString(fieldNode, String.valueOf(i));
                if (tag != null && !tag.isEmpty()) {
                    result.add(tag);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private Double getValidatedDouble(JsonNode node, String field, double min, double max) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null) {
            double value = fieldNode.asDouble();
            return Math.max(min, Math.min(max, value));
        }
        return null;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength).trim() : text;
    }

    // ЯКІСНИЙ FALLBACK ЗАМІСТЬ ПОМИЛОК
    private ProductAnalysisResult createHighQualityFallback() {
        ProductAnalysisResult result = new ProductAnalysisResult();

        result.setCategoryUk("Товари");
        result.setCategoryRu("Товары");
        result.setCategoryEn("Products");
        result.setTrendScore(6.0);
        result.setPredictedPriceRange("mid-range");

        // Якісні fallback тексти
        Map<String, String> fallbackTitles = new HashMap<>();
        fallbackTitles.put("uk", "Якісний товар за вигідною ціною");
        fallbackTitles.put("ru", "Качественный товар по выгодной цене");
        fallbackTitles.put("en", "Quality product at great price");
        result.setSeoTitles(fallbackTitles);

        Map<String, String> fallbackDescriptions = new HashMap<>();
        fallbackDescriptions.put("uk", "Високоякісний товар з відмінними характеристиками. Швидка доставка та гарантія якості.");
        fallbackDescriptions.put("ru", "Высококачественный товар с отличными характеристиками. Быстрая доставка и гарантия качества.");
        fallbackDescriptions.put("en", "High-quality product with excellent features. Fast delivery and quality guarantee.");
        result.setDescriptions(fallbackDescriptions);

        return result;
    }

    private Map<String, String> parseLanguageMap(JsonNode root, String fieldName) {
        Map<String, String> result = new HashMap<>();
        JsonNode node = root.get(fieldName);
        if (node != null) {
            result.put("uk", getJsonString(node, "uk"));
            result.put("ru", getJsonString(node, "ru"));
            result.put("en", getJsonString(node, "en"));
        }
        return result;
    }

    // Helper methods for JSON parsing
    private String getJsonString(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asText() : null;
    }

    private Double getJsonDouble(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asDouble() : null;
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

    private ProductAnalysisResult createFallbackVisionResult() {
        ProductAnalysisResult result = new ProductAnalysisResult();
        result.setColors(List.of("Unknown"));
        result.setMainFeatures(List.of("Standard product"));
        result.setStyle("Modern");
        result.setVisualQuality(5.0);
        return result;
    }

    private ProductAnalysisResult createFallbackTextResult() {
        ProductAnalysisResult result = new ProductAnalysisResult();
        result.setCategoryUk("Інше");
        result.setCategoryRu("Прочее");
        result.setCategoryEn("Other");
        result.setTrendScore(5.0);
        result.setPredictedPriceRange("mid-range");

        // Fallback multilingual content
        Map<String, String> fallbackTitles = new HashMap<>();
        fallbackTitles.put("uk", "Якісний товар за доступною ціною");
        fallbackTitles.put("ru", "Качественный товар по доступной цене");
        fallbackTitles.put("en", "Quality Product at Affordable Price");
        result.setSeoTitles(fallbackTitles);

        Map<String, String> fallbackDescriptions = new HashMap<>();
        fallbackDescriptions.put("uk", "Високоякісний товар, який поєднує в собі сучасний дизайн та функціональність. Ідеально підходить для повсякденного використання.");
        fallbackDescriptions.put("ru", "Высококачественный товар, сочетающий современный дизайн и функциональность. Идеально подходит для повседневного использования.");
        fallbackDescriptions.put("en", "High-quality product combining modern design with functionality. Perfect for everyday use.");
        result.setDescriptions(fallbackDescriptions);

        Map<String, String> fallbackAudiences = new HashMap<>();
        fallbackAudiences.put("uk", "Активні люди, які цінують якість та зручність");
        fallbackAudiences.put("ru", "Активные люди, ценящие качество и удобство");
        fallbackAudiences.put("en", "Active people who value quality and convenience");
        result.setTargetAudience(fallbackAudiences);

        return result;
    }
}