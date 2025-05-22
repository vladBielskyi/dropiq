package com.dropiq.engine.product.dto;

import com.dropiq.engine.integration.exp.model.DataSourceConfig;
import lombok.Data;

import java.util.List;

@Data
public class CreateDataSetRequest {
    private String name;
    private String description;
    private List<DataSourceConfig> dataSources;
}
