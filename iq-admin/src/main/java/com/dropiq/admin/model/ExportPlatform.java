package com.dropiq.admin.model;

public enum ExportPlatform {
    HOROSHOP("Horoshop"),
    PROM_UA("Prom.ua"),
    OLX("OLX"),
    SHOPIFY("Shopify"),
    CUSTOM_API("Custom API");

    private final String displayName;

    ExportPlatform(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
