package com.dropiq.engine.integration.imp.horoshop.service;

import com.dropiq.engine.integration.imp.horoshop.model.HoroshopBulkResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@lombok.Data
public class HoroshopSyncResult {
    private Long datasetId;
    private String userId;
    private String syncType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean success;
    private String message;

    private HoroshopBulkResult exportResult;
    private HoroshopBulkResult importResult;
    private List<HoroshopSyncConflict> conflicts = new ArrayList<>();

    // Statistics
    private int totalProducts;
    private int syncedProducts;
    private int conflictedProducts;
    private int errorProducts;

    public void calculateStatistics() {
        totalProducts = 0;
        syncedProducts = 0;
        conflictedProducts = 0;
        errorProducts = 0;

        if (exportResult != null) {
            totalProducts += exportResult.getTotalProcessed();
            syncedProducts += exportResult.getTotalSuccess();
            errorProducts += exportResult.getTotalErrors();
        }

        if (importResult != null) {
            totalProducts += importResult.getTotalProcessed();
            syncedProducts += importResult.getTotalSuccess();
            errorProducts += importResult.getTotalErrors();
        }

        conflictedProducts = conflicts.size();
    }

    public double getSuccessRate() {
        return totalProducts > 0 ? (double) syncedProducts / totalProducts * 100 : 0;
    }
}
