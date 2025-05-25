package com.dropiq.engine.integration.imp.horoshop.service;

import com.dropiq.engine.integration.imp.horoshop.model.HoroshopConfig;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.model.DataSetStatus;
import com.dropiq.engine.product.repository.DataSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job for automatic Horoshop synchronization
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoroshopSyncJobScheduler {

    private final HoroshopRobustSyncService syncService;
    private final DataSetRepository dataSetRepository;
    private final HoroshopSyncConfigManager configManager;

    @Value("${horoshop.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${horoshop.sync.max-concurrent-jobs:3}")
    private int maxConcurrentJobs;

    @Value("${horoshop.sync.job-timeout-minutes:30}")
    private int jobTimeoutMinutes;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    /**
     * Regular sync job - runs every 30 minutes
     * Focuses on stock and price updates
     */
    @Scheduled(fixedDelay = 1800000) // 30 minutes
    public void performRegularSync() {
        if (!syncEnabled) {
            log.debug("Horoshop sync is disabled, skipping regular sync");
            return;
        }

        log.info("Starting regular Horoshop sync job");

        try {
            List<DataSet> datasetsToSync = getDatasetsPendingSync();
            log.info("Found {} datasets requiring sync", datasetsToSync.size());

            if (datasetsToSync.isEmpty()) {
                log.info("No datasets require synchronization");
                return;
            }

            // Process datasets with controlled concurrency
            processDatasetsWithConcurrencyControl(datasetsToSync, "REGULAR");

        } catch (Exception e) {
            log.error("Regular sync job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Urgent sync job - runs every 5 minutes
     * Handles low stock items and critical updates
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void performUrgentSync() {
        if (!syncEnabled) {
            return;
        }

        log.debug("Starting urgent Horoshop sync job");

        try {
            List<DataSet> urgentDatasets = getDatasetsRequiringUrgentSync();

            if (!urgentDatasets.isEmpty()) {
                log.info("Found {} datasets requiring urgent sync", urgentDatasets.size());
                processDatasetsWithConcurrencyControl(urgentDatasets, "URGENT");
            }

        } catch (Exception e) {
            log.error("Urgent sync job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Daily comprehensive sync - runs at 2 AM
     * Full synchronization including conflict resolution
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void performDailyComprehensiveSync() {
        if (!syncEnabled) {
            return;
        }

        log.info("Starting daily comprehensive Horoshop sync");

        try {
            List<DataSet> allActiveDatasets = dataSetRepository.findByStatus(DataSetStatus.ACTIVE);
            log.info("Performing comprehensive sync for {} datasets", allActiveDatasets.size());

            // Use comprehensive sync options
            HoroshopSyncOptions comprehensiveOptions = createComprehensiveOptions();

            for (DataSet dataset : allActiveDatasets) {
                try {
                    HoroshopConfig config = configManager.getConfigForDataset(dataset.getId());
                    if (config != null) {
                        log.info("Starting comprehensive sync for dataset: {}", dataset.getName());

                        CompletableFuture<HoroshopSyncResult> future = syncService.performRobustSync(
                                dataset.getId(), dataset.getCreatedBy(), config, comprehensiveOptions);

                        // Wait for completion with timeout
                        HoroshopSyncResult result = future.get(jobTimeoutMinutes, TimeUnit.MINUTES);

                        logSyncResult(dataset, result, "COMPREHENSIVE");

                        // Small delay between datasets
                        Thread.sleep(5000);

                    } else {
                        log.warn("No Horoshop config found for dataset: {}", dataset.getName());
                    }
                } catch (Exception e) {
                    log.error("Comprehensive sync failed for dataset {}: {}", dataset.getName(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Daily comprehensive sync job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Process datasets with controlled concurrency
     */
    private void processDatasetsWithConcurrencyControl(List<DataSet> datasets, String syncType) {
        int batchSize = Math.min(maxConcurrentJobs, datasets.size());

        for (int i = 0; i < datasets.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, datasets.size());
            List<DataSet> batch = datasets.subList(i, endIndex);

            log.info("Processing {} sync batch {}-{} of {}", syncType, i + 1, endIndex, datasets.size());

            // Process batch concurrently
            List<CompletableFuture<Void>> futures = batch.stream()
                    .map(dataset -> processDatasetAsync(dataset, syncType))
                    .toList();

            // Wait for batch completion
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(jobTimeoutMinutes, TimeUnit.MINUTES)
                    .join();

            // Small delay between batches
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Process single dataset asynchronously
     */
    private CompletableFuture<Void> processDatasetAsync(DataSet dataset, String syncType) {
        return CompletableFuture.runAsync(() -> {
            try {
                HoroshopConfig config = configManager.getConfigForDataset(dataset.getId());
                if (config == null) {
                    log.warn("No Horoshop config found for dataset: {}", dataset.getName());
                    return;
                }

                HoroshopSyncOptions options = createSyncOptions(syncType);

                log.info("Starting {} sync for dataset: {}", syncType, dataset.getName());

                CompletableFuture<HoroshopSyncResult> syncFuture = syncService.performRobustSync(
                        dataset.getId(), dataset.getCreatedBy(), config, options);

                HoroshopSyncResult result = syncFuture.get(jobTimeoutMinutes, TimeUnit.MINUTES);

                logSyncResult(dataset, result, syncType);

            } catch (Exception e) {
                log.error("{} sync failed for dataset {}: {}", syncType, dataset.getName(), e.getMessage());
            }
        }, executorService);
    }

    /**
     * Get datasets that need regular synchronization
     */
    private List<DataSet> getDatasetsPendingSync() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(6); // 6 hours ago

        return dataSetRepository.findAll().stream()
                .filter(dataset -> dataset.getStatus() == DataSetStatus.ACTIVE)
                .filter(dataset -> dataset.getAutoSync() != null && dataset.getAutoSync())
                .filter(dataset -> dataset.getLastSync() == null || dataset.getLastSync().isBefore(cutoff))
                .filter(dataset -> configManager.hasValidConfig(dataset.getId()))
                .toList();
    }

    /**
     * Get datasets requiring urgent synchronization
     */
    private List<DataSet> getDatasetsRequiringUrgentSync() {
        LocalDateTime urgentCutoff = LocalDateTime.now().minusMinutes(30); // 30 minutes ago

        return dataSetRepository.findAll().stream()
                .filter(dataset -> dataset.getStatus() == DataSetStatus.ACTIVE)
                .filter(dataset -> configManager.hasValidConfig(dataset.getId()))
                .filter(dataset -> hasUrgentSyncNeeded(dataset, urgentCutoff))
                .toList();
    }

    /**
     * Check if dataset needs urgent sync (low stock products)
     */
    private boolean hasUrgentSyncNeeded(DataSet dataset, LocalDateTime cutoff) {
        // Check if dataset has products with low stock that haven't been synced recently
        return dataset.getProducts().stream()
                .anyMatch(product ->
                        (product.getStock() != null && product.getStock() <= 5) && // Low stock
                                (product.getLastSync() == null || product.getLastSync().isBefore(cutoff)) // Not recently synced
                );
    }

    /**
     * Create sync options based on sync type
     */
    private HoroshopSyncOptions createSyncOptions(String syncType) {
        HoroshopSyncOptions options = new HoroshopSyncOptions();

        switch (syncType) {
            case "REGULAR":
                options.setSyncStock(true);
                options.setSyncPrices(true);
                options.setExportToHoroshop(true);
                options.setImportFromHoroshop(true);
                options.setConflictResolution(ConflictResolution.REMOTE_WINS);
                options.setSyncIntervalHours(6);
                break;

            case "URGENT":
                options.setSyncStock(true);
                options.setSyncPrices(true);
                options.setExportToHoroshop(true);
                options.setImportFromHoroshop(false); // Only export for urgent updates
                options.setConflictResolution(ConflictResolution.LOCAL_WINS);
                options.setUrgentSync(true);
                options.setSyncIntervalHours(1);
                break;

            case "COMPREHENSIVE":
                options.setSyncStock(true);
                options.setSyncPrices(true);
                options.setExportToHoroshop(true);
                options.setImportFromHoroshop(true);
                options.setConflictResolution(ConflictResolution.DETECT_ONLY); // Detect conflicts for manual resolution
                options.setSyncIntervalHours(24);
                break;
        }

        return options;
    }

    /**
     * Create comprehensive sync options
     */
    private HoroshopSyncOptions createComprehensiveOptions() {
        return createSyncOptions("COMPREHENSIVE");
    }

    /**
     * Log sync result with appropriate level
     */
    private void logSyncResult(DataSet dataset, HoroshopSyncResult result, String syncType) {
        if (result.isSuccess()) {
            log.info("{} sync completed for dataset '{}': {} products synced, {:.1f}% success rate",
                    syncType, dataset.getName(), result.getSyncedProducts(), result.getSuccessRate());

            if (result.getConflictedProducts() > 0) {
                log.warn("Dataset '{}' has {} conflicts requiring attention",
                        dataset.getName(), result.getConflictedProducts());
            }
        } else {
            log.error("{} sync failed for dataset '{}': {}",
                    syncType, dataset.getName(), result.getMessage());
        }
    }

    /**
     * Manual trigger for immediate sync
     */
    public CompletableFuture<HoroshopSyncResult> triggerImmediateSync(Long datasetId, String userId) {
        log.info("Manual sync triggered for dataset {} by user {}", datasetId, userId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                DataSet dataset = dataSetRepository.findById(datasetId)
                        .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

                HoroshopConfig config = configManager.getConfigForDataset(datasetId);
                if (config == null) {
                    throw new RuntimeException("No Horoshop configuration found for dataset");
                }

                HoroshopSyncOptions options = createSyncOptions("REGULAR");
                options.setUrgentSync(true); // Mark as urgent for faster processing

                return syncService.performRobustSync(datasetId, userId, config, options).get();

            } catch (Exception e) {
                log.error("Manual sync failed for dataset {}: {}", datasetId, e.getMessage());
                throw new RuntimeException("Manual sync failed: " + e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Get sync status for dataset
     */
    public SyncStatus getSyncStatus(Long datasetId) {
        try {
            DataSet dataset = dataSetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

            SyncStatus status = new SyncStatus();
            status.setDatasetId(datasetId);
            status.setDatasetName(dataset.getName());
            status.setLastSync(dataset.getLastSync());
            status.setAutoSyncEnabled(dataset.getAutoSync() != null && dataset.getAutoSync());
            status.setNextScheduledSync(calculateNextSync(dataset));
            status.setHasValidConfig(configManager.hasValidConfig(datasetId));

            // Check if urgent sync is needed
            status.setUrgentSyncNeeded(hasUrgentSyncNeeded(dataset, LocalDateTime.now().minusMinutes(30)));

            // Count products needing sync
            LocalDateTime syncCutoff = LocalDateTime.now().minusHours(6);
            long productsNeedingSync = dataset.getProducts().stream()
                    .filter(product -> product.getLastSync() == null || product.getLastSync().isBefore(syncCutoff))
                    .count();
            status.setProductsNeedingSync((int) productsNeedingSync);

            return status;

        } catch (Exception e) {
            log.error("Failed to get sync status for dataset {}: {}", datasetId, e.getMessage());
            throw new RuntimeException("Failed to get sync status: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate next scheduled sync time
     */
    private LocalDateTime calculateNextSync(DataSet dataset) {
        if (dataset.getAutoSync() == null || !dataset.getAutoSync()) {
            return null;
        }

        LocalDateTime lastSync = dataset.getLastSync() != null ? dataset.getLastSync() : dataset.getCreatedAt();
        int intervalHours = dataset.getSyncIntervalHours() != null ? dataset.getSyncIntervalHours() : 6;

        return lastSync.plusHours(intervalHours);
    }

    /**
     * Enable/disable sync for dataset
     */
    public void toggleAutoSync(Long datasetId, boolean enabled) {
        DataSet dataset = dataSetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        dataset.setAutoSync(enabled);
        dataSetRepository.save(dataset);

        log.info("Auto-sync {} for dataset: {}", enabled ? "enabled" : "disabled", dataset.getName());
    }

    /**
     * Update sync interval for dataset
     */
    public void updateSyncInterval(Long datasetId, int intervalHours) {
        if (intervalHours < 1 || intervalHours > 168) { // 1 hour to 1 week
            throw new IllegalArgumentException("Sync interval must be between 1 and 168 hours");
        }

        DataSet dataset = dataSetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        dataset.setSyncIntervalHours(intervalHours);
        dataSetRepository.save(dataset);

        log.info("Sync interval updated to {} hours for dataset: {}", intervalHours, dataset.getName());
    }
}

/**
 * Configuration manager for Horoshop sync
 */
@Service
@RequiredArgsConstructor
@Slf4j
class HoroshopSyncConfigManager {

    private final Map<Long, HoroshopConfig> configCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Get configuration for dataset
     */
    public HoroshopConfig getConfigForDataset(Long datasetId) {
        // Check cache first
        HoroshopConfig cached = configCache.get(datasetId);
        if (cached != null) {
            return cached;
        }

        // In real implementation, this would load from database
        // For now, return a sample config
        HoroshopConfig config = createSampleConfig(datasetId);
        configCache.put(datasetId, config);

        return config;
    }

    /**
     * Check if dataset has valid configuration
     */
    public boolean hasValidConfig(Long datasetId) {
        try {
            HoroshopConfig config = getConfigForDataset(datasetId);
            return config != null &&
                    config.getApiUrl() != null &&
                    (config.getToken() != null || (config.getUsername() != null && config.getPassword() != null));
        } catch (Exception e) {
            log.warn("Failed to validate config for dataset {}: {}", datasetId, e.getMessage());
            return false;
        }
    }

    /**
     * Update configuration for dataset
     */
    public void updateConfig(Long datasetId, HoroshopConfig config) {
        configCache.put(datasetId, config);
        // In real implementation, this would save to database
        log.info("Configuration updated for dataset: {}", datasetId);
    }

    /**
     * Remove configuration from cache
     */
    public void clearConfig(Long datasetId) {
        configCache.remove(datasetId);
    }

    private HoroshopConfig createSampleConfig(Long datasetId) {
        // This would be loaded from database in real implementation
        HoroshopConfig config = new HoroshopConfig();
        config.setDomain("demo.horoshop.ua");
        config.setApiUrl("https://demo.horoshop.ua/api/");
        config.setToken("sample-token-" + datasetId);
        config.setBatchSize(20);
        config.setTimeout(60);
        config.setRetryAttempts(3);
        return config;
    }
}