package com.dropiq.engine.integration.horoshop.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductExportRequest {
    @JsonProperty("token")
    private String token;

    @JsonProperty("expr")
    private Map<String, Object> expr;

    @JsonProperty("parent")
    private Object parent; // Can be String or ParentReference

    @JsonProperty("display_in_showcase")
    private Integer displayInShowcase;

    @JsonProperty("article")
    private Object article; // Can be String or List<String>

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("includedParams")
    private List<String> includedParams;

    @JsonProperty("excludedParams")
    private List<String> excludedParams;
}
