package com.dropiq.engine.product.support;

import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import com.dropiq.engine.product.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    /**
     * Convert UnifiedProduct to Product
     */
    public Product toProduct(UnifiedProduct unifiedProduct) {
        if (unifiedProduct == null) {
            return null;
        }

        Product product = new Product();

        // Direct field mapping
        product.setExternalId(unifiedProduct.getExternalId());
        product.setGroupId(unifiedProduct.getGroupId());
        product.setName(unifiedProduct.getName());
        product.setOriginalDescription(unifiedProduct.getDescription());
        product.setOriginalPrice(BigDecimal.valueOf(unifiedProduct.getPrice()));
        product.setStock(unifiedProduct.getStock());
        product.setAvailable(unifiedProduct.isAvailable());
        product.setExternalCategoryId(unifiedProduct.getExternalCategoryId());
        product.setExternalCategoryName(unifiedProduct.getExternalCategoryName());
        product.setSourceType(unifiedProduct.getSourceType());
        product.setSourceUrl(unifiedProduct.getSourceUrl());
        product.setUpdatedAt(unifiedProduct.getLastUpdated());
        product.setLastSync(LocalDateTime.now());

        // Copy collections
        if (unifiedProduct.getImageUrls() != null) {
            product.setImageUrls(new ArrayList<>(unifiedProduct.getImageUrls()));
        }

        if (unifiedProduct.getAttributes() != null) {
            product.setAttributes(new HashMap<>(unifiedProduct.getAttributes()));
        }

        product.calculateSellingPrice();

        return product;
    }

    /**
     * Convert Product to UnifiedProduct
     */
    public UnifiedProduct toUnifiedProduct(Product product) {
        if (product == null) {
            return null;
        }

        UnifiedProduct unifiedProduct = new UnifiedProduct();

        // Direct field mapping
        unifiedProduct.setExternalId(product.getExternalId());
        unifiedProduct.setGroupId(product.getGroupId());
        unifiedProduct.setName(product.getName());
        unifiedProduct.setDescription(product.getOriginalDescription());
        unifiedProduct.setPrice(product.getOriginalPrice().doubleValue());
        unifiedProduct.setStock(product.getStock());
        unifiedProduct.setAvailable(product.getAvailable());
        unifiedProduct.setExternalCategoryId(product.getExternalCategoryId());
        unifiedProduct.setExternalCategoryName(product.getExternalCategoryName());
        unifiedProduct.setSourceType(product.getSourceType());
        unifiedProduct.setSourceUrl(product.getSourceUrl());
        unifiedProduct.setLastUpdated(product.getUpdatedAt());

        // Copy collections
        if (product.getImageUrls() != null) {
            unifiedProduct.setImageUrls(new ArrayList<>(product.getImageUrls()));
        }

        if (product.getAttributes() != null) {
            unifiedProduct.setAttributes(new HashMap<>(product.getAttributes()));
        }

        // Initialize empty collections for UnifiedProduct-specific fields
        unifiedProduct.setPlatformSpecificData(new HashMap<>());

        return unifiedProduct;
    }

    /**
     * Convert list of UnifiedProducts to Products
     */
    public List<Product> toProductList(List<UnifiedProduct> unifiedProducts) {
        if (unifiedProducts == null) {
            return new ArrayList<>();
        }

        return unifiedProducts.stream()
                .map(this::toProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Set<Product> toProductSet(Set<UnifiedProduct> unifiedProducts) {
        if (unifiedProducts == null) {
            return new HashSet<>();
        }

        return unifiedProducts.stream()
                .map(this::toProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Convert list of Products to UnifiedProducts
     */
    public List<UnifiedProduct> toUnifiedProductList(List<Product> products) {
        if (products == null) {
            return new ArrayList<>();
        }

        return products.stream()
                .map(this::toUnifiedProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<UnifiedProduct> toUnifiedProductList(Set<Product> products) {
        if (products == null) {
            return new ArrayList<>();
        }

        return products.stream()
                .map(this::toUnifiedProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Update existing Product with data from UnifiedProduct
     */
    public void updateProduct(Product existingProduct, UnifiedProduct source) {
        if (existingProduct == null || source == null) {
            return;
        }

        existingProduct.setExternalId(source.getExternalId());
        existingProduct.setGroupId(source.getGroupId());
        existingProduct.setName(source.getName());
        existingProduct.setOriginalDescription(source.getDescription());
        existingProduct.setOriginalPrice(BigDecimal.valueOf(source.getPrice()));
        existingProduct.setStock(source.getStock());
        existingProduct.setAvailable(source.isAvailable());
        existingProduct.setExternalCategoryId(source.getExternalCategoryId());
        existingProduct.setExternalCategoryName(source.getExternalCategoryName());
        existingProduct.setSourceType(source.getSourceType());
        existingProduct.setSourceUrl(source.getSourceUrl());
        existingProduct.setUpdatedAt(LocalDateTime.now());

        // Update collections
        if (source.getImageUrls() != null) {
            existingProduct.getImageUrls().clear();
            existingProduct.getImageUrls().addAll(source.getImageUrls());
        }

        if (source.getAttributes() != null) {
            existingProduct.getAttributes().clear();
            existingProduct.getAttributes().putAll(source.getAttributes());
        }
    }

    public void syncStockAndAvailable(Product product, UnifiedProduct existingUnifiedProduct) {
        if (existingUnifiedProduct == null || product == null) {
            return;
        }

        product.setStock(existingUnifiedProduct.getStock());
        product.setAvailable(existingUnifiedProduct.getStock() > 0);
        product.setExternalId(existingUnifiedProduct.getExternalId());
        product.setExternalCategoryId(existingUnifiedProduct.getExternalCategoryId());
        product.setExternalCategoryName(existingUnifiedProduct.getExternalCategoryName());
        product.setSourceType(existingUnifiedProduct.getSourceType());
        product.setSourceUrl(existingUnifiedProduct.getSourceUrl());
        product.setUpdatedAt(existingUnifiedProduct.getLastUpdated());
        product.setLastSync(LocalDateTime.now());
        if (existingUnifiedProduct.getImageUrls() != null) {
            product.setImageUrls(new ArrayList<>(existingUnifiedProduct.getImageUrls()));
        }
    }

    /**
     * Update existing UnifiedProduct with data from Product
     */
    public void updateUnifiedProduct(UnifiedProduct existingUnifiedProduct, Product source) {
        if (existingUnifiedProduct == null || source == null) {
            return;
        }

        existingUnifiedProduct.setExternalId(source.getExternalId());
        existingUnifiedProduct.setGroupId(source.getGroupId());
        existingUnifiedProduct.setName(source.getName());
        existingUnifiedProduct.setDescription(source.getOriginalDescription());
        existingUnifiedProduct.setPrice(source.getOriginalPrice().doubleValue());
        existingUnifiedProduct.setStock(source.getStock());
        existingUnifiedProduct.setAvailable(source.getAvailable());
        existingUnifiedProduct.setExternalCategoryId(source.getExternalCategoryId());
        existingUnifiedProduct.setExternalCategoryName(source.getExternalCategoryName());
        existingUnifiedProduct.setSourceType(source.getSourceType());
        existingUnifiedProduct.setSourceUrl(source.getSourceUrl());
        existingUnifiedProduct.setLastUpdated(LocalDateTime.now());

        // Update collections
        if (source.getImageUrls() != null) {
            existingUnifiedProduct.getImageUrls().clear();
            existingUnifiedProduct.getImageUrls().addAll(source.getImageUrls());
        }

        if (source.getAttributes() != null) {
            existingUnifiedProduct.getAttributes().clear();
            existingUnifiedProduct.getAttributes().putAll(source.getAttributes());
        }
    }

    /**
     * Create a partial copy with only essential fields
     */
    public Product toProductSummary(UnifiedProduct unifiedProduct) {
        if (unifiedProduct == null) {
            return null;
        }

        Product product = new Product();
        product.setExternalId(unifiedProduct.getExternalId());
        product.setName(unifiedProduct.getName());
        product.setOriginalPrice(BigDecimal.valueOf(unifiedProduct.getPrice()));
        product.setStock(unifiedProduct.getStock());
        product.setAvailable(unifiedProduct.isAvailable());
        product.setSourceType(unifiedProduct.getSourceType());
        product.setUpdatedAt(unifiedProduct.getLastUpdated());

        return product;
    }
}
