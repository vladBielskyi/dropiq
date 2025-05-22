package com.dropiq.engine.product.model;

import com.dropiq.engine.integration.exp.model.SourceType;
import lombok.Data;

import java.util.Map;

@Data
public class DataSetStatistics {
    private Long datasetId;
    private String datasetName;
    private int totalProducts;
    private int availableProducts;
    private int unavailableProducts;
    private Map<SourceType, Long> productsByPlatform;
    private Map<String, Long> productsByCategory;
    private Double averagePrice;
}
