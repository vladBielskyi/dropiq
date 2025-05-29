package com.dropiq.engine.integration.horoshop.dto.request;

import com.dropiq.engine.integration.horoshop.dto.ProductImportDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductImportRequest {
    @JsonProperty("token")
    private String token;

    @JsonProperty("products")
    private List<ProductImportDto> products;
}
