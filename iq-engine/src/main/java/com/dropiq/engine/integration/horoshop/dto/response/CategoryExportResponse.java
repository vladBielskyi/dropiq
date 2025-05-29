package com.dropiq.engine.integration.horoshop.dto.response;

import com.dropiq.engine.integration.horoshop.dto.CategoryDto;
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
public class CategoryExportResponse {
    @JsonProperty("status")
    private String status;

    @JsonProperty("response")
    private CategoryExportResponseData response;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CategoryExportResponseData {
        @JsonProperty("pages")
        private List<CategoryDto> pages;
    }
}
