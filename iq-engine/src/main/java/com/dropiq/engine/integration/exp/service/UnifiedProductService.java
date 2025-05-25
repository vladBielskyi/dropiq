package com.dropiq.engine.integration.exp.service;

import com.dropiq.engine.integration.exp.PlatformHandler;
import com.dropiq.engine.integration.exp.model.DataSourceConfig;
import com.dropiq.engine.integration.exp.model.ProductVariantGroup;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UnifiedProductService {

    private final Map<SourceType, PlatformHandler> platformHandlers;

    public UnifiedProductService(List<PlatformHandler> handlers) {
        Map<SourceType, PlatformHandler> handlersMap = new HashMap<>();
        for (PlatformHandler handler : handlers) {
            handlersMap.put(handler.getSourceType(), handler);
        }
        this.platformHandlers = Collections.unmodifiableMap(handlersMap);
    }

    /**
     * Fetch products from a single platform
     */
    public List<UnifiedProduct> fetchProductsFromPlatform(SourceType platformType, String url, Map<String, String> headers) {
        PlatformHandler handler = platformHandlers.get(platformType);

        if (handler == null) {
            throw new RuntimeException("Unsupported platform: " + platformType);
        }

        try {
            return handler.fetchProducts(url, headers);
        } catch (Exception e) {
            log.error("Error fetching products from platform {}: {}", platformType, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Fetch products from all configured platforms
     */
    public List<UnifiedProduct> fetchProductsFromAllPlatforms(List<DataSourceConfig> configs) {
        List<UnifiedProduct> allProducts = new ArrayList<>();

        for (DataSourceConfig config : configs) {
            try {
                PlatformHandler handler = platformHandlers.get(config.getPlatformType());

                if (handler == null) {
                    log.warn("Unsupported platform: {}", config.getPlatformType());
                    continue;
                }

                List<UnifiedProduct> products = handler.fetchProducts(config.getUrl(), config.getHeaders());
                allProducts.addAll(products);

            } catch (Exception e) {
                log.error("Error fetching products from {}: {}", config.getPlatformType(), e.getMessage());
            }
        }

        return allProducts;
    }

    /**
     * Group products by variant group
     */
    public List<ProductVariantGroup> groupProductsByVariant(List<UnifiedProduct> products) {
        List<ProductVariantGroup> variantGroups = new ArrayList<>();

        // Filter products that have group IDs and group them
        Map<String, List<UnifiedProduct>> groupedProducts = products.stream()
                .filter(product -> product.getGroupId() != null && !product.getGroupId().isEmpty())
                .collect(Collectors.groupingBy(UnifiedProduct::getGroupId));

        // Create variant groups from grouped products
        for (Map.Entry<String, List<UnifiedProduct>> entry : groupedProducts.entrySet()) {
            ProductVariantGroup variantGroup = new ProductVariantGroup();
            variantGroup.setGroupId(entry.getKey());

            List<UnifiedProduct> productList = entry.getValue();
            if (!productList.isEmpty()) {
                UnifiedProduct firstProduct = productList.get(0);
                variantGroup.setName(firstProduct.getName());
                variantGroup.setDescription(firstProduct.getDescription());
                variantGroup.setCategoryId(firstProduct.getExternalCategoryId());
                variantGroup.setCategoryName(firstProduct.getExternalCategoryName());
                variantGroup.getImageUrls().addAll(firstProduct.getImageUrls());
            }

            for (UnifiedProduct product : productList) {
                variantGroup.getVariants().add(product);
                variantGroup.getSourcePlatforms().add(product.getSourceType());
            }

            variantGroup.setLastUpdated(LocalDateTime.now());
            variantGroups.add(variantGroup);
        }

        return variantGroups;
    }

    /**
     * Aggregate products from multiple platforms and group by variant
     */
    public List<ProductVariantGroup> aggregateAndGroupProducts(List<DataSourceConfig> configs) {
        List<UnifiedProduct> allProducts = fetchProductsFromAllPlatforms(configs);
        List<ProductVariantGroup> groups = new ArrayList<>();

        // Group products by group ID
        Map<String, List<UnifiedProduct>> groupMap = new HashMap<>();

        for (UnifiedProduct product : allProducts) {
            String groupId = product.getGroupId();
            if (groupId == null || groupId.isEmpty()) {
                groupId = "single_" + product.getExternalId(); // Create unique group for products without group ID
            }

            groupMap.computeIfAbsent(groupId, k -> new ArrayList<>()).add(product);
        }

        // Create variant groups
        for (Map.Entry<String, List<UnifiedProduct>> entry : groupMap.entrySet()) {
            String groupId = entry.getKey();
            List<UnifiedProduct> productList = entry.getValue();

            if (groupId.startsWith("single_")) {
                // Products without group ID, each is its own "group"
                for (UnifiedProduct product : productList) {
                    ProductVariantGroup singleGroup = ProductVariantGroup.fromProduct(product);
                    groups.add(singleGroup);
                }
            } else {
                // Products with the same group ID
                ProductVariantGroup group = new ProductVariantGroup();
                group.setGroupId(groupId);

                if (!productList.isEmpty()) {
                    UnifiedProduct firstProduct = productList.get(0);
                    group.setName(firstProduct.getName());
                    group.setDescription(firstProduct.getDescription());
                    group.setCategoryId(firstProduct.getExternalCategoryId());
                    group.setCategoryName(firstProduct.getExternalCategoryName());
                    group.getImageUrls().addAll(firstProduct.getImageUrls());
                }

                for (UnifiedProduct product : productList) {
                    group.getVariants().add(product);
                    group.getSourcePlatforms().add(product.getSourceType());
                }

                group.setLastUpdated(LocalDateTime.now());
                groups.add(group);
            }
        }

        return groups;
    }

    /**
     * Get products by category from a specific platform
     */
    public List<UnifiedProduct> getProductsByCategory(SourceType platformType, String url, String categoryId, Map<String, String> headers) {
        List<UnifiedProduct> allProducts = fetchProductsFromPlatform(platformType, url, headers);

        return allProducts.stream()
                .filter(product -> categoryId.equals(product.getExternalCategoryId()))
                .collect(Collectors.toList());
    }

    /**
     * Search products by name across platforms
     */
    public List<UnifiedProduct> searchProductsByName(List<DataSourceConfig> configs, String searchTerm) {
        List<UnifiedProduct> allProducts = fetchProductsFromAllPlatforms(configs);

        return allProducts.stream()
                .filter(product -> product.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Get statistics about products from different platforms
     */
    public Map<String, Object> getProductStatistics(List<DataSourceConfig> configs) {
        List<UnifiedProduct> allProducts = fetchProductsFromAllPlatforms(configs);
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalProducts", allProducts.size());

        // Group by source platform
        Map<SourceType, List<UnifiedProduct>> byPlatform = allProducts.stream()
                .collect(Collectors.groupingBy(UnifiedProduct::getSourceType));

        Map<String, Integer> platformCounts = new HashMap<>();
        for (Map.Entry<SourceType, List<UnifiedProduct>> entry : byPlatform.entrySet()) {
            platformCounts.put(entry.getKey().name(), entry.getValue().size());
        }
        stats.put("productsByPlatform", platformCounts);

        // Group by category
        Map<String, Long> categoryStats = allProducts.stream()
                .filter(product -> product.getExternalCategoryId() != null)
                .collect(Collectors.groupingBy(UnifiedProduct::getExternalCategoryId, Collectors.counting()));
        stats.put("productsByCategory", categoryStats);

        // Available vs unavailable
        long availableCount = allProducts.stream()
                .mapToLong(product -> product.isAvailable() ? 1 : 0)
                .sum();
        stats.put("availableProducts", availableCount);
        stats.put("unavailableProducts", allProducts.size() - availableCount);

        return stats;
    }
}