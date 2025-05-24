package com.dropiq.admin.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Filter criteria for products
 */
@Getter
@Setter
public class ProductFilterCriteria {

    // Text filters
    private String nameFilter;
    private String categoryFilter;
    private String descriptionFilter;

    // Source filters
    private Set<SourceType> sourceTypes;
    private Set<String> groupIds;

    // Price filters
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    // Stock filters
    private Integer minStock;
    private Integer maxStock;
    private Boolean availableOnly;

    // Status filters
    private Set<ProductStatus> statuses;

    // AI/SEO filters
    private Boolean aiOptimizedOnly;
    private Boolean hasSeoTitle;
    private Boolean hasSeoDescription;

    // Date filters
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private LocalDateTime updatedAfter;
    private LocalDateTime updatedBefore;

    // Special filters
    private Boolean hasImages;
    private Boolean hasGroupId;
    private String attributeKey;
    private String attributeValue;

    public boolean isEmpty() {
        return (nameFilter == null || nameFilter.trim().isEmpty()) &&
                (categoryFilter == null || categoryFilter.trim().isEmpty()) &&
                (descriptionFilter == null || descriptionFilter.trim().isEmpty()) &&
                (sourceTypes == null || sourceTypes.isEmpty()) &&
                (groupIds == null || groupIds.isEmpty()) &&
                minPrice == null && maxPrice == null &&
                minStock == null && maxStock == null &&
                availableOnly == null &&
                (statuses == null || statuses.isEmpty()) &&
                aiOptimizedOnly == null &&
                hasSeoTitle == null && hasSeoDescription == null &&
                createdAfter == null && createdBefore == null &&
                updatedAfter == null && updatedBefore == null &&
                hasImages == null && hasGroupId == null &&
                (attributeKey == null || attributeKey.trim().isEmpty()) &&
                (attributeValue == null || attributeValue.trim().isEmpty());
    }
}