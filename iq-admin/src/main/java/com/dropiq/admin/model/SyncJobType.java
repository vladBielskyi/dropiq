package com.dropiq.admin.model;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum SyncJobType implements EnumClass<String> {
    DATASET_SYNC("Full dataset synchronization"),
    PRODUCT_UPDATE("Update product information"),
    AI_OPTIMIZATION("AI-powered optimization"),
    PRICE_UPDATE("Price synchronization"),
    STOCK_UPDATE("Stock level synchronization"),
    CATEGORY_SYNC("Category synchronization"),
    IMAGE_SYNC("Product image synchronization");

    private final String description;

    SyncJobType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String getId() {
        return name();
    }
}
