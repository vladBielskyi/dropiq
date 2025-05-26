package com.dropiq.engine.integration.exp.model;

import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class UnifiedProduct {

    // Базові поля
    private String externalId;
    private String groupId; // Для групування варіантів товару
    private String name;
    private String description;
    private Double price;
    private Integer stock;
    private Boolean available;

    // Категорізація
    private String externalCategoryId;
    private String externalCategoryName;

    // Розміри та варіанти
    private ProductSize size;
    private List<ProductVariant> variants = new ArrayList<>();

    // Зображення
    private List<String> imageUrls = new ArrayList<>();

    // Атрибути
    private Map<String, String> attributes = new HashMap<>();

    // Платформо-специфічні дані
    private Map<String, String> platformSpecificData = new HashMap<>();

    // Мета-дані
    private SourceType sourceType;
    private String sourceUrl;
    private LocalDateTime lastUpdated;

    // Додаткові поля для SEO та AI
    private String seoTitle;
    private String seoDescription;
    private List<String> tags = new ArrayList<>();
    private String brand;
    private String model;
    private String color;
    private String material;
    private String country;

    // Розмірна сітка та фізичні характеристики
    private PhysicalDimensions dimensions;
    private Double weight;

    @Data
    public static class ProductSize {
        private String originalValue; // Оригінальне значення з XML
        private SizeType type; // Тип розміру
        private String normalizedValue; // Нормалізоване значення
        private String unit; // Одиниця виміру
        private Map<String, String> additionalSizes = new HashMap<>(); // Додаткові розміри

        @Getter
        public enum SizeType {
            CLOTHING_ALPHA("S", "M", "L", "XL", "XXL", "3XL", "4XL"),
            CLOTHING_NUMERIC("36", "38", "40", "42", "44", "46", "48", "50", "52", "54"),
            SHOES_EU("36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46"),
            SHOES_US("6", "7", "8", "9", "10", "11", "12", "13"),
            PANTS_WAIST("28", "29", "30", "31", "32", "33", "34", "36", "38"),
            COMBINED("S/M", "L/XL", "XXL/3XL", "2xl/3xl"),
            ACCESSORIES,
            UNKNOWN;

            private final String[] commonValues;

            SizeType(String... commonValues) {
                this.commonValues = commonValues;
            }

        }
    }

    @Data
    public static class ProductVariant {
        private String id;
        private String name;
        private ProductSize size;
        private String color;
        private Double price;
        private Integer stock;
        private Boolean available;
        private String barcode;
        private List<String> imageUrls = new ArrayList<>();
        private Map<String, String> attributes = new HashMap<>();
    }

    @Data
    public static class PhysicalDimensions {
        private Double length;
        private Double width;
        private Double height;
        private String unit = "cm";
    }

    // Утилітні методи
    public boolean hasVariants() {
        return variants != null && !variants.isEmpty();
    }

    public void addVariant(ProductVariant variant) {
        if (this.variants == null) {
            this.variants = new ArrayList<>();
        }
        this.variants.add(variant);
    }

    public void addImageUrl(String url) {
        if (url != null && !url.trim().isEmpty() && !this.imageUrls.contains(url)) {
            this.imageUrls.add(url);
        }
    }

    public void addAttribute(String key, String value) {
        if (key != null && !key.trim().isEmpty()) {
            this.attributes.put(key, value != null ? value : "");
        }
    }

    public String getPrimaryImageUrl() {
        return imageUrls.isEmpty() ? null : imageUrls.get(0);
    }

    public boolean isInStock() {
        return stock != null && stock > 0;
    }

    public String getFormattedPrice() {
        if (price == null) return "0";
        return String.format("%.2f", price);
    }
}