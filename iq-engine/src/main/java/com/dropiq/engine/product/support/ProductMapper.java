package com.dropiq.engine.product.support;

import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import com.dropiq.engine.product.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ProductMapper {

    private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 500;
    private static final int MAX_NAME_LENGTH = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_URL_LENGTH = 500;

    /**
     * Convert UnifiedProduct to Product
     */
    public Product toProduct(UnifiedProduct unifiedProduct) {
        if (unifiedProduct == null) {
            return null;
        }

        Product product = new Product();

        try {

            product.setExternalId(truncateString(unifiedProduct.getExternalId(), 255));
            product.setExternalGroupId(truncateString(unifiedProduct.getGroupId(), 255));
            product.setExternalName(truncateString(unifiedProduct.getName(), MAX_NAME_LENGTH));
            product.setExternalDescription(truncateString(unifiedProduct.getDescription(), MAX_DESCRIPTION_LENGTH));

            // Price handling
            if (unifiedProduct.getPrice() != null && unifiedProduct.getPrice() > 0) {
                product.setOriginalPrice(BigDecimal.valueOf(unifiedProduct.getPrice()));
            } else {
                product.setOriginalPrice(BigDecimal.ZERO);
            }

            product.setStock(unifiedProduct.getStock() != null ? unifiedProduct.getStock() : 0);
            product.setAvailable(Boolean.TRUE.equals(unifiedProduct.getAvailable()));

            // Category fields
            product.setExternalCategoryId(truncateString(unifiedProduct.getCategoryId(), 255));
            product.setExternalCategoryName(truncateString(unifiedProduct.getCategoryName(), 255));

            // Source information
            product.setSourceType(unifiedProduct.getSourceType());
            product.setSourceUrl(truncateString(unifiedProduct.getSourceUrl(), MAX_URL_LENGTH));
            product.setUpdatedAt(unifiedProduct.getLastUpdated() != null ? unifiedProduct.getLastUpdated() : LocalDateTime.now());
            product.setLastSync(LocalDateTime.now());

            if (!unifiedProduct.getImageUrls().isEmpty()) {
                product.setMainImageUrl(unifiedProduct.getImageUrls().getFirst());
            }

            product.setNormalizedSizeInfo(unifiedProduct.getSize().getOriginalValue(),
                    unifiedProduct.getSize().getNormalizedValue(),
                    (unifiedProduct.getSize().getType().name()));

            mapEnhancedFields(product, unifiedProduct);

            if (unifiedProduct.getImageUrls() != null && !unifiedProduct.getImageUrls().isEmpty()) {
                List<String> validUrls = unifiedProduct.getImageUrls().stream()
                        .filter(Objects::nonNull)
                        .filter(url -> !url.trim().isEmpty())
                        .map(url -> truncateString(url, MAX_URL_LENGTH))
                        .collect(Collectors.toList());
                product.setImageUrls(validUrls);
            }

            if (unifiedProduct.getAttributes() != null && !unifiedProduct.getAttributes().isEmpty()) {
                Map<String, String> validatedAttributes = new HashMap<>();
                for (Map.Entry<String, String> entry : unifiedProduct.getAttributes().entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().trim().isEmpty()) {
                        String key = truncateString(entry.getKey(), 255);
                        String value = truncateString(entry.getValue(), MAX_ATTRIBUTE_VALUE_LENGTH);
                        validatedAttributes.put(key, value);
                    }
                }
                product.setAttributes(validatedAttributes);
            }

            // Copy platform-specific data with validation
            if (unifiedProduct.getPlatformSpecificData() != null && !unifiedProduct.getPlatformSpecificData().isEmpty()) {
                Map<String, String> validatedPlatformData = new HashMap<>();
                for (Map.Entry<String, String> entry : unifiedProduct.getPlatformSpecificData().entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().trim().isEmpty()) {
                        String key = truncateString(entry.getKey(), 255);
                        String value = truncateString(entry.getValue(), MAX_ATTRIBUTE_VALUE_LENGTH);
                        validatedPlatformData.put(key, value);
                    }
                }
            }

            product.calculateSellingPrice();

        } catch (Exception e) {
            log.error("Error converting UnifiedProduct to Product: {}", e.getMessage(), e);
            return null;
        }

        return product;
    }

    /**
     * Map enhanced fields from UnifiedProduct to Product
     */
    private void mapEnhancedFields(Product product, UnifiedProduct unifiedProduct) {
        try {

            // Handle size information - prevent long size_info by processing attributes properly
            if (unifiedProduct.getSize() != null) {
                UnifiedProduct.ProductSize size = unifiedProduct.getSize();

                if (size.getOriginalValue() != null && !size.getOriginalValue().trim().isEmpty()) {
                    // Only store short size values as attributes
                    String sizeValue = size.getOriginalValue().trim();
                    if (sizeValue.length() <= 50) { // Only short sizes
                        product.getAttributes().put("size_original", sizeValue);
                    }
                }

                if (size.getNormalizedValue() != null && !size.getNormalizedValue().trim().isEmpty()) {
                    product.getAttributes().put("size_normalized", truncateString(size.getNormalizedValue(), 50));
                }

                if (size.getType() != null) {
                    product.getAttributes().put("size_type", size.getType().name());
                }

                if (size.getUnit() != null && !size.getUnit().trim().isEmpty()) {
                    product.getAttributes().put("size_unit", truncateString(size.getUnit(), 20));
                }
            }

        } catch (Exception e) {
            log.debug("Error mapping enhanced fields: {}", e.getMessage());
        }
    }

    /**
     * Convert Product to UnifiedProduct
     */
    public UnifiedProduct toUnifiedProduct(Product product) {
        if (product == null) {
            return null;
        }

        UnifiedProduct unifiedProduct = new UnifiedProduct();

        try {
            // Direct field mapping
            unifiedProduct.setExternalId(product.getExternalId());
            unifiedProduct.setGroupId(product.getExternalGroupId());
            unifiedProduct.setName(product.getExternalName());
            unifiedProduct.setDescription(product.getExternalDescription());

            if (product.getOriginalPrice() != null) {
                unifiedProduct.setPrice(product.getOriginalPrice().doubleValue());
            } else {
                unifiedProduct.setPrice(0.0);
            }

            unifiedProduct.setStock(product.getStock() != null ? product.getStock() : 0);
            unifiedProduct.setAvailable(Boolean.TRUE.equals(product.getAvailable()));
            unifiedProduct.setCategoryId(product.getExternalCategoryId());
            unifiedProduct.setCategoryName(product.getExternalCategoryName());
            unifiedProduct.setSourceType(product.getSourceType());
            unifiedProduct.setSourceUrl(product.getSourceUrl());
            unifiedProduct.setLastUpdated(product.getUpdatedAt() != null ? product.getUpdatedAt() : LocalDateTime.now());

            // Copy collections
            if (product.getImageUrls() != null) {
                unifiedProduct.setImageUrls(new ArrayList<>(product.getImageUrls()));
            }

            if (product.getAttributes() != null) {
                unifiedProduct.setAttributes(new HashMap<>(product.getAttributes()));
            }

            // Map enhanced fields back
            mapEnhancedFieldsBack(unifiedProduct, product);

        } catch (Exception e) {
            log.error("Error converting Product to UnifiedProduct: {}", e.getMessage(), e);
            return null;
        }

        return unifiedProduct;
    }

    /**
     * Map enhanced fields back from Product to UnifiedProduct
     */
    private void mapEnhancedFieldsBack(UnifiedProduct unifiedProduct, Product product) {
        try {
            // Reconstruct size information
            Map<String, String> attributes = product.getAttributes();
            if (attributes != null) {
                String sizeOriginal = attributes.get("size_original");
                String sizeNormalized = attributes.get("size_normalized");
                String sizeTypeStr = attributes.get("size_type");
                String sizeUnit = attributes.get("size_unit");

                if (sizeOriginal != null || sizeNormalized != null || sizeTypeStr != null) {
                    UnifiedProduct.ProductSize size = new UnifiedProduct.ProductSize();
                    size.setOriginalValue(sizeOriginal);
                    size.setNormalizedValue(sizeNormalized);
                    size.setUnit(sizeUnit);

                    if (sizeTypeStr != null) {
                        try {
                            size.setType(UnifiedProduct.ProductSize.SizeType.valueOf(sizeTypeStr));
                        } catch (IllegalArgumentException e) {
                            log.debug("Unknown size type: {}", sizeTypeStr);
                        }
                    }

                    unifiedProduct.setSize(size);
                }
            }

        } catch (Exception e) {
            log.debug("Error mapping enhanced fields back: {}", e.getMessage());
        }
    }

    /**
     * Utility method to truncate strings to avoid database constraint violations
     */
    private String truncateString(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        if (value.length() <= maxLength) {
            return value;
        }

        log.debug("Truncating string from {} to {} characters", value.length(), maxLength);
        return value.substring(0, maxLength - 3) + "...";
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

        try {
            existingProduct.setExternalId(truncateString(source.getExternalId(), 255));
            existingProduct.setExternalGroupId(truncateString(source.getGroupId(), 255));
            existingProduct.setExternalName(truncateString(source.getName(), MAX_NAME_LENGTH));
            existingProduct.setExternalDescription(truncateString(source.getDescription(), MAX_DESCRIPTION_LENGTH));

            if (source.getPrice() != null && source.getPrice() > 0) {
                existingProduct.setOriginalPrice(BigDecimal.valueOf(source.getPrice()));
            }

            existingProduct.setStock(source.getStock() != null ? source.getStock() : 0);
            existingProduct.setAvailable(Boolean.TRUE.equals(source.getAvailable()));
            existingProduct.setExternalCategoryId(truncateString(source.getCategoryId(), 255));
            existingProduct.setExternalCategoryName(truncateString(source.getCategoryName(), 255));
            existingProduct.setSourceType(source.getSourceType());
            existingProduct.setSourceUrl(truncateString(source.getSourceUrl(), MAX_URL_LENGTH));
            existingProduct.setUpdatedAt(LocalDateTime.now());
            existingProduct.setLastSync(LocalDateTime.now());

            // Update enhanced fields
            mapEnhancedFields(existingProduct, source);

            // Update collections
            if (source.getImageUrls() != null) {
                existingProduct.getImageUrls().clear();
                List<String> validUrls = source.getImageUrls().stream()
                        .filter(Objects::nonNull)
                        .filter(url -> !url.trim().isEmpty())
                        .map(url -> truncateString(url, MAX_URL_LENGTH))
                        .collect(Collectors.toList());
                existingProduct.getImageUrls().addAll(validUrls);
            }

            if (source.getAttributes() != null) {
                existingProduct.getAttributes().clear();
                Map<String, String> validatedAttributes = new HashMap<>();
                for (Map.Entry<String, String> entry : source.getAttributes().entrySet()) {
                    if (entry.getKey() != null && !entry.getKey().trim().isEmpty()) {
                        String key = truncateString(entry.getKey(), 255);
                        String value = truncateString(entry.getValue(), MAX_ATTRIBUTE_VALUE_LENGTH);
                        validatedAttributes.put(key, value);
                    }
                }
                existingProduct.getAttributes().putAll(validatedAttributes);
            }

            existingProduct.calculateSellingPrice();

        } catch (Exception e) {
            log.error("Error updating Product: {}", e.getMessage(), e);
        }
    }

    /**
     * Sync stock and availability only
     */
    public void syncStockAndAvailable(Product product, UnifiedProduct existingUnifiedProduct) {
        if (existingUnifiedProduct == null || product == null) {
            return;
        }

        try {
            product.setStock(existingUnifiedProduct.getStock() != null ? existingUnifiedProduct.getStock() : 0);
            product.setAvailable(Boolean.TRUE.equals(existingUnifiedProduct.getAvailable()) && product.getStock() > 0);
            product.setLastSync(LocalDateTime.now());

            // Update price if changed
            if (existingUnifiedProduct.getPrice() != null && existingUnifiedProduct.getPrice() > 0) {
                product.setOriginalPrice(BigDecimal.valueOf(existingUnifiedProduct.getPrice()));
                product.calculateSellingPrice();
            }

        } catch (Exception e) {
            log.error("Error syncing stock and availability: {}", e.getMessage(), e);
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
        product.setExternalId(truncateString(unifiedProduct.getExternalId(), 255));
        product.setExternalName(truncateString(unifiedProduct.getName(), MAX_NAME_LENGTH));

        if (unifiedProduct.getPrice() != null && unifiedProduct.getPrice() > 0) {
            product.setOriginalPrice(BigDecimal.valueOf(unifiedProduct.getPrice()));
        } else {
            product.setOriginalPrice(BigDecimal.ZERO);
        }

        product.setStock(unifiedProduct.getStock() != null ? unifiedProduct.getStock() : 0);
        product.setAvailable(Boolean.TRUE.equals(unifiedProduct.getAvailable()));
        product.setSourceType(unifiedProduct.getSourceType());
        product.setUpdatedAt(unifiedProduct.getLastUpdated() != null ? unifiedProduct.getLastUpdated() : LocalDateTime.now());

        return product;
    }
}