package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class HoroshopSyncStatus {
    private String productArticle;
    private String status; // "SUCCESS", "ERROR", "SKIPPED", "PENDING"
    private String message;
    private LocalDateTime timestamp;
    private List<String> warnings = new ArrayList<>();
}
