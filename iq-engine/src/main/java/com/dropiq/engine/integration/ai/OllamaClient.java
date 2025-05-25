package com.dropiq.engine.integration.ai;

import com.dropiq.engine.integration.ai.model.ProductAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            // Download and encode image
            String base64Image = downloadAndEncodeImage(imageUrl);

            // Call vision model
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
            return Base64Utils.encodeToString(imageBytes);
        } catch (Exception e) {
            log.warn("Failed to download image from URL: {}, using placeholder", imageUrl);
            // Return empty base64 string - model will work without image
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

            // Extract JSON from response
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}') + 1;

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                return createFallbackVisionResult();
            }

            String jsonContent = content.substring(jsonStart, jsonEnd);
            JsonNode analysisNode = objectMapper.readTree(jsonContent);

            ProductAnalysisResult result = new ProductAnalysisResult();
            result.setProductType(getJsonString(analysisNode, "product_type"));
            result.setMainCategory(getJsonString(analysisNode, "main_category"));
            result.setSubcategory(getJsonString(analysisNode, "subcategory"));
            result.setMainFeatures(getJsonStringArray(analysisNode, "main_features"));
            result.setColors(getJsonStringArray(analysisNode, "colors"));
            result.setStyle(getJsonString(analysisNode, "style"));
            result.setTargetAudience(getJsonString(analysisNode, "target_audience"));
            result.setPriceRange(getJsonString(analysisNode, "price_range"));
            result.setVisualQuality(getJsonDouble(analysisNode, "visual_quality"));
            result.setBrandVisible(getJsonBoolean(analysisNode, "brand_visible"));

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

            // Extract JSON from response
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

            // Parse SEO titles
            JsonNode seoTitles = analysisNode.get("seo_titles");
            if (seoTitles != null) {
                Map<String, String> titles = new HashMap<>();
                titles.put("uk", getJsonString(seoTitles, "uk"));
                titles.put("ru", getJsonString(seoTitles, "ru"));
                titles.put("en", getJsonString(seoTitles, "en"));
                result.setSeoTitles(titles);
            }

            // Parse descriptions
            JsonNode descriptions = analysisNode.get("descriptions");
            if (descriptions != null) {
                Map<String, String> descs = new HashMap<>();
                descs.put("uk", getJsonString(descriptions, "uk"));
                descs.put("ru", getJsonString(descriptions, "ru"));
                descs.put("en", getJsonString(descriptions, "en"));
                result.setDescriptions(descs);
            }

            // Parse meta descriptions
            JsonNode metaDescriptions = analysisNode.get("meta_descriptions");
            if (metaDescriptions != null) {
                Map<String, String> metaDescs = new HashMap<>();
                metaDescs.put("uk", getJsonString(metaDescriptions, "uk"));
                metaDescs.put("ru", getJsonString(metaDescriptions, "ru"));
                metaDescs.put("en", getJsonString(metaDescriptions, "en"));
                result.setMetaDescriptions(metaDescs);
            }

            // Parse tags
            JsonNode tags = analysisNode.get("tags");
            if (tags != null) {
                Map<String, List<String>> tagMap = new HashMap<>();
                tagMap.put("uk", getJsonStringList(tags, "uk"));
                tagMap.put("ru", getJsonStringList(tags, "ru"));
                tagMap.put("en", getJsonStringList(tags, "en"));
                result.setTags(tagMap);
            }

            // Parse target audience
            JsonNode targetAudience = analysisNode.get("target_audience");
            if (targetAudience != null) {
                Map<String, String> audience = new HashMap<>();
                audience.put("uk", getJsonString(targetAudience, "uk"));
                audience.put("ru", getJsonString(targetAudience, "ru"));
                audience.put("en", getJsonString(targetAudience, "en"));
                result.setTargetAudience(audience);
            }

            // Parse other fields
            result.setTrendScore(getJsonDouble(analysisNode, "trend_score"));
            result.setPredictedPriceRange(getJsonString(analysisNode, "predicted_price_range"));
            result.setStyleTags(getJsonString(analysisNode, "style_tags"));
            result.setMainFeatures(getJsonString(analysisNode, "main_features"));

            return result;

        } catch (Exception e) {
            log.error("Error parsing text response: {}", e.getMessage());
            return createFallbackTextResult();
        }
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

    private Boolean getJsonBoolean(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null ? fieldNode.asBoolean() : null;
    }

    private List<String> getJsonStringArray(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && fieldNode.isArray()) {
            List<String> result = new ArrayList<>();
            fieldNode.forEach(item -> result.add(item.asText()));
            return result;
        }
        return new ArrayList<>();
    }

    private List<String> getJsonStringList(JsonNode node, String field) {
        return getJsonStringArray(node, field);
    }

    private ProductAnalysisResult createFallbackVisionResult() {
        ProductAnalysisResult result = new ProductAnalysisResult();
        result.setMainCategory("General");
        result.setSubcategory("Other");
        result.setColors(List.of("Unknown"));
        result.setMainFeatures(List.of("Standard product"));
        result.setStyle("Modern");
        result.setVisualQuality(5.0);
        result.setBrandVisible(false);
        return result;
    }

    private ProductAnalysisResult createFallbackTextResult() {
        ProductAnalysisResult result = new ProductAnalysisResult();
        result.setCategoryUk("Інше");
        result.setCategoryRu("Прочее");
        result.setCategoryEn("Other");
        result.setTrendScore(5.0);
        result.setPredictedPriceRange("mid-range");
        return result;
    }
}
