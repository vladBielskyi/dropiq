package com.dropiq.engine.integration.imp.horoshop.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class HoroshopWholesalePrice {
    @JsonProperty("minimal_threshold")
    private String minimalThreshold;
    private Double price;
}
