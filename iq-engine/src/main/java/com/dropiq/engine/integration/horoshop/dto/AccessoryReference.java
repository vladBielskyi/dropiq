package com.dropiq.engine.integration.horoshop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessoryReference {
    @JsonProperty("article")
    private String article;

    @JsonProperty("page")
    private Object page; // Can be String or ParentReference
}
