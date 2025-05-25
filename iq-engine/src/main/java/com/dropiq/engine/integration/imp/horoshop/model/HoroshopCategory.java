package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class HoroshopCategory {
    private Long id;
    private String name;
    private String slug;
    private String path; // "Parent Category / Child Category"
    private Long parentId;
    private List<HoroshopCategory> children = new ArrayList<>();
    private Map<String, String> title = new HashMap<>(); // Multi-language titles
    private Map<String, String> description = new HashMap<>();
    private Boolean active = true;
    private Integer sortOrder = 0;
}