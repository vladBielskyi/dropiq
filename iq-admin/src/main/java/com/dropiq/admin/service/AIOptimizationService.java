package com.dropiq.admin.service;

import com.dropiq.admin.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class AIOptimizationService {

    private final Random random = new Random();

    /**
     * Optimize a single product using AI
     * This is a placeholder implementation - in production this would call actual AI services
     */
    public ProductService.ProductOptimizationResult optimizeProduct(Product product) {
        log.info("Starting AI optimization for product: {}", product.getName());

        try {
            // Simulate AI processing time
            Thread.sleep(1000 + random.nextInt(2000));

            ProductService.ProductOptimizationResult result = new ProductService.ProductOptimizationResult();

            // Optimize product name
            result.setOptimizedName(optimizeProductName(product.getName()));

            // Optimize description
            result.setOptimizedDescription(optimizeProductDescription(product.getDescription()));

            // Generate SEO content
            result.setSeoTitle(generateSeoTitle(product.getName()));
            result.setSeoDescription(generateSeoDescription(product.getDescription()));
            result.setSeoKeywords(generateSeoKeywords(product));

            // Detect attributes from name/description
            result.setDetectedAttributes(detectProductAttributes(product));

            // Calculate SEO score
            result.setSeoScore(calculateSeoScore(product, result));

            // Calculate trend score
            result.setTrendScore(calculateTrendScore(product));

            log.info("AI optimization completed for product: {}", product.getName());
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI optimization interrupted", e);
        } catch (Exception e) {
            log.error("AI optimization failed for product {}: {}", product.getName(), e.getMessage());
            throw new RuntimeException("AI optimization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Optimize product name using AI
     */
    private String optimizeProductName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return originalName;
        }

        // Simulate AI name optimization
        String optimized = originalName;

        // Add some common optimization patterns
        if (!optimized.toLowerCase().contains("premium")) {
            optimized = "Premium " + optimized;
        }

        // Remove excessive punctuation
        optimized = optimized.replaceAll("[!]{2,}", "!");
        optimized = optimized.replaceAll("[?]{2,}", "?");

        // Ensure proper capitalization
        optimized = capitalizeWords(optimized);

        return optimized.length() > 100 ? optimized.substring(0, 97) + "..." : optimized;
    }

    /**
     * Optimize product description using AI
     */
    private String optimizeProductDescription(String originalDescription) {
        if (originalDescription == null || originalDescription.trim().isEmpty()) {
            return "Experience the perfect blend of quality and style with this exceptional product. " +
                    "Crafted with attention to detail and designed to exceed expectations, " +
                    "this item offers outstanding value and performance.";
        }

        String optimized = originalDescription;

        // Add compelling introduction if missing
        if (!optimized.toLowerCase().startsWith("experience") &&
                !optimized.toLowerCase().startsWith("discover") &&
                !optimized.toLowerCase().startsWith("introducing")) {
            optimized = "Experience the excellence of " + optimized;
        }

        // Add call-to-action if missing
        if (!optimized.toLowerCase().contains("perfect for") &&
                !optimized.toLowerCase().contains("ideal for")) {
            optimized += " Perfect for those who appreciate quality and style.";
        }

        return optimized;
    }

    /**
     * Generate SEO-optimized title
     */
    private String generateSeoTitle(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            return "Premium Quality Product - Best Value Online";
        }

        String seoTitle = productName;

        // Add power words if not present
        List<String> powerWords = Arrays.asList("Best", "Premium", "Quality", "Top", "Professional");
        String finalSeoTitle = seoTitle;
        boolean hasPowerWord = powerWords.stream()
                .anyMatch(word -> finalSeoTitle.toLowerCase().contains(word.toLowerCase()));

        if (!hasPowerWord) {
            seoTitle = "Premium " + seoTitle;
        }

        // Add value proposition
        if (!seoTitle.toLowerCase().contains("sale") &&
                !seoTitle.toLowerCase().contains("deal") &&
                !seoTitle.toLowerCase().contains("value")) {
            seoTitle += " - Best Value Online";
        }

        // Ensure optimal length (50-60 characters)
        if (seoTitle.length() > 60) {
            seoTitle = seoTitle.substring(0, 57) + "...";
        }

        return seoTitle;
    }

    /**
     * Generate SEO-optimized description
     */
    private String generateSeoDescription(String productDescription) {
        if (productDescription == null || productDescription.trim().isEmpty()) {
            return "Discover premium quality products at unbeatable prices. " +
                    "Shop now for the best deals and exceptional customer service. " +
                    "Fast shipping and satisfaction guaranteed.";
        }

        String seoDesc = productDescription;

        // Ensure it starts with an action word
        if (!seoDesc.toLowerCase().matches("^(discover|shop|buy|get|find|explore).*")) {
            seoDesc = "Discover " + seoDesc.toLowerCase();
        }

        // Add call-to-action if missing
        if (!seoDesc.toLowerCase().contains("shop now") &&
                !seoDesc.toLowerCase().contains("buy now") &&
                !seoDesc.toLowerCase().contains("order now")) {
            seoDesc += " Shop now for the best deals!";
        }

        // Ensure optimal length (150-160 characters)
        if (seoDesc.length() > 160) {
            seoDesc = seoDesc.substring(0, 157) + "...";
        } else if (seoDesc.length() < 120) {
            seoDesc += " Fast shipping and satisfaction guaranteed.";
        }

        return seoDesc;
    }

    /**
     * Generate SEO keywords
     */
    private String generateSeoKeywords(Product product) {
        List<String> keywords = Arrays.asList(
                "premium quality",
                "best price",
                "online shopping",
                "fast delivery",
                "customer satisfaction"
        );

        // Add product-specific keywords
        if (product.getExternalCategoryName() != null) {
            keywords.add(product.getExternalCategoryName().toLowerCase());
        }

        if (product.getAttributes() != null) {
            product.getAttributes().values().forEach(value -> {
                if (value != null && value.length() < 20) {
                    keywords.add(value.toLowerCase());
                }
            });
        }

        return String.join(", ", keywords.subList(0, Math.min(keywords.size(), 10)));
    }

    /**
     * Detect product attributes using AI
     */
    private String detectProductAttributes(Product product) {
        StringBuilder attributes = new StringBuilder();

        String name = product.getName() != null ? product.getName().toLowerCase() : "";
        String description = product.getDescription() != null ? product.getDescription().toLowerCase() : "";
        String combined = name + " " + description;

        // Detect colors
        List<String> colors = Arrays.asList("black", "white", "red", "blue", "green", "yellow", "pink", "purple", "orange", "brown", "gray", "grey");
        for (String color : colors) {
            if (combined.contains(color)) {
                attributes.append("Color: ").append(capitalizeWords(color)).append("; ");
                break;
            }
        }

        // Detect sizes
        List<String> sizes = Arrays.asList("xs", "s", "m", "l", "xl", "xxl", "small", "medium", "large", "extra large");
        for (String size : sizes) {
            if (combined.contains(size)) {
                attributes.append("Size: ").append(size.toUpperCase()).append("; ");
                break;
            }
        }

        // Detect materials
        List<String> materials = Arrays.asList("cotton", "polyester", "wool", "silk", "leather", "plastic", "metal", "wood", "glass");
        for (String material : materials) {
            if (combined.contains(material)) {
                attributes.append("Material: ").append(capitalizeWords(material)).append("; ");
                break;
            }
        }

        // Detect brand if present
        if (product.getPlatformSpecificData() != null && product.getPlatformSpecificData().containsKey("vendor")) {
            String vendor = product.getPlatformSpecificData().get("vendor");
            if (vendor != null && !vendor.equals("-")) {
                attributes.append("Brand: ").append(vendor).append("; ");
            }
        }

        return attributes.toString().trim();
    }

    /**
     * Calculate SEO score
     */
    private BigDecimal calculateSeoScore(Product product, ProductService.ProductOptimizationResult result) {
        double score = 50.0; // Base score

        // Title optimization
        if (result.getSeoTitle() != null && result.getSeoTitle().length() >= 30 && result.getSeoTitle().length() <= 60) {
            score += 15;
        }

        // Description optimization
        if (result.getSeoDescription() != null && result.getSeoDescription().length() >= 120 && result.getSeoDescription().length() <= 160) {
            score += 15;
        }

        // Keywords presence
        if (result.getSeoKeywords() != null && result.getSeoKeywords().split(",").length >= 5) {
            score += 10;
        }

        // Product has images
        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            score += 10;
        }

        // Product has price
        if (product.getSellingPrice() != null && product.getSellingPrice().compareTo(BigDecimal.ZERO) > 0) {
            score += 5;
        }

        // Product is available
        if (product.getAvailable() && product.getStock() != null && product.getStock() > 0) {
            score += 5;
        }

        return BigDecimal.valueOf(Math.min(score, 100.0));
    }

    /**
     * Calculate trend score
     */
    private BigDecimal calculateTrendScore(Product product) {
        double score = 50.0; // Base score

        // Category-based trending (simulation)
        if (product.getExternalCategoryName() != null) {
            String category = product.getExternalCategoryName().toLowerCase();
            if (category.contains("electronic") || category.contains("phone") || category.contains("computer")) {
                score += 20;
            } else if (category.contains("clothing") || category.contains("fashion") || category.contains("style")) {
                score += 15;
            } else if (category.contains("home") || category.contains("kitchen") || category.contains("decor")) {
                score += 10;
            }
        }

        // Price-based trending (lower prices tend to trend more)
        if (product.getSellingPrice() != null) {
            double price = product.getSellingPrice().doubleValue();
            if (price < 50) {
                score += 15;
            } else if (price < 100) {
                score += 10;
            } else if (price < 200) {
                score += 5;
            }
        }

        // Availability boost
        if (product.getAvailable() && product.getStock() != null && product.getStock() > 10) {
            score += 10;
        }

        // Add some randomness to simulate market dynamics
        score += (random.nextDouble() - 0.5) * 20;

        return BigDecimal.valueOf(Math.max(0, Math.min(score, 100.0)));
    }

    /**
     * Capitalize words utility method
     */
    private String capitalizeWords(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }
}
