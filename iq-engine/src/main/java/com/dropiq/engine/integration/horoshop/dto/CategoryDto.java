package com.dropiq.engine.integration.horoshop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryDto {
    @JsonProperty("id")
    private Integer id;

    @JsonProperty("parent")
    private Integer parent;

    @JsonProperty("title")
    private MultilingualText title;

    @JsonProperty("discount")
    private BigDecimal discount;
}
