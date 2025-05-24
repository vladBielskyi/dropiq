package com.dropiq.admin.model;

import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.SourceType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Virtual entity for grouping products in TreeDataGrid
 */
@Getter
@Setter
public class ProductGroup {

    private String id;
    private String name;
    private String groupId;
    private SourceType sourceType;
    private String displayName;
    private boolean isGroup;
    private int productCount;
    private List<Object> children = new ArrayList<>();

    // Constructor for group nodes
    public ProductGroup(String groupId, SourceType sourceType, int productCount) {
        this.id = generateId(groupId, sourceType);
        this.groupId = groupId;
        this.sourceType = sourceType;
        this.productCount = productCount;
        this.isGroup = true;
        this.displayName = createDisplayName();
    }

    // Constructor for product nodes
    public ProductGroup(Product product) {
        this.id = "product_" + product.getId();
        this.name = product.getName();
        this.groupId = product.getGroupId();
        this.sourceType = product.getSourceType();
        this.isGroup = false;
        this.displayName = product.getName();
        this.children.add(product);
    }

    private String generateId(String groupId, SourceType sourceType) {
        return "group_" + (groupId != null ? groupId : "no_group") + "_" + sourceType.name();
    }

    private String createDisplayName() {
        if (groupId != null && !groupId.isEmpty()) {
            return String.format("Group: %s (%s) - %d products", groupId, sourceType.name(), productCount);
        } else {
            return String.format("Ungrouped (%s) - %d products", sourceType.name(), productCount);
        }
    }

    public void addChild(Object child) {
        this.children.add(child);
        if (child instanceof Product) {
            this.productCount++;
        }
    }
}
