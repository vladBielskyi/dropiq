package com.dropiq.engine.integration.horoshop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductImportDto extends BaseHoroshopObject {
    // Basic product information
    @JsonProperty("parent_article")
    private String parentArticle;

    @JsonProperty("article")
    private String article;

    @JsonProperty("article_for_display")
    private String articleForDisplay;

    @JsonProperty("title")
    private Object title; // Can be String or MultilingualText

    @JsonProperty("mod_title")
    private Object modTitle; // Can be String or MultilingualText

    @JsonProperty("description")
    private Object description; // Can be String or MultilingualText

    @JsonProperty("short_description")
    private Object shortDescription; // Can be String or MultilingualText

    @JsonProperty("marketplace_description")
    private Object marketplaceDescription; // Can be String or MultilingualText

    // SEO fields
    @JsonProperty("seo_title")
    private Object seoTitle; // Can be String or MultilingualText

    @JsonProperty("seo_keywords")
    private Object seoKeywords; // Can be String or MultilingualText

    @JsonProperty("seo_description")
    private Object seoDescription; // Can be String or MultilingualText

    @JsonProperty("h1_title")
    private Object h1Title; // Can be String or MultilingualText

    // Display and availability
    @JsonProperty("display_in_showcase")
    private Boolean displayInShowcase;

    @JsonProperty("presence")
    private String presence;

    // Pricing
    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("price_old")
    private BigDecimal priceOld;

    @JsonProperty("discount")
    private BigDecimal discount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("wholesale_prices")
    private List<WholesalePrice> wholesalePrices;

    @JsonProperty("price_levels")
    private List<PriceLevel> priceLevels;

    // Categories
    @JsonProperty("parent")
    private Object parent; // Can be String or ParentReference

    @JsonProperty("alt_parent")
    private List<Object> altParent; // List of String or ParentReference

    // Product attributes
    @JsonProperty("color")
    private String color;

    @JsonProperty("brand")
    private String brand;

    @JsonProperty("gtin")
    private String gtin;

    @JsonProperty("mpn")
    private String mpn;

    @JsonProperty("popularity")
    private Integer popularity;

    @JsonProperty("guarantee_shop")
    private String guaranteeShop;

    @JsonProperty("guarantee_length")
    private Integer guaranteeLength;

    @JsonProperty("countdown_end_time")
    private String countdownEndTime;

    @JsonProperty("countdown_description")
    private Object countdownDescription; // Can be String or MultilingualText

    @JsonProperty("uktzed")
    private String uktzed;

    @JsonProperty("condition")
    private Object condition; // Can be String or ReferenceObject

    @JsonProperty("adult")
    private Boolean adult;

    @JsonProperty("unit_of_measurement")
    private String unitOfMeasurement;

    @JsonProperty("multiplicity")
    private Integer multiplicity;

    @JsonProperty("minimal_order")
    private Integer minimalOrder;

    @JsonProperty("installments_payment")
    private String installmentsPayment;

    @JsonProperty("monobank_installments_payment")
    private String monobankInstallmentsPayment;

    // Lists and arrays
    @JsonProperty("icons")
    private List<String> icons;

    @JsonProperty("export_to_marketplace")
    private String exportToMarketplace;

    // Characteristics
    @JsonProperty("characteristics")
    private Map<String, Object> characteristics;

    // Relations
    @JsonProperty("accessories")
    private List<Object> accessories; // List of String or AccessoryReference

    @JsonProperty("gifts")
    private List<Object> gifts; // List of String or AccessoryReference

    @JsonProperty("supplier_id")
    private SupplierReference supplierId;

    // Inventory
    @JsonProperty("residues")
    private List<WarehouseResidue> residues;

    // Images
    @JsonProperty("images")
    private GalleryConfig images;

    @JsonProperty("gallery_common")
    private GalleryConfig galleryCommon;

    @JsonProperty("gallery_360")
    private GalleryConfig gallery360;

    // URL configuration
    @JsonProperty("forceAliasUpdate")
    private Boolean forceAliasUpdate;

    @JsonProperty("slug")
    private String slug;
}
