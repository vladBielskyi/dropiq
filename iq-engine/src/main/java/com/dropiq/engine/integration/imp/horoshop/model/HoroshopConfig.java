package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

@Data
public class HoroshopConfig {
    private String domain; // shop domain (e.g., "myshop.horoshop.ua")
    private String apiUrl; // Full API URL (e.g., "https://myshop.horoshop.ua/api/")
    private String token; // API token
    private String username; // API username (optional)
    private String password; // API password (optional)
    private Integer timeout = 120; // seconds
    private Integer retryAttempts = 3;
    private Integer batchSize = 50; // products per batch
}
