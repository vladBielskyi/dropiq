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

    private String externalId;
    private String groupId;
    private String name;
    private String description;
    private Double price;
    private Integer stock;
    private Boolean available;

    private String categoryId;
    private String categoryName;

    private ProductSize size;
    private List<String> imageUrls = new ArrayList<>();
    private Map<String, String> attributes = new HashMap<>();
    private Map<String, String> platformSpecificData = new HashMap<>();

    private SourceType sourceType;
    private String sourceUrl;
    private LocalDateTime lastUpdated;

    private String brand;
    private String model;
    private String color;
    private String material;

    @Data
    public static class ProductSize {
        private String originalValue;
        private SizeType type;
        private String normalizedValue;
        private String unit;
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
}