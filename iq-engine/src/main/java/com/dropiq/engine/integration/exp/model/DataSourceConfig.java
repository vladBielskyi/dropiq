package com.dropiq.engine.integration.exp.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class DataSourceConfig {
    private String platformType;            // EASYDROP, MYDROP, etc.
    private String url;                     // API URL
    private Map<String, String> headers;    // API headers

    public DataSourceConfig() {
        this.headers = new HashMap<>();
    }
}