package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HoroshopExportSettings {
    private Boolean includeImages = true;
    private Boolean includePrices = true;
    private Boolean includeStock = true;
    private Boolean includeCategories = true;
    private Boolean includeCharacteristics = true;
    private List<String> languages = List.of("ru", "ua", "en");
    private List<Long> categoryIds = new ArrayList<>(); // Filter by categories
    private String priceType = "retail"; // "retail", "wholesale"
}
