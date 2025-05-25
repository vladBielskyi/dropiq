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

    @Value("${ollama.text.model:qwen2.5:7b}")
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

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.1);
        options.put("top_p", 0.9);
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

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.2);
        options.put("top_p", 0.9);
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

            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}') + 1;

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                return createFallbackTextResult();
            }

            String jsonContent = content.substring(jsonStart, jsonEnd);
            JsonNode analysisNode = objectMapper.readTree(jsonContent);

            ProductAnalysisResult result = new ProductAnalysisResult();

            // Parse categories
            JsonNode categories = analysisNode.get("categories");
            if (categories != null) {
                result.setCategoryUk(getJsonString(categories, "main_uk"));
                result.setCategoryRu(getJsonString(categories, "main_ru"));
                result.setCategoryEn(getJsonString(categories, "main_en"));
                result.setSubcategoryUk(getJsonString(categories, "sub_uk"));
                result.setSubcategoryRu(getJsonString(categories, "sub_ru"));
                result.setSubcategoryEn(getJsonString(categories, "sub_en"));
            }

            // Parse multilingual content
            result.setSeoTitles(parseLanguageMap(analysisNode, "seo_titles"));
            result.setDescriptions(parseLanguageMap(analysisNode, "descriptions"));
            result.setMetaDescriptions(parseLanguageMap(analysisNode, "meta_descriptions"));
            result.setTargetAudience(parseLanguageMap(analysisNode, "target_audiences"));

            // Parse tags
            JsonNode tags = analysisNode.get("tags");
            if (tags != null) {
                Map<String, List<String>> tagMap = new HashMap<>();
                tagMap.put("uk", getJsonStringList(tags, "uk"));
                tagMap.put("ru", getJsonStringList(tags, "ru"));
                tagMap.put("en", getJsonStringList(tags, "en"));
                result.setTags(tagMap);
            }

            // Parse marketing and sales fields
            result.setTrendScore(getJsonDouble(analysisNode, "trend_score"));
            result.setPredictedPriceRange(getJsonString(analysisNode, "predicted_price_range"));
            result.setStyleTags(getJsonString(analysisNode, "style_tags"));
            result.setMarketingAngles(getJsonString(analysisNode, "marketing_angles"));
            result.setCompetitiveAdvantage(getJsonString(analysisNode, "competitive_advantage"));
            result.setUrgencyTriggers(getJsonString(analysisNode, "urgency_triggers"));

            return result;

        } catch (Exception e) {
            log.error("Error parsing text response: {}", e.getMessage());
            return createFallbackTextResult();
        }
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