package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class HoroshopBulkResult {
    private Integer totalProcessed = 0;
    private Integer totalSuccess = 0;
    private Integer totalErrors = 0;
    private Integer totalSkipped = 0;
    private List<HoroshopSyncStatus> results = new ArrayList<>();
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public void addResult(HoroshopSyncStatus status) {
        results.add(status);
        totalProcessed++;

        switch (status.getStatus()) {
            case "SUCCESS" -> totalSuccess++;
            case "ERROR" -> totalErrors++;
            case "SKIPPED" -> totalSkipped++;
        }
    }

    public double getSuccessRate() {
        return totalProcessed > 0 ? (double) totalSuccess / totalProcessed * 100 : 0;
    }
}
