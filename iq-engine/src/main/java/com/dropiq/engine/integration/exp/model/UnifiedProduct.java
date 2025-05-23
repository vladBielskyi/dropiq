package com.dropiq.engine.integration.exp.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

@Data
public class UnifiedProduct {
    private String id;                          // Internal ID
    private String externalId;                  // ID from source platform
    private String groupId;                     // For product variants
    private String name;                        // Product name
    private String description;                 // Product description
    private Double price;                       // Product price
    private Integer stock;                      // Quantity in stock
    private boolean available;                  // Product availability
    private String externalCategoryId;                  // Category ID
    private String externalCategoryName;                // Category name
    private List<String> imageUrls;             // Product images
    private Map<String, String> attributes;     // Product attributes (size, color, etc.)
    private SourceType sourceType;                  // EASYDROP, MYDROP, etc.     // Human-readable source name
    private String sourceUrl;                   // URL where the product was fetched from
    private LocalDateTime lastUpdated;          // Last update timestamp
    private Map<String, String> platformSpecificData; // Additional platform-specific data

    public UnifiedProduct() {
        this.imageUrls = new ArrayList<>();
        this.attributes = new HashMap<>();
        this.platformSpecificData = new HashMap<>();
    }
}
