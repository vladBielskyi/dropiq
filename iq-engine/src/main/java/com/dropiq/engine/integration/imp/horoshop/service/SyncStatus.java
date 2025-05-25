package com.dropiq.engine.integration.imp.horoshop.service;

import java.time.LocalDateTime; /**
 * Sync status information
 */
@lombok.Data
public class SyncStatus {
    private Long datasetId;
    private String datasetName;
    private LocalDateTime lastSync;
    private LocalDateTime nextScheduledSync;
    private boolean autoSyncEnabled;
    private boolean hasValidConfig;
    private boolean urgentSyncNeeded;
    private int productsNeedingSync;
    private LocalDateTime statusTime = LocalDateTime.now();
}
