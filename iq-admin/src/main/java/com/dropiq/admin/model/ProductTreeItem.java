package com.dropiq.admin.model;

import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.SourceType;
import io.jmix.core.metamodel.annotation.JmixEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Tree item for grouping products in TreeDataGrid
 */
@Getter
@Setter
@JmixEntity
public class ProductTreeItem {

    private String id;
    private String name;
    private String displayName;
    private ProductTreeItemType type;
    private Product data; // Can be Product or grouping info
    private List<ProductTreeItem> children = new ArrayList<>();
    private ProductTreeItem parent;

    // Grouping properties
    private SourceType sourceType;
    private String groupId;
    private String category;
    private Integer productCount;
    private BigDecimal totalValue;

    public enum ProductTreeItemType {
        SOURCE_GROUP,    // MYDROP, EASYDROP
        CATEGORY_GROUP,  // Category within source
        VARIANT_GROUP,   // Products with same groupId
        PRODUCT          // Individual product
    }

    // Constructor for source group
    public static ProductTreeItem createSourceGroup(SourceType sourceType, int productCount, BigDecimal totalValue) {
        ProductTreeItem item = new ProductTreeItem();
        item.type = ProductTreeItemType.SOURCE_GROUP;
        item.sourceType = sourceType;
        item.productCount = productCount;
        item.totalValue = totalValue;
        item.id = "source_" + sourceType.name();
        item.name = sourceType.name();
        item.displayName = String.format("%s (%d products, %.2f UAH)",
                sourceType.name(), productCount, totalValue != null ? totalValue : 0);
        return item;
    }

    // Constructor for category group
    public static ProductTreeItem createCategoryGroup(String category, SourceType sourceType,
                                                      int productCount, BigDecimal totalValue) {
        ProductTreeItem item = new ProductTreeItem();
        item.type = ProductTreeItemType.CATEGORY_GROUP;
        item.category = category != null ? category : "Uncategorized";
        item.sourceType = sourceType;
        item.productCount = productCount;
        item.totalValue = totalValue;
        item.id = "category_" + sourceType.name() + "_" + (category != null ? category : "none");
        item.name = item.category;
        item.displayName = String.format("%s (%d products, %.2f UAH)",
                item.category, productCount, totalValue != null ? totalValue : 0);
        return item;
    }

    // Constructor for variant group
    public static ProductTreeItem createVariantGroup(String groupId, SourceType sourceType, String category,
                                                     int productCount, BigDecimal totalValue) {
        ProductTreeItem item = new ProductTreeItem();
        item.type = ProductTreeItemType.VARIANT_GROUP;
        item.groupId = groupId;
        item.sourceType = sourceType;
        item.category = category;
        item.productCount = productCount;
        item.totalValue = totalValue;
        item.id = "variant_" + sourceType.name() + "_" + (groupId != null ? groupId : "single");
        item.name = groupId != null ? groupId : "Single Products";
        item.displayName = String.format("Group: %s (%d variants, %.2f UAH)",
                item.name, productCount, totalValue != null ? totalValue : 0);
        return item;
    }

    // Constructor for product
    public static ProductTreeItem createProduct(Product product) {
        ProductTreeItem item = new ProductTreeItem();
        item.type = ProductTreeItemType.PRODUCT;
        item.data = product;
        item.sourceType = product.getSourceType();
        item.groupId = product.getGroupId();
        item.category = product.getExternalCategoryName();
        item.id = "product_" + product.getId();
        item.name = product.getName();
        item.displayName = product.getName();
        return item;
    }

    public void addChild(ProductTreeItem child) {
        child.setParent(this);
        this.children.add(child);
    }

    public Product getProduct() {
        return type == ProductTreeItemType.PRODUCT ? (Product) data : null;
    }

    public boolean isGroup() {
        return type != ProductTreeItemType.PRODUCT;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}