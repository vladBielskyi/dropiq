package com.dropiq.engine.integration.exp.model;

import lombok.Data;

import java.util.Map;

@Data
public class DataSourceConfig {
    private SourceType platformType;            // EASYDROP, MYDROP, etc.
    private String url;                     // API URL
    private Map<String, String> headers;
    private Boolean exportUnavailable;// API headers
}