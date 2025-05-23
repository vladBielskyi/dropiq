package com.dropiq.admin.service;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.ProductOptimizationStatus;
import io.jmix.core.DataManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ProductService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private AIOptimizationService aiOptimizationService;

    /**
     * Get all products for a dataset
     */
    public List<Product> getProductsByDataset(DataSet dataset) {
        return dataManager.load(Product.class)
                .query("select p from Product p where p.dataset = :dataset order by p.name")
                .parameter("dataset", dataset)
                .list();
    }

    /**
     * Get products with filters
     */
    public List<Product> getProductsWithFilter(DataSet dataset, ProductFilter filter) {
        StringBuilder queryBuilder = new StringBuilder("select p from Product p where p.dataset = :dataset");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("dataset", dataset);

        if (filter.getSearchTerm() != null && !filter.getSearchTerm().trim().isEmpty()) {
            queryBuilder.append(" and (lower(p.name) like :searchTerm or lower(p.description) like :searchTerm)");
            parameters.put("searchTerm", "%" + filter.getSearchTerm().toLowerCase() + "%");
        }

        if (filter.getActiveOnly() != null && filter.getActiveOnly()) {
            queryBuilder.append(" and p.active = true");
        }

        if (filter.getAvailableOnly() != null && filter.getAvailableOnly()) {
            queryBuilder.append(" and p.available = true");
        }

        if (filter.getMinPrice() != null) {
            queryBuilder.append(" and p.sellingPrice >= :minPrice");
            parameters.put("minPrice", filter.getMinPrice());
        }

        if (filter.getMaxPrice() != null) {
            queryBuilder.append(" and p.sellingPrice <= :maxPrice");
            parameters.put("maxPrice", filter.getMaxPrice());
        }

        if (filter.getCategory() != null && !filter.getCategory().trim().isEmpty()) {
            queryBuilder.append(" and p.externalCategoryName = :category");
            parameters.put("category", filter.getCategory());
        }

        if (filter.getOptimizationStatus() != null) {
            queryBuilder.append(" and p.optimizationStatus = :optimizationStatus");
            parameters.put("optimizationStatus", filter.getOptimizationStatus());
        }

        queryBuilder.append(" order by p.name");

        var query = dataManager.load(Product.class).query(queryBuilder.toString());
        parameters.forEach(query::parameter);

        return query.list();
    }

    /**
     * Bulk activate products
     */
    public void bulkActivateProducts(Collection<Product> products) {
        log.info("Bulk activating {} products", products.size());

        for (Product product : products) {
            product.setActive(true);
            product.setUpdatedAt(LocalDateTime.now());
            product.setUpdatedBy("admin"); // TODO: Get from UserSession
        }

        dataManager.save(products.toArray(new Product[0]));
        log.info("Successfully activated {} products", products.size());
    }

    /**
     * Bulk deactivate products
     */
    public void bulkDeactivateProducts(Collection<Product> products) {
        log.info("Bulk deactivating {} products", products.size());

        for (Product product : products) {
            product.setActive(false);
            product.setUpdatedAt(LocalDateTime.now());
            product.setUpdatedBy("admin"); // TODO: Get from UserSession
        }

        dataManager.save(products.toArray(new Product[0]));
        log.info("Successfully deactivated {} products", products.size());
    }

    /**
     * Apply markup to products
     */
    public void applyMarkupToProducts(Collection<Product> products, BigDecimal markupPercentage) {
        log.info("Applying {}% markup to {} products", markupPercentage, products.size());

        for (Product product : products) {
            if (product.getOriginalPrice() != null) {
                product.setMarkupPercentage(markupPercentage);
                calculateSellingPrice(product);
                product.setUpdatedAt(LocalDateTime.now());
                product.setUpdatedBy("admin"); // TODO: Get from UserSession
            }
        }

        dataManager.save(products.toArray(new Product[0]));
        log.info("Successfully applied markup to {} products", products.size());
    }

    /**
     * Set category for products
     */
    public void setCategoryForProducts(Collection<Product> products, String categoryName) {
        log.info("Setting category '{}' for {} products", categoryName, products.size());

        for (Product product : products) {
            product.setInternalCategory(categoryName);
            product.setUpdatedAt(LocalDateTime.now());
            product.setUpdatedBy("admin"); // TODO: Get from UserSession
        }

        dataManager.save(products.toArray(new Product[0]));
        log.info("Successfully set category for {} products", products.size());
    }

    /**
     * Optimize single product with AI
     */
    public void optimizeProduct(Product product) {
        log.info("Optimizing product: {}", product.getName());

        try {
            product.setOptimizationStatus(ProductOptimizationStatus.IN_PROGRESS);
            product.setLastOptimization(LocalDateTime.now());
            dataManager.save(product);

            // Call AI optimization service
            ProductOptimizationResult result = aiOptimizationService.optimizeProduct(product);

            // Apply optimization results
            if (result.getOptimizedName() != null) {
                product.setAiOptimizedName(result.getOptimizedName());
            }

            if (result.getOptimizedDescription() != null) {
                product.setAiOptimizedDescription(result.getOptimizedDescription());
            }

            if (result.getSeoTitle() != null) {
                product.setSeoTitle(result.getSeoTitle());
            }

            if (result.getSeoDescription() != null) {
                product.setSeoDescription(result.getSeoDescription());
            }

            if (result.getSeoKeywords() != null) {
                product.setSeoKeywords(result.getSeoKeywords());
            }

            if (result.getDetectedAttributes() != null) {
                product.setAiDetectedAttributes(result.getDetectedAttributes());
            }

            if (result.getSeoScore() != null) {
                product.setSeoScore(result.getSeoScore());
            }

            if (result.getTrendScore() != null) {
                product.setTrendScore(result.getTrendScore());
            }

            product.setOptimizationStatus(ProductOptimizationStatus.COMPLETED);
            product.setAiOptimized(true);

            dataManager.save(product);
            log.info("Successfully optimized product: {}", product.getName());

        } catch (Exception e) {
            log.error("Error optimizing product {}: {}", product.getName(), e.getMessage(), e);
            product.setOptimizationStatus(ProductOptimizationStatus.FAILED);
            dataManager.save(product);
            throw new RuntimeException("Product optimization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Optimize multiple products
     */
    public void optimizeProducts(Collection<Product> products) {
        log.info("Optimizing {} products", products.size());

        int successCount = 0;
        int failureCount = 0;

        for (Product product : products) {
            try {
                optimizeProduct(product);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to optimize product {}: {}", product.getName(), e.getMessage());
                failureCount++;
            }
        }

        log.info("Optimization completed: {} successful, {} failed", successCount, failureCount);
    }

    /**
     * Delete products
     */
    public void deleteProducts(Collection<Product> products) {
        log.info("Deleting {} products", products.size());

        for (Product product : products) {
            dataManager.remove(product);
        }

        log.info("Successfully deleted {} products", products.size());
    }

    /**
     * Get product statistics for dataset
     */
    public ProductStatistics getProductStatistics(DataSet dataset) {
        List<Product> products = getProductsByDataset(dataset);

        ProductStatistics stats = new ProductStatistics();
        stats.setTotalProducts(products.size());
        stats.setActiveProducts((int) products.stream().filter(Product::getActive).count());
        stats.setAvailableProducts((int) products.stream().filter(Product::getAvailable).count());
        stats.setOptimizedProducts((int) products.stream().filter(Product::getAiOptimized).count());

        // Price statistics
        OptionalDouble avgPrice = products.stream()
                .filter(p -> p.getSellingPrice() != null)
                .mapToDouble(p -> p.getSellingPrice().doubleValue())
                .average();
        stats.setAveragePrice(avgPrice.orElse(0.0));

        BigDecimal minPrice = products.stream()
                .filter(p -> p.getSellingPrice() != null)
                .map(Product::getSellingPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        stats.setMinPrice(minPrice.doubleValue());

        BigDecimal maxPrice = products.stream()
                .filter(p -> p.getSellingPrice() != null)
                .map(Product::getSellingPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        stats.setMaxPrice(maxPrice.doubleValue());

        // Category distribution
        Map<String, Long> categoryStats = products.stream()
                .filter(p -> p.getExternalCategoryName() != null)
                .collect(Collectors.groupingBy(Product::getExternalCategoryName, Collectors.counting()));
        stats.setCategoryDistribution(categoryStats);

        // Source distribution
        Map<String, Long> sourceStats = products.stream()
                .filter(p -> p.getSourceType() != null)
                .collect(Collectors.groupingBy(Product::getSourceType, Collectors.counting()));
        stats.setSourceDistribution(sourceStats);

        return stats;
    }

    // Private helper methods
    private void calculateSellingPrice(Product product) {
        if (product.getOriginalPrice() != null && product.getMarkupPercentage() != null) {
            BigDecimal originalPrice = product.getOriginalPrice();
            BigDecimal markupPercentage = product.getMarkupPercentage();

            BigDecimal markup = originalPrice
                    .multiply(markupPercentage)
                    .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);

            product.setSellingPrice(originalPrice.add(markup));

            // Calculate profit margin
            if (originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal profitMargin = markup
                        .divide(originalPrice, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                product.setProfitMargin(profitMargin);
            }
        }
    }

    // Data classes
    @Setter
    @Getter
    public static class ProductFilter {
        // Getters and setters
        private String searchTerm;
        private Boolean activeOnly;
        private Boolean availableOnly;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private String category;
        private ProductOptimizationStatus optimizationStatus;

    }

    @Setter
    @Getter
    public static class ProductStatistics {
        // Getters and setters
        private int totalProducts;
        private int activeProducts;
        private int availableProducts;
        private int optimizedProducts;
        private double averagePrice;
        private double minPrice;
        private double maxPrice;
        private Map<String, Long> categoryDistribution;
        private Map<String, Long> sourceDistribution;

    }

    @Setter
    @Getter
    public static class ProductOptimizationResult {
        // Getters and setters
        private String optimizedName;
        private String optimizedDescription;
        private String seoTitle;
        private String seoDescription;
        private String seoKeywords;
        private String detectedAttributes;
        private BigDecimal seoScore;
        private BigDecimal trendScore;

    }
}