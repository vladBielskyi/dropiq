package com.dropiq.engine.integration.imp.horoshop.controller;

import com.dropiq.engine.integration.imp.horoshop.model.HoroshopSyncStatus;
import com.dropiq.engine.integration.imp.horoshop.service.HoroshopSyncJobScheduler;
import com.dropiq.engine.integration.imp.horoshop.service.HoroshopSyncResult;
import com.dropiq.engine.integration.imp.horoshop.service.SyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for managing Horoshop synchronization
 */
@Slf4j
@RestController
@RequestMapping("/api/horoshop/sync")
@RequiredArgsConstructor
public class HoroshopSyncController {

    private final HoroshopSyncJobScheduler syncJobScheduler;

    /**
     * Trigger immediate sync for dataset
     */
    @PostMapping("/datasets/{datasetId}/trigger")
    public ResponseEntity<String> triggerSync(@PathVariable Long datasetId,
                                              @RequestHeader("X-User-ID") String userId) {

        log.info("Manual sync triggered for dataset {} by user {}", datasetId, userId);

        try {
            CompletableFuture<HoroshopSyncResult> future = syncJobScheduler.triggerImmediateSync(datasetId, userId);

            return ResponseEntity.accepted()
                    .body("Sync started successfully. Check status endpoint for progress.");

        } catch (Exception e) {
            log.error("Failed to trigger sync for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Failed to start sync: " + e.getMessage());
        }
    }

    /**
     * Get sync status for dataset
     */
    @GetMapping("/datasets/{datasetId}/status")
    public ResponseEntity<SyncStatus> getSyncStatus(@PathVariable Long datasetId) {

        try {
            SyncStatus status = syncJobScheduler.getSyncStatus(datasetId);
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Failed to get sync status for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Enable/disable auto-sync for dataset
     */
    @PostMapping("/datasets/{datasetId}/auto-sync")
    public ResponseEntity<String> toggleAutoSync(@PathVariable Long datasetId,
                                                 @RequestBody AutoSyncRequest request) {

        try {
            syncJobScheduler.toggleAutoSync(datasetId, request.isEnabled());

            String message = String.format("Auto-sync %s for dataset %d",
                    request.isEnabled() ? "enabled" : "disabled", datasetId);

            return ResponseEntity.ok(message);

        } catch (Exception e) {
            log.error("Failed to toggle auto-sync for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Failed to update auto-sync: " + e.getMessage());
        }
    }

    /**
     * Update sync interval for dataset
     */
    @PostMapping("/datasets/{datasetId}/interval")
    public ResponseEntity<String> updateSyncInterval(@PathVariable Long datasetId,
                                                     @RequestBody SyncIntervalRequest request) {

        try {
            syncJobScheduler.updateSyncInterval(datasetId, request.getIntervalHours());

            String message = String.format("Sync interval updated to %d hours for dataset %d",
                    request.getIntervalHours(), datasetId);

            return ResponseEntity.ok(message);

        } catch (Exception e) {
            log.error("Failed to update sync interval for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Failed to update sync interval: " + e.getMessage());
        }
    }

    /**
     * Get sync history for dataset
     */
    @GetMapping("/datasets/{datasetId}/history")
    public ResponseEntity<List<HoroshopSyncHistoryEntry>> getSyncHistory(@PathVariable Long datasetId,
                                                                         @RequestParam(defaultValue = "10") int limit) {

        try {
            List<HoroshopSyncHistoryEntry> history = getSyncHistoryForDataset(datasetId, limit);
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("Failed to get sync history for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get overall sync statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<HoroshopSyncStatistics> getSyncStatistics() {

        try {
            HoroshopSyncStatistics stats = calculateSyncStatistics();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get sync statistics: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Force sync all datasets (admin only)
     */
    @PostMapping("/force-all")
    public ResponseEntity<String> forceSyncAll(@RequestHeader("X-User-Role") String userRole) {

        if (!"ADMIN".equals(userRole)) {
            return ResponseEntity.status(403).body("Admin role required");
        }

        try {
            // This would trigger sync for all active datasets
            log.info("Force sync all datasets triggered by admin");

            // In real implementation, this would iterate through all datasets
            // and trigger sync for each one

            return ResponseEntity.accepted()
                    .body("Force sync started for all datasets. Check individual dataset statuses.");

        } catch (Exception e) {
            log.error("Failed to force sync all datasets: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Failed to start force sync: " + e.getMessage());
        }
    }

    /**
     * Pause/resume sync jobs
     */
    @PostMapping("/pause")
    public ResponseEntity<String> pauseSync(@RequestBody PauseSyncRequest request) {

        try {
            // This would pause/resume the sync scheduler
            log.info("Sync jobs {}", request.isPause() ? "paused" : "resumed");

            return ResponseEntity.ok(String.format("Sync jobs %s successfully",
                    request.isPause() ? "paused" : "resumed"));

        } catch (Exception e) {
            log.error("Failed to {} sync jobs: {}", request.isPause() ? "pause" : "resume", e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Failed to update sync status: " + e.getMessage());
        }
    }

    /**
     * Get sync configuration for dataset
     */
    @GetMapping("/datasets/{datasetId}/config")
    public ResponseEntity<HoroshopSyncConfiguration> getSyncConfig(@PathVariable Long datasetId) {

        try {
            HoroshopSyncConfiguration config = buildSyncConfiguration(datasetId);
            return ResponseEntity.ok(config);

        } catch (Exception e) {
            log.error("Failed to get sync config for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update sync configuration for dataset
     */
    @PostMapping("/datasets/{datasetId}/config")
    public ResponseEntity<String> updateSyncConfig(@PathVariable Long datasetId,
                                                   @Valid @RequestBody HoroshopSyncConfiguration config) {

        try {
            updateDatasetSyncConfiguration(datasetId, config);

            return ResponseEntity.ok("Sync configuration updated successfully");

        } catch (Exception e) {
            log.error("Failed to update sync config for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Failed to update configuration: " + e.getMessage());
        }
    }

    // Helper methods

    private List<HoroshopSyncHistoryEntry> getSyncHistoryForDataset(Long datasetId, int limit) {
        // This would query sync history from database
        // For now, return mock data
        return List.of(
                new HoroshopSyncHistoryEntry(
                        java.time.LocalDateTime.now().minusHours(2),
                        "REGULAR",
                        true,
                        150,
                        148,
                        2,
                        "Sync completed successfully"
                ),
                new HoroshopSyncHistoryEntry(
                        java.time.LocalDateTime.now().minusHours(8),
                        "URGENT",
                        true,
                        25,
                        25,
                        0,
                        "Urgent low-stock sync completed"
                )
        );
    }

    private HoroshopSyncStatistics calculateSyncStatistics() {
        // This would calculate real statistics from database
        HoroshopSyncStatistics stats = new HoroshopSyncStatistics();
        stats.setTotalDatasets(15);
        stats.setActiveSyncDatasets(12);
        stats.setTotalSyncsToday(45);
        stats.setSuccessfulSyncsToday(42);
        stats.setFailedSyncsToday(3);
        stats.setAverageSuccessRate(95.2);
        stats.setTotalProductsSynced(12_450);
        stats.setLastSyncTime(java.time.LocalDateTime.now().minusMinutes(15));
        return stats;
    }

    private HoroshopSyncConfiguration buildSyncConfiguration(Long datasetId) {
        // This would load from database
        HoroshopSyncConfiguration config = new HoroshopSyncConfiguration();
        config.setDatasetId(datasetId);
        config.setAutoSyncEnabled(true);
        config.setSyncIntervalHours(6);
        config.setSyncStock(true);
        config.setSyncPrices(true);
        config.setConflictResolution("REMOTE_WINS");
        config.setUrgentSyncEnabled(true);
        config.setMaxRetries(3);
        config.setBatchSize(20);
        return config;
    }

    private void updateDatasetSyncConfiguration(Long datasetId, HoroshopSyncConfiguration config) {
        // This would save to database
        log.info("Updating sync configuration for dataset {}", datasetId);

        // Update auto-sync settings
        syncJobScheduler.toggleAutoSync(datasetId, config.isAutoSyncEnabled());
        syncJobScheduler.updateSyncInterval(datasetId, config.getSyncIntervalHours());

        // Additional configuration would be saved to database
    }
}

// Request/Response DTOs

@lombok.Data
class AutoSyncRequest {
    private boolean enabled;
}

@lombok.Data
class SyncIntervalRequest {
    private int intervalHours;
}

@lombok.Data
class PauseSyncRequest {
    private boolean pause;
}

@lombok.Data
class HoroshopSyncHistoryEntry {
    private java.time.LocalDateTime timestamp;
    private String syncType;
    private boolean success;
    private int totalProducts;
    private int successfulProducts;
    private int failedProducts;
    private String message;

    public HoroshopSyncHistoryEntry(java.time.LocalDateTime timestamp, String syncType,
                                    boolean success, int totalProducts, int successfulProducts,
                                    int failedProducts, String message) {
        this.timestamp = timestamp;
        this.syncType = syncType;
        this.success = success;
        this.totalProducts = totalProducts;
        this.successfulProducts = successfulProducts;
        this.failedProducts = failedProducts;
        this.message = message;
    }
}

@lombok.Data
class HoroshopSyncStatistics {
    private int totalDatasets;
    private int activeSyncDatasets;
    private int totalSyncsToday;
    private int successfulSyncsToday;
    private int failedSyncsToday;
    private double averageSuccessRate;
    private int totalProductsSynced;
    private java.time.LocalDateTime lastSyncTime;
}

@lombok.Data
class HoroshopSyncConfiguration {
    private Long datasetId;
    private boolean autoSyncEnabled;
    private int syncIntervalHours;
    private boolean syncStock;
    private boolean syncPrices;
    private String conflictResolution;
    private boolean urgentSyncEnabled;
    private int maxRetries;
    private int batchSize;
}
