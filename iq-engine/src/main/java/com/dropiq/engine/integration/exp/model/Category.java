package com.dropiq.engine.integration.exp.model;

import lombok.Data;

@Data
public class Category {
    private String id;
    private String parentId;
    private String name;
    private SourceType sourceType;
}
