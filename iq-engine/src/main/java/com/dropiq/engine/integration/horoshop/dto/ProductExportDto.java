package com.dropiq.engine.integration.horoshop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductExportDto {
    // Basic product information
    @JsonProperty("parent_article")
    private String parentArticle;

    @JsonProperty("article")
    private String article;

    @JsonProperty("article_for_display")
    private String articleForDisplay;

    @JsonProperty("creation_time")
    private LocalDateTime creationTime;

    @JsonProperty("title")
    private MultilingualText title;

    @JsonProperty("mod_title")
    private MultilingualText modTitle;

    @JsonProperty("description")
    private MultilingualText description;

    @JsonProperty("short_description")
    private MultilingualText shortDescription;

    // SEO fields
    @JsonProperty("seo_title")
    private MultilingualText seoTitle;

    @JsonProperty("seo_keywords")
    private MultilingualText seoKeywords;

    @JsonProperty("seo_description")
    private MultilingualText seoDescription;

    @JsonProperty("h1_title")
    private MultilingualText h1Title;

    // Display and availability
    @JsonProperty("display_in_showcase")
    private Integer displayInShowcase;

    @JsonProperty("presence")
    private ReferenceObject presence;

    @JsonProperty("we_recommended")
    private Integer weRecommended;

    @JsonProperty("quantity")
    private Integer quantity;

    // Pricing
    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("price_old")
    private BigDecimal priceOld;

    @JsonProperty("discount")
    private BigDecimal discount;

    @JsonProperty("currency")
    private ReferenceObject currency;

    // Categories
    @JsonProperty("parent")
    private ReferenceObject parent;

    @JsonProperty("alt_parent")
    private List<ReferenceObject> altParent;

    // Product attributes
    @JsonProperty("color")
    private ReferenceObject color;

    @JsonProperty("brand")
    private ReferenceObject brand;

    @JsonProperty("gtin")
    private String gtin;

    @JsonProperty("mpn")
    private String mpn;

    @JsonProperty("popularity")
    private Integer popularity;

    @JsonProperty("unit_of_measurement")
    private ReferenceObject unitOfMeasurement;

    // Lists and arrays
    @JsonProperty("icons")
    private List<ReferenceObject> icons;

    // Characteristics
    @JsonProperty("characteristics")
    private Map<String, ReferenceObject> characteristics;

    // Relations
    @JsonProperty("accessories")
    private List<AccessoryReference> accessories;

    @JsonProperty("gifts")
    private List<AccessoryReference> gifts;

    // Inventory
    @JsonProperty("residues")
    private List<WarehouseResidue> residues;

    // Images
    @JsonProperty("images")
    private List<String> images;

    @JsonProperty("gallery_common")
    private List<String> galleryCommon;

    @JsonProperty("gallery_360")
    private List<String> gallery360;

    // URL information
    @JsonProperty("slug")
    private String slug;

    @JsonProperty("link")
    private String link;
}
