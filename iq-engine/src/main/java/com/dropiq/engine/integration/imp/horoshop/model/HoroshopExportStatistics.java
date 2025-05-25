package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class HoroshopExportStatistics {
    private Integer totalProducts;
    private Integer totalCategories;
    private Integer activeProducts;
    private Integer inactiveProducts;
    private Double averagePrice;
    private Map<String, Long> productsByCategory;
    private LocalDateTime fetchTime = LocalDateTime.now();
}
