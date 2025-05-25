package com.dropiq.engine.integration.ai;

import com.dropiq.engine.integration.ai.model.ProductAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OllamaClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ollama.vision.model:llava:13b}")
    private String visionModel;

    @Value("${ollama.text.model:mixtral:8x7b}")
    private String textModel;

    @Value("${ollama.timeout:300}")
    private int timeoutSeconds;

    // Product name patterns for real e-commerce
    private static final List<String> BRAND_PATTERNS = Arrays.asList(
            // Sportswear
            "Nike", "Adidas", "Puma", "Reebok", "New Balance", "Vans", "Converse",
            "Under Armour", "Asics", "Fila", "Champion", "Skechers", "Columbia", "The North Face",
            "Oakley", "Speedo", "Saucony", "Brooks", "Jordan", "Umbro", "Kappa", "Diadora",

            // Fast Fashion / High Street
            "Zara", "H&M", "Mango", "Reserved", "Bershka", "Pull&Bear", "Stradivarius", "Massimo Dutti",
            "Uniqlo", "Topshop", "Forever 21", "American Eagle", "Express", "Hollister", "Abercrombie & Fitch",
            "Pacsun", "New Look", "Primark", "Monki", "SHEIN", "Romwe", "Boohoo", "PrettyLittleThing", "ASOS",

            // Luxury and Designer Brands
            "Gucci", "Louis Vuitton", "Chanel", "Prada", "Versace", "Burberry", "Fendi", "Balenciaga",
            "Saint Laurent", "Dior", "Hermès", "Givenchy", "Bvlgari", "Tom Ford", "Valentino",
            "Alexander McQueen", "Marc Jacobs", "Celine", "Miu Miu", "Chloé", "Salvatore Ferragamo",
            "Viktor & Rolf", "Lanvin", "Acne Studios", "Off-White", "Ralph Lauren",

            // Streetwear
            "Supreme", "Stüssy", "A Bathing Ape (BAPE)", "Palace", "Fear of God", "Yeezy", "Off-White",
            "Kith", "Huf", "Vans", "The Hundreds", "Champion", "Carhartt WIP", "Thrasher",
            "Anti Social Social Club", "Billionaire Boys Club", "Stone Island", "Supreme", "T-Shirts Society",

            // Workwear and Utility
            "Carhartt", "Dickies", "Wrangler", "Levi's", "Wrangler", "Timberland", "Filson", "Patagonia",
            "Duluth Trading Co.", "Cabela's", "Hanes", "Wrangler", "Red Kap",

            // High-end Casuals
            "Tommy Hilfiger", "Calvin Klein", "Gant", "Lacoste", "Polo Ralph Lauren", "Ted Baker", "Ben Sherman",
            "Fred Perry", "Lyle & Scott", "Hackett London", "J.Crew", "Lands' End", "Brooks Brothers", "Barbour",
            "Vince", "Theory", "Z Zegna",

            // Activewear
            "Lululemon", "Athleta", "Sweaty Betty", "Outdoor Voices", "Tory Sport", "Puma", "Nike Training",
            "Adidas Performance", "Reebok Fitness", "Fabletics", "Gymshark", "ALO Yoga", "Beyond Yoga",
            "Girlfriend Collective", "Athleta", "Under Armour",

            // Footwear
            "Dr. Martens", "Timberland", "UGG", "Vans", "Nike SB", "Converse", "Allbirds", "Skechers",
            "Crocs", "Clarks", "Steve Madden", "Hush Puppies", "Reef", "Puma", "Adidas Originals", "New Balance",

            // Outerwear / Jackets
            "Patagonia", "Arc'teryx", "Columbia Sportswear", "The North Face", "Canada Goose", "Moncler",
            "Helly Hansen", "Jack Wolfskin", "Marmot", "Mountain Hardwear", "Ralph Lauren Outerwear", "Woolrich",
            "Fjällräven", "Barbour", "Eddie Bauer", "L.L. Bean", "Timberland Pro",

            // Denim & Casual Wear
            "Levi's", "Wrangler", "Lee", "Calvin Klein Jeans", "Diesel", "True Religion", "7 For All Mankind",
            "AG Jeans", "Paige", "Citizens of Humanity", "Lucky Brand", "Gap", "Old Navy", "Uniqlo", "Tommy Jeans"
    );

    public OllamaClient(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyze product image with enhanced vision model
     */
    public ProductAnalysisResult analyzeProductImage(String imageUrl, String productName) {
        try {
            log.info("Starting image analysis for: {}", productName);

            String base64Image = downloadAndEncodeImage(imageUrl);
            if (base64Image.isEmpty()) {
                log.warn("Failed to download image, using fallback analysis");
                return createSmartFallbackVisionResult(productName);
            }

            String response = callOllamaVision(createProductionVisionPrompt(productName), base64Image);
            ProductAnalysisResult result = parseVisionResponse(response);

            // Enhance with product name analysis
            enhanceWithProductNameAnalysis(result, productName);

            return result;

        } catch (Exception e) {
            log.error("Error in image analysis: {}", e.getMessage());
            return createSmartFallbackVisionResult(productName);
        }
    }

    /**
     * Generate multilingual content with production quality
     */
    public ProductAnalysisResult generateMultilingualContent(String productInfo, ProductAnalysisResult visionResult) {
        try {
            log.info("Generating multilingual SEO content");

            String prompt = createProductionTextPrompt(productInfo, visionResult);
            String response = callOllamaText(prompt);

            ProductAnalysisResult result = parseTextResponse(response);

            // Merge vision results if available
            if (visionResult != null) {
                mergeVisionResults(result, visionResult);
            }

            // Validate and clean all content
            validateAndCleanResult(result);

            return result;

        } catch (Exception e) {
            log.error("Error generating content: {}", e.getMessage());
            return createProductionFallbackResult(productInfo);
        }
    }

    private String createProductionVisionPrompt(String productName) {
        return String.format("""
            You are an expert e-commerce product analyst. Analyze this product image professionally.
            
            Product context: %s
            
            Focus on:
            1. Exact product type and model (be specific)
            2. Key selling features visible in image
            3. Material, quality, and craftsmanship
            4. Target customer profile
            5. Price positioning (budget/mid-range/premium/luxury)
            
            Return ONLY valid JSON:
            {
              "product_type": "specific product category (e.g., 'running shoes', 'winter jacket')",
              "model_name": "short SEO-optimized model name (e.g., 'Air Max 270', 'Ultraboost 22')",
              "brand_detected": "brand if visible or null",
              "main_features": [
                "key feature 1 (specific)",
                "key feature 2 (specific)",
                "key feature 3 (specific)"
              ],
              "materials": ["material 1", "material 2"],
              "colors": ["primary color", "secondary color"],
              "style": "modern/classic/sporty/casual/formal/streetwear",
              "quality_indicators": {
                "construction": "premium/good/standard",
                "materials": "high-end/quality/basic",
                "design": "innovative/trendy/classic"
              },
              "target_demographic": {
                "age_range": "18-25/25-35/35-45/45+",
                "gender": "male/female/unisex",
                "lifestyle": "athletic/fashion/professional/casual"
              },
              "visual_quality": 8.5,
              "price_tier": "budget/mid-range/premium/luxury",
              "season": "all-season/summer/winter/spring-fall",
              "use_cases": ["primary use", "secondary use"]
            }
            
            BE SPECIFIC AND ACCURATE. Think like a successful e-commerce manager.
            """, productName != null ? productName : "Unknown product");
    }

    private String createProductionTextPrompt(String productInfo, ProductAnalysisResult visionResult) {
        StringBuilder visionContext = new StringBuilder();
        if (visionResult != null) {
            if (visionResult.getModelName() != null) {
                visionContext.append("Model: ").append(visionResult.getModelName()).append("\n");
            }
            if (visionResult.getMainFeatures() != null) {
                visionContext.append("Features: ").append(String.join(", ", visionResult.getMainFeatures())).append("\n");
            }
            if (visionResult.getStyle() != null) {
                visionContext.append("Style: ").append(visionResult.getStyle()).append("\n");
            }
            if (visionResult.getPriceRange() != null) {
                visionContext.append("Price tier: ").append(visionResult.getPriceRange()).append("\n");
            }
        }

        return String.format("""
            You are a top e-commerce copywriter for successful online stores like Amazon, ASOS, Zalando.
            Create compelling, SEO-optimized content that converts browsers into buyers.
            
            PRODUCT INFORMATION:
            %s
            
            VISION ANALYSIS:
            %s
            
            Create production-ready e-commerce content following these STRICT RULES:
            
            1. SEO TITLES (50-70 characters):
               - Include brand/model if known
               - Main product type
               - 1-2 key features
               - Size/color variant if applicable
               Examples: "Nike Air Max 270 Men's Running Shoes - Black/White"
                        "Women's Winter Puffer Jacket - Waterproof, Hooded"
            
            2. DESCRIPTIONS (150-250 words):
               - Opening hook (benefit statement)
               - 3-4 key features with benefits
               - Technical specs if relevant
               - Use case scenarios
               - Call to action
               Format: Short paragraphs, benefit-focused, scannable
            
            3. META DESCRIPTIONS (150-160 characters):
               - Compelling summary
               - Include price tier hint
               - Urgency/exclusivity element
               - Clear value proposition
            
            4. TAGS (5-8 per language):
               - Category tags
               - Feature tags
               - Brand/style tags
               - Use case tags
               - Season/occasion tags
            
            Return ONLY valid JSON:
            {
              "seo_optimized_name": "Product name for SEO (English only, 40-60 chars)",
              "categories": {
                "main_uk": "main category in Ukrainian",
                "main_ru": "main category in Russian",
                "main_en": "main category in English",
                "sub_uk": "subcategory in Ukrainian",
                "sub_ru": "subcategory in Russian", 
                "sub_en": "subcategory in English",
                "micro_uk": "micro-category in Ukrainian",
                "micro_ru": "micro-category in Russian",
                "micro_en": "micro-category in English"
              },
              "seo_titles": {
                "uk": "SEO title in Ukrainian (50-70 chars)",
                "ru": "SEO title in Russian (50-70 chars)",
                "en": "SEO title in English (50-70 chars)"
              },
              "descriptions": {
                "uk": "Full description in Ukrainian (150-250 words)",
                "ru": "Full description in Russian (150-250 words)",
                "en": "Full description in English (150-250 words)"
              },
              "meta_descriptions": {
                "uk": "Meta description in Ukrainian (150-160 chars)",
                "ru": "Meta description in Russian (150-160 chars)",
                "en": "Meta description in English (150-160 chars)"
              },
              "tags": {
                "uk": ["tag1", "tag2", "tag3", "tag4", "tag5"],
                "ru": ["tag1", "tag2", "tag3", "tag4", "tag5"],
                "en": ["tag1", "tag2", "tag3", "tag4", "tag5"]
              },
              "target_audiences": {
                "uk": "Target audience description in Ukrainian",
                "ru": "Target audience description in Russian",
                "en": "Target audience description in English"
              },
              "selling_points": [
                "unique selling point 1",
                "unique selling point 2",
                "unique selling point 3"
              ],
              "trend_score": 7.5,
              "conversion_score": 8.0,
              "search_volume_estimate": "medium",
              "competitive_advantage": "Main advantage over competitors",
              "urgency_triggers": "Limited stock, special offer, trending now",
              "cross_sell_categories": ["related category 1", "related category 2"]
            }
            
            CRITICAL REQUIREMENTS:
            - Write like real successful stores (think Rozetka, Amazon, ASOS)
            - Natural, conversational tone (not robotic)
            - Focus on benefits, not just features
            - Use power words that sell
            - Include size/fit information where relevant
            - Mention care instructions if applicable
            - Create FOMO with urgency elements
            - Optimize for mobile reading (short paragraphs)
            - Use emotional triggers
            - Include trust signals
            
            LANGUAGE REQUIREMENTS:
            - Ukrainian: Modern, youth-friendly Ukrainian (not formal/Soviet style)
            - Russian: Contemporary Russian (avoid украинизмы)
            - English: International English (US spelling)
            
            ALL CONTENT MUST BE READY FOR IMMEDIATE USE IN A REAL STORE!
            """, productInfo, visionContext.toString());
    }

    private String callOllamaVision(String prompt, String base64Image) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", visionModel);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        if (!base64Image.isEmpty()) {
            requestBody.put("images", List.of(base64Image));
        }

        // Optimized parameters for production quality
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.2);        // Low for consistency
        options.put("top_p", 0.9);
        options.put("top_k", 40);
        options.put("num_predict", 1000);      // Enough for detailed JSON
        options.put("repeat_penalty", 1.1);
        options.put("seed", 42);               // Reproducibility
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

        // Optimized for multilingual content generation
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.3);        // Slightly more creative for text
        options.put("top_p", 0.95);
        options.put("top_k", 50);
        options.put("num_predict", 2000);      // More tokens for full content
        options.put("repeat_penalty", 1.2);
        options.put("presence_penalty", 0.1);
        options.put("frequency_penalty", 0.1);
        requestBody.put("options", options);

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private void enhanceWithProductNameAnalysis(ProductAnalysisResult result, String productName) {
        if (productName == null) return;

        // Extract brand from product name
        String detectedBrand = null;
        for (String brand : BRAND_PATTERNS) {
            if (productName.toLowerCase().contains(brand.toLowerCase())) {
                detectedBrand = brand;
                break;
            }
        }

        if (detectedBrand != null && result.getBrandDetected() == null) {
            result.setBrandDetected(detectedBrand);
        }

        // Extract model numbers/codes
        Pattern modelPattern = Pattern.compile("\\b[A-Z0-9]{2,}[-\\s]?[A-Z0-9]+\\b");
        Matcher matcher = modelPattern.matcher(productName);
        if (matcher.find()) {
            String modelCode = matcher.group();
            if (result.getModelName() == null) {
                result.setModelName(modelCode);
            }
        }

        // Extract size information
        Pattern sizePattern = Pattern.compile("\\b(XS|S|M|L|XL|XXL|\\d{2,3})\\b");
        matcher = sizePattern.matcher(productName);
        if (matcher.find()) {
            result.getAttributes().put("size", matcher.group());
        }
    }

    private void mergeVisionResults(ProductAnalysisResult target, ProductAnalysisResult vision) {
        if (vision.getProductType() != null) target.setProductType(vision.getProductType());
        if (vision.getModelName() != null) target.setModelName(vision.getModelName());
        if (vision.getBrandDetected() != null) target.setBrandDetected(vision.getBrandDetected());
        if (vision.getMainFeatures() != null) target.setMainFeatures(vision.getMainFeatures());
        if (vision.getColors() != null) target.setColors(vision.getColors());
        if (vision.getStyle() != null) target.setStyle(vision.getStyle());
        if (vision.getVisualQuality() != null) target.setVisualQuality(vision.getVisualQuality());
        if (vision.getPriceRange() != null) target.setPredictedPriceRange(vision.getPriceRange());
        if (vision.getMaterials() != null) target.setMaterials(vision.getMaterials());
        if (vision.getTargetDemographic() != null) target.setTargetDemographic(vision.getTargetDemographic());
        if (vision.getQualityIndicators() != null) target.setQualityIndicators(vision.getQualityIndicators());
        if (vision.getSeason() != null) target.setSeason(vision.getSeason());
        if (vision.getUseCases() != null) target.setUseCases(vision.getUseCases());
    }

    private void validateAndCleanResult(ProductAnalysisResult result) {
        // Clean and validate SEO titles
        if (result.getSeoTitles() != null) {
            result.getSeoTitles().forEach((lang, title) -> {
                if (title != null && title.length() > 70) {
                    result.getSeoTitles().put(lang, title.substring(0, 67) + "...");
                }
            });
        }

        // Validate meta descriptions length
        if (result.getMetaDescriptions() != null) {
            result.getMetaDescriptions().forEach((lang, desc) -> {
                if (desc != null && desc.length() > 160) {
                    result.getMetaDescriptions().put(lang, desc.substring(0, 157) + "...");
                }
            });
        }

        // Ensure descriptions are proper length
        if (result.getDescriptions() != null) {
            result.getDescriptions().forEach((lang, desc) -> {
                if (desc != null && desc.length() < 100) {
                    // Too short, enhance it
                    result.getDescriptions().put(lang, enhanceShortDescription(desc, lang));
                }
            });
        }

        // Validate tags
        if (result.getTags() != null) {
            result.getTags().forEach((lang, tags) -> {
                if (tags != null && tags.size() > 8) {
                    result.getTags().put(lang, tags.subList(0, 8));
                }
            });
        }

        // Ensure trend score is realistic
        if (result.getTrendScore() == null || result.getTrendScore() < 1 || result.getTrendScore() > 10) {
            result.setTrendScore(calculateRealisticTrendScore(result));
        }
    }

    private String enhanceShortDescription(String desc, String lang) {
        String suffix = "";
        switch (lang) {
            case "uk":
                suffix = " Високоякісний товар з швидкою доставкою по Україні. Гарантія якості.";
                break;
            case "ru":
                suffix = " Высококачественный товар с быстрой доставкой. Гарантия качества.";
                break;
            case "en":
                suffix = " High-quality product with fast shipping. Quality guaranteed.";
                break;
        }
        return desc + suffix;
    }

    private Double calculateRealisticTrendScore(ProductAnalysisResult result) {
        double score = 5.0; // Base score

        // Adjust based on various factors
        if (result.getStyle() != null && result.getStyle().contains("modern")) score += 1.0;
        if (result.getPredictedPriceRange() != null && result.getPredictedPriceRange().contains("premium")) score += 0.5;
        if (result.getMainFeatures() != null && result.getMainFeatures().size() > 3) score += 0.5;
        if (result.getBrandDetected() != null) score += 1.0;
        if (result.getVisualQuality() != null && result.getVisualQuality() > 7) score += 0.5;

        return Math.min(9.5, Math.max(3.0, score)); // Keep it realistic
    }

    private ProductAnalysisResult parseVisionResponse(String response) {
        try {
            JsonNode responseNode = objectMapper.readTree(response);
            String content = responseNode.get("response").asText();

            // Extract JSON from response
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}') + 1;

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                log.warn("No valid JSON found in vision response");
                return createSmartFallbackVisionResult(null);
            }

            String jsonContent = content.substring(jsonStart, jsonEnd);
            JsonNode analysisNode = objectMapper.readTree(jsonContent);

            ProductAnalysisResult result = new ProductAnalysisResult();

            // Parse all vision analysis fields
            result.setProductType(getJsonStringSafe(analysisNode, "product_type"));
            result.setModelName(getJsonStringSafe(analysisNode, "model_name"));
            result.setBrandDetected(getJsonStringSafe(analysisNode, "brand_detected"));
            result.setMainFeatures(getJsonStringListSafe(analysisNode, "main_features"));
            result.setMaterials(getJsonStringListSafe(analysisNode, "materials"));
            result.setColors(getJsonStringListSafe(analysisNode, "colors"));
            result.setStyle(getJsonStringSafe(analysisNode, "style"));
            result.setVisualQuality(getJsonDoubleSafe(analysisNode, "visual_quality"));
            result.setPriceRange(getJsonStringSafe(analysisNode, "price_tier"));
            result.setSeason(getJsonStringSafe(analysisNode, "season"));
            result.setUseCases(getJsonStringListSafe(analysisNode, "use_cases"));

            // Parse complex objects
            JsonNode qualityNode = analysisNode.get("quality_indicators");
            if (qualityNode != null) {
                Map<String, String> quality = new HashMap<>();
                qualityNode.fields().forEachRemaining(field ->
                        quality.put(field.getKey(), field.getValue().asText()));
                result.setQualityIndicators(quality);
            }

            JsonNode demographicNode = analysisNode.get("target_demographic");
            if (demographicNode != null) {
                Map<String, String> demographic = new HashMap<>();
                demographicNode.fields().forEachRemaining(field ->
                        demographic.put(field.getKey(), field.getValue().asText()));
                result.setTargetDemographic(demographic);
            }

            return result;

        } catch (Exception e) {
            log.error("Error parsing vision response: {}", e.getMessage());
            return createSmartFallbackVisionResult(null);
        }
    }

    private ProductAnalysisResult parseTextResponse(String response) {
        try {
            JsonNode responseNode = objectMapper.readTree(response);
            String content = responseNode.get("response").asText();

            // Clean and extract JSON
            content = cleanAIResponse(content);
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}') + 1;

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                log.warn("No valid JSON found in text response");
                return createProductionFallbackResult(null);
            }

            String jsonContent = content.substring(jsonStart, jsonEnd);
            JsonNode analysisNode = objectMapper.readTree(jsonContent);

            ProductAnalysisResult result = new ProductAnalysisResult();

            // Parse SEO optimized name
            result.setSeoOptimizedName(getJsonStringSafe(analysisNode, "seo_optimized_name"));

            // Parse categories
            JsonNode categories = analysisNode.get("categories");
            if (categories != null) {
                result.setCategoryUk(getJsonStringSafe(categories, "main_uk"));
                result.setCategoryRu(getJsonStringSafe(categories, "main_ru"));
                result.setCategoryEn(getJsonStringSafe(categories, "main_en"));
                result.setSubcategoryUk(getJsonStringSafe(categories, "sub_uk"));
                result.setSubcategoryRu(getJsonStringSafe(categories, "sub_ru"));
                result.setSubcategoryEn(getJsonStringSafe(categories, "sub_en"));
                result.setMicroCategoryUk(getJsonStringSafe(categories, "micro_uk"));
                result.setMicroCategoryRu(getJsonStringSafe(categories, "micro_ru"));
                result.setMicroCategoryEn(getJsonStringSafe(categories, "micro_en"));
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
                tags.fields().forEachRemaining(field -> {
                    List<String> tagList = new ArrayList<>();
                    field.getValue().forEach(tag -> tagList.add(tag.asText()));
                    tagMap.put(field.getKey(), tagList);
                });
                result.setTags(tagMap);
            }

            // Parse additional fields
            result.setSellingPoints(getJsonStringListSafe(analysisNode, "selling_points"));
            result.setTrendScore(getJsonDoubleSafe(analysisNode, "trend_score"));
            result.setConversionScore(getJsonDoubleSafe(analysisNode, "conversion_score"));
            result.setSearchVolumeEstimate(getJsonStringSafe(analysisNode, "search_volume_estimate"));
            result.setCompetitiveAdvantage(getJsonStringSafe(analysisNode, "competitive_advantage"));
            result.setUrgencyTriggers(getJsonStringSafe(analysisNode, "urgency_triggers"));
            result.setCrossSellCategories(getJsonStringListSafe(analysisNode, "cross_sell_categories"));

            return result;

        } catch (Exception e) {
            log.error("Error parsing text response: {}", e.getMessage());
            return createProductionFallbackResult(null);
        }
    }

    private String cleanAIResponse(String content) {
        if (content == null) return "";

        return content
                .replaceAll("(?i)(here is|here's|here are|this is|these are)\\s*(the)?\\s*(json|response|answer)[^{]*", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .replaceAll("(?i)json\\s*:?\\s*", "")
                .trim();
    }

    private Map<String, String> parseLanguageMap(JsonNode root, String fieldName) {
        Map<String, String> result = new HashMap<>();
        JsonNode node = root.get(fieldName);
        if (node != null) {
            node.fields().forEachRemaining(field ->
                    result.put(field.getKey(), field.getValue().asText()));
        }
        return result;
    }

    // Safe parsing methods
    private String getJsonStringSafe(JsonNode node, String field) {
        try {
            JsonNode fieldNode = node.get(field);
            return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Double getJsonDoubleSafe(JsonNode node, String field) {
        try {
            JsonNode fieldNode = node.get(field);
            return fieldNode != null && !fieldNode.isNull() ? fieldNode.asDouble() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> getJsonStringListSafe(JsonNode node, String field) {
        try {
            JsonNode fieldNode = node.get(field);
            if (fieldNode != null && fieldNode.isArray()) {
                List<String> result = new ArrayList<>();
                fieldNode.forEach(item -> result.add(item.asText()));
                return result;
            }
        } catch (Exception e) {
            log.debug("Error parsing string list for field {}: {}", field, e.getMessage());
        }
        return new ArrayList<>();
    }

    // Production-ready fallback methods
    private ProductAnalysisResult createSmartFallbackVisionResult(String productName) {
        ProductAnalysisResult result = new ProductAnalysisResult();

        // Analyze product name for clues
        String lowerName = productName != null ? productName.toLowerCase() : "";

        // Detect product type from name
        if (lowerName.contains("shoe") || lowerName.contains("sneaker") || lowerName.contains("boot")) {
            result.setProductType("footwear");
            result.setMainFeatures(Arrays.asList("Comfortable fit", "Durable construction", "Stylish design"));
        } else if (lowerName.contains("jacket") || lowerName.contains("coat")) {
            result.setProductType("outerwear");
            result.setMainFeatures(Arrays.asList("Weather protection", "Modern style", "Quality materials"));
        } else if (lowerName.contains("shirt") || lowerName.contains("tee") || lowerName.contains("top")) {
            result.setProductType("tops");
            result.setMainFeatures(Arrays.asList("Comfortable fabric", "Versatile style", "Easy care"));
        } else {
            result.setProductType("general apparel");
            result.setMainFeatures(Arrays.asList("Quality construction", "Modern design", "Great value"));
        }

        result.setColors(Arrays.asList("Mixed colors"));
        result.setStyle("modern");
        result.setVisualQuality(7.0);
        result.setPriceRange("mid-range");
        result.setSeason("all-season");

        return result;
    }

    private ProductAnalysisResult createProductionFallbackResult(String productInfo) {
        ProductAnalysisResult result = new ProductAnalysisResult();

        // Set production-ready fallback content
        result.setSeoOptimizedName("Quality Product - Best Value");

        result.setCategoryUk("Товари");
        result.setCategoryRu("Товары");
        result.setCategoryEn("Products");

        // Professional fallback SEO titles
        Map<String, String> fallbackTitles = new HashMap<>();
        fallbackTitles.put("uk", "Якісний товар за найкращою ціною | Швидка доставка");
        fallbackTitles.put("ru", "Качественный товар по лучшей цене | Быстрая доставка");
        fallbackTitles.put("en", "Quality Product at Best Price | Fast Shipping");
        result.setSeoTitles(fallbackTitles);

        // Professional fallback descriptions
        Map<String, String> fallbackDescriptions = new HashMap<>();
        fallbackDescriptions.put("uk", "Високоякісний товар, який поєднує в собі сучасний дизайн та функціональність. Ідеально підходить для повсякденного використання. Виготовлений з якісних матеріалів, що забезпечують довговічність та комфорт. Швидка доставка по всій Україні.");
        fallbackDescriptions.put("ru", "Высококачественный товар, сочетающий современный дизайн и функциональность. Идеально подходит для повседневного использования. Изготовлен из качественных материалов, обеспечивающих долговечность и комфорт. Быстрая доставка по всей стране.");
        fallbackDescriptions.put("en", "High-quality product combining modern design with functionality. Perfect for everyday use. Made from quality materials ensuring durability and comfort. Fast shipping available.");
        result.setDescriptions(fallbackDescriptions);

        // Professional meta descriptions
        Map<String, String> fallbackMeta = new HashMap<>();
        fallbackMeta.put("uk", "Купуйте якісні товари за найкращими цінами ✓ Швидка доставка ✓ Гарантія якості ✓ Великий вибір");
        fallbackMeta.put("ru", "Покупайте качественные товары по лучшим ценам ✓ Быстрая доставка ✓ Гарантия качества ✓ Большой выбор");
        fallbackMeta.put("en", "Buy quality products at best prices ✓ Fast shipping ✓ Quality guarantee ✓ Wide selection");
        result.setMetaDescriptions(fallbackMeta);

        // Professional tags
        Map<String, List<String>> fallbackTags = new HashMap<>();
        fallbackTags.put("uk", Arrays.asList("якісний товар", "краща ціна", "швидка доставка", "гарантія", "новинка"));
        fallbackTags.put("ru", Arrays.asList("качественный товар", "лучшая цена", "быстрая доставка", "гарантия", "новинка"));
        fallbackTags.put("en", Arrays.asList("quality product", "best price", "fast shipping", "warranty", "new arrival"));
        result.setTags(fallbackTags);

        // Target audience
        Map<String, String> fallbackAudience = new HashMap<>();
        fallbackAudience.put("uk", "Для всіх, хто цінує якість та стиль");
        fallbackAudience.put("ru", "Для всех, кто ценит качество и стиль");
        fallbackAudience.put("en", "For everyone who values quality and style");
        result.setTargetAudience(fallbackAudience);

        result.setTrendScore(6.5);
        result.setConversionScore(7.0);
        result.setPredictedPriceRange("mid-range");
        result.setCompetitiveAdvantage("Best value for money");
        result.setUrgencyTriggers("Limited time offer");

        return result;
    }

    private String downloadAndEncodeImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            byte[] imageBytes = url.openStream().readAllBytes();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            log.warn("Failed to download image from URL: {}", imageUrl);
            return "";
        }
    }
}