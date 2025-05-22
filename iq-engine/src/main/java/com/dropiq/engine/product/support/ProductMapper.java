package com.dropiq.engine.product.support;

import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import com.dropiq.engine.product.entity.Product;
import org.springframework.stereotype.Component;

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
        product.setDescription(unifiedProduct.getDescription());
        product.setPrice(unifiedProduct.getPrice());
        product.setStock(unifiedProduct.getStock());
        product.setAvailable(unifiedProduct.isAvailable());
        product.setCategoryId(unifiedProduct.getCategoryId());
        product.setCategoryName(unifiedProduct.getCategoryName());
        product.setSourceType(unifiedProduct.getSourceType());
        product.setSourceUrl(unifiedProduct.getSourceUrl());
        product.setLastUpdated(unifiedProduct.getLastUpdated());

        // Copy collections
        if (unifiedProduct.getImageUrls() != null) {
            product.setImageUrls(new ArrayList<>(unifiedProduct.getImageUrls()));
        }

        if (unifiedProduct.getAttributes() != null) {
            product.setAttributes(new HashMap<>(unifiedProduct.getAttributes()));
        }

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
        unifiedProduct.setDescription(product.getDescription());
        unifiedProduct.setPrice(product.getPrice());
        unifiedProduct.setStock(product.getStock());
        unifiedProduct.setAvailable(product.isAvailable());
        unifiedProduct.setCategoryId(product.getCategoryId());
        unifiedProduct.setCategoryName(product.getCategoryName());
        unifiedProduct.setSourceType(product.getSourceType());
        unifiedProduct.setSourceUrl(product.getSourceUrl());
        unifiedProduct.setLastUpdated(product.getLastUpdated());

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
        existingProduct.setDescription(source.getDescription());
        existingProduct.setPrice(source.getPrice());
        existingProduct.setStock(source.getStock());
        existingProduct.setAvailable(source.isAvailable());
        existingProduct.setCategoryId(source.getCategoryId());
        existingProduct.setCategoryName(source.getCategoryName());
        existingProduct.setSourceType(source.getSourceType());
        existingProduct.setSourceUrl(source.getSourceUrl());
        existingProduct.setLastUpdated(LocalDateTime.now());

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
        existingUnifiedProduct.setDescription(source.getDescription());
        existingUnifiedProduct.setPrice(source.getPrice());
        existingUnifiedProduct.setStock(source.getStock());
        existingUnifiedProduct.setAvailable(source.isAvailable());
        existingUnifiedProduct.setCategoryId(source.getCategoryId());
        existingUnifiedProduct.setCategoryName(source.getCategoryName());
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
        product.setPrice(unifiedProduct.getPrice());
        product.setStock(unifiedProduct.getStock());
        product.setAvailable(unifiedProduct.isAvailable());
        product.setSourceType(unifiedProduct.getSourceType());
        product.setLastUpdated(unifiedProduct.getLastUpdated());

        return product;
    }
}
