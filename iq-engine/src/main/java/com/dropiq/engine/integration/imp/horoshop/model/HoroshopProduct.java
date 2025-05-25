package com.dropiq.engine.integration.imp.horoshop.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class HoroshopProduct {

    // Basic product info
    @JsonProperty("parent_article")
    private String parentArticle;

    private String article; // SKU/Product ID

    private Map<String, String> title = new HashMap<>(); // Multi-language titles
    private Map<String, String> description = new HashMap<>(); // Multi-language descriptions
    @JsonProperty("short_description")
    private Map<String, String> shortDescription = new HashMap<>();

    // Product attributes
    private String color;
    private String size;
    private String material;
    private String brand;
    private String model;
    private String gtin; // Global Trade Item Number
    private String mpn;  // Manufacturer Part Number

    // Pricing
    private Double price;
    @JsonProperty("price_old")
    private Double priceOld;
    @JsonProperty("wholesale_prices")
    private List<HoroshopWholesalePrice> wholesalePrices = new ArrayList<>();

    // Inventory
    private String presence; // "В наличии", "Нет в наличии", "Под заказ"
    private Integer quantity;

    // Categories
    private String parent; // Category path: "Category / Subcategory"
    @JsonProperty("parent_id")
    private Long parentId; // Category ID (recommended over path)
    @JsonProperty("alt_parent")
    private List<Object> altParent = new ArrayList<>(); // Additional categories

    // SEO & Display
    private String slug;
    @JsonProperty("display_in_showcase")
    private Boolean displayInShowcase = true;
    @JsonProperty("forceAliasUpdate")
    private Boolean forceAliasUpdate = false;

    // Images
    private HoroshopImages images;

    // Marketing
    private List<String> icons = new ArrayList<>(); // "Распродажа", "Новинка", "Хит"
    private Integer popularity = 0;

    // Guarantees & Promotions
    @JsonProperty("guarantee_shop")
    private String guaranteeShop;
    @JsonProperty("guarantee_length")
    private Integer guaranteeLength; // months
    @JsonProperty("countdown_end_time")
    private String countdownEndTime; // "2021-12-31 23:59:59"
    @JsonProperty("countdown_description")
    private Map<String, String> countdownDescription = new HashMap<>();

    // Export settings
    @JsonProperty("export_to_marketplace")
    private String exportToMarketplace; // "Facebook Feed;Rozetka Feed"

    // Characteristics/Attributes
    private List<HoroshopCharacteristic> characteristics = new ArrayList<>();

    // Meta fields
    private Map<String, Object> meta = new HashMap<>();
}
