package com.dropiq.engine.product.model;

public enum SyncJobType {
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
}
