package com.dropiq.admin.model;

import com.dropiq.admin.entity.Product;
import io.jmix.core.metamodel.annotation.JmixEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JmixEntity
public class ProductDisplayItem {

    private String id;
    private String displayName;
    private ProductDisplayType type;
    private Product product; // For individual products
    private List<Product> variants; // For grouped products
    private String groupId;
    private Integer variantCount;
    private BigDecimal totalValue;
    private Boolean expanded = false;

    public enum ProductDisplayType {
        SINGLE_PRODUCT,    // Individual product without variants
        PRODUCT_GROUP,     // Group header for products with same groupId
        VARIANT_PRODUCT    // Individual variant within a group
    }

    // Constructor for single product
    public static ProductDisplayItem createSingleProduct(Product product) {
        ProductDisplayItem item = new ProductDisplayItem();
        item.type = ProductDisplayType.SINGLE_PRODUCT;
        item.product = product;
        item.id = "single_" + product.getId();
        item.displayName = product.getName();
        item.groupId = product.getGroupId();
        return item;
    }

    // Constructor for product group
    public static ProductDisplayItem createProductGroup(String groupId, List<Product> variants) {
        ProductDisplayItem item = new ProductDisplayItem();
        item.type = ProductDisplayType.PRODUCT_GROUP;
        item.variants = new ArrayList<>(variants);
        item.groupId = groupId;
        item.variantCount = variants.size();
        item.id = "group_" + groupId;

        // Calculate total value
        item.totalValue = variants.stream()
                .filter(p -> p.getOriginalPrice() != null)
                .map(Product::getOriginalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Use first product's name as base name
        Product firstProduct = variants.get(0);
        item.displayName = String.format("📦 %s (%d variants, %.2f UAH)",
                firstProduct.getName(), variants.size(), item.totalValue);

        return item;
    }

    // Constructor for variant within group
    public static ProductDisplayItem createVariantProduct(Product product) {
        ProductDisplayItem item = new ProductDisplayItem();
        item.type = ProductDisplayType.VARIANT_PRODUCT;
        item.product = product;
        item.id = "variant_" + product.getId();
        item.displayName = "  ↳ " + product.getName(); // Indented to show it's a variant
        item.groupId = product.getGroupId();
        return item;
    }

    public boolean isGroup() {
        return type == ProductDisplayType.PRODUCT_GROUP;
    }

    public boolean isSingleProduct() {
        return type == ProductDisplayType.SINGLE_PRODUCT;
    }

    public boolean isVariant() {
        return type == ProductDisplayType.VARIANT_PRODUCT;
    }

    public boolean isExpanded() {
        return expanded;
    }

    // Get the actual product for operations
    public Product getActualProduct() {
        if (type == ProductDisplayType.PRODUCT_GROUP) {
            return variants != null && !variants.isEmpty() ? variants.get(0) : null;
        }
        return product;
    }

    // Get all products in this item (for groups, returns all variants; for single/variant, returns single product)
    public List<Product> getAllProducts() {
        if (type == ProductDisplayType.PRODUCT_GROUP) {
            return variants != null ? variants : new ArrayList<>();
        } else if (product != null) {
            List<Product> singleList = new ArrayList<>();
            singleList.add(product);
            return singleList;
        }
        return new ArrayList<>();
    }
}