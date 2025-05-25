package com.dropiq.engine.integration.imp.horoshop.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HoroshopImportSettings {
    @JsonProperty("update_existing")
    private Boolean updateExisting = true;
    @JsonProperty("create_categories")
    private Boolean createCategories = true;
    @JsonProperty("ignore_errors")
    private Boolean ignoreErrors = false;
    @JsonProperty("batch_size")
    private Integer batchSize = 100;
}
