package com.dropiq.engine.product.dto;

import lombok.Data;

@Data
public class MergeDataSetRequest {
    private Long dataset1Id;
    private Long dataset2Id;
    private String newName;
}
