package com.dropiq.engine.product.model;

import com.dropiq.engine.integration.exp.model.SourceType;
import lombok.Data;

import java.util.List;

@Data
public class DataSetFilter {
    private List<SourceType> sourceTypes;
    private Double minPrice;
    private Double maxPrice;
    private Boolean availableOnly;
    private List<String> categoryIds;
    private String searchTerm;
}
