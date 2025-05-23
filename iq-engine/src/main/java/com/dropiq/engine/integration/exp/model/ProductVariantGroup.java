package com.dropiq.engine.integration.exp.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a group of product variants (same product, different attributes)
 */
@Data
public class ProductVariantGroup {
    private String groupId;                              // Group identifier
    private String name;                                 // Product name
    private String description;                          // Product description
    private String categoryId;                           // Category ID
    private String categoryName;                         // Category name
    private List<String> imageUrls;                      // Common images
    private Set<UnifiedProduct> variants;                // Product variants
    private Set<SourceType> sourcePlatforms;                 // Source platforms
    private LocalDateTime lastUpdated;                   // Last update timestamp

    public ProductVariantGroup() {
        this.imageUrls = new ArrayList<>();
        this.variants = new HashSet<>();
        this.sourcePlatforms = new HashSet<>();
    }

    /**
     * Create a variant group from a product
     */
    public static ProductVariantGroup fromProduct(UnifiedProduct product) {
        ProductVariantGroup group = new ProductVariantGroup();
        group.setGroupId(product.getGroupId());
        group.setName(product.getName());
        group.setDescription(product.getDescription());
        group.setCategoryId(product.getExternalCategoryId());
        group.setCategoryName(product.getExternalCategoryName());
        group.getImageUrls().addAll(product.getImageUrls());
        group.getVariants().add(product);
        group.getSourcePlatforms().add(product.getSourceType());
        group.setLastUpdated(product.getLastUpdated());
        return group;
    }

    /**
     * Add a product variant to the group
     */
    public void addVariant(UnifiedProduct product) {
        this.variants.add(product);
        this.sourcePlatforms.add(product.getSourceType());
        this.lastUpdated = LocalDateTime.now();
    }
}
