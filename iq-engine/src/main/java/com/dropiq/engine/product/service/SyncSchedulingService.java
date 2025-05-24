package com.dropiq.engine.product.service;

import com.dropiq.engine.product.entity.DataSet;

import com.dropiq.engine.product.entity.SyncHistory;
import com.dropiq.engine.product.entity.SyncJob;
import com.dropiq.engine.product.model.DataSetStatus;


import com.dropiq.engine.product.model.SyncJobStatus;
import com.dropiq.engine.product.model.SyncJobType;
import com.dropiq.engine.product.repository.SyncHistoryRepository;
import com.dropiq.engine.product.repository.SyncJobRepository;
import com.dropiq.engine.user.entity.User;
import com.dropiq.engine.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncSchedulingService {

    private final SyncJobRepository syncJobRepository;
    private final SyncHistoryRepository syncHistoryRepository;
    private final DataSetService dataSetService;
    private final UserService userService;
    private final ExecutorService executorService;

    @Value("${sync.max-concurrent-jobs:5}")
    private int maxConcurrentJobs;

    @Value("${sync.job-timeout-minutes:30}")
    private int jobTimeoutMinutes;

    private final Map<Long, Future<?>> runningJobs = new ConcurrentHashMap<>();

    /**
     * Schedule a new sync job
     */
    @Transactional
    public SyncJob scheduleSync(Long userId, String entityType, Long entityId, SyncJobType jobType,
                                LocalDateTime scheduledAt, Integer priority) {
        // Check user quota
        if (userService.checkQuotaLimit(userId, "SYNCS")) {
            throw new IllegalStateException("User has reached sync quota limit");
        }

        // Check for existing pending jobs
        Optional<SyncJob> existingJob = syncJobRepository.findPendingJobForEntity(entityType, entityId);
        if (existingJob.isPresent()) {
            log.info("Updating existing pending job for {}: {}", entityType, entityId);
            SyncJob job = existingJob.get();
            job.setScheduledAt(scheduledAt);
            job.setPriority(priority);
            return syncJobRepository.save(job);
        }

        // Create new sync job
        SyncJob job = new SyncJob();
        job.setJobType(jobType);
        job.setEntityType(entityType);
        job.setEntityId(entityId);
        job.setUserId(userId);
        job.setStatus(SyncJobStatus.PENDING);
        job.setPriority(priority != null ? priority : 5);
        job.setScheduledAt(scheduledAt != null ? scheduledAt : LocalDateTime.now());
        job.setCreatedAt(LocalDateTime.now());

        job = syncJobRepository.save(job);
        log.info("Scheduled new sync job {} for {}: {}", job.getId(), entityType, entityId);

        // Update user quota
        userService.updateQuotaUsage(userId, "SYNCS", 1);

        return job;
    }

    /**
     * Schedule recurring sync for auto-sync enabled entities
     */
    @Scheduled(fixedDelay = 3600000) // Run every hour
    @Transactional
    public void scheduleAutoSyncs() {
        log.info("Checking for auto-sync entities...");

        // Find all datasets with auto-sync enabled
        List<DataSet> autoSyncDatasets = dataSetService.findAutoSyncDatasets();

        for (DataSet dataset : autoSyncDatasets) {
            if (shouldSync(dataset)) {
                try {
                    Long userId = getUserIdFromCreatedBy(dataset.getCreatedBy());
                    scheduleSync(userId, "DATASET", dataset.getId(),
                            SyncJobType.DATASET_SYNC, LocalDateTime.now(), 3);
                } catch (Exception e) {
                    log.error("Failed to schedule auto-sync for dataset {}: {}",
                            dataset.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Process pending sync jobs
     */
    @Scheduled(fixedDelay = 30000) // Run every 30 seconds
    public void processPendingJobs() {
        if (runningJobs.size() >= maxConcurrentJobs) {
            log.debug("Max concurrent jobs reached, skipping processing");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<SyncJob> pendingJobs = syncJobRepository.findJobsToProcess(
                SyncJobStatus.PENDING, now, maxConcurrentJobs - runningJobs.size()
        );

        for (SyncJob job : pendingJobs) {
            processJob(job);
        }
    }

    /**
     * Process a single sync job
     */
    private void processJob(SyncJob job) {
        log.info("Processing sync job {}", job.getId());

        // Mark job as running
        job.setStatus(SyncJobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        syncJobRepository.save(job);

        // Create sync history entry
        SyncHistory history = createSyncHistory(job);

        // Submit job to executor
        Future<?> future = executorService.submit(() -> {
            try {
                executeJob(job, history);
            } catch (Exception e) {
                log.error("Error executing job {}: {}", job.getId(), e.getMessage(), e);
                handleJobFailure(job, history, e);
            }
        });

        runningJobs.put(job.getId(), future);

        // Schedule timeout check
        scheduleTimeoutCheck(job.getId(), jobTimeoutMinutes);
    }

    /**
     * Execute the actual sync job
     */
    @Transactional
    @CacheEvict(value = {"datasets", "products"}, allEntries = true)
    public void executeJob(SyncJob job, SyncHistory history) {
        log.info("Executing job {} of type {}", job.getId(), job.getJobType());

        switch (job.getJobType()) {
            case DATASET_SYNC:
                executeDatasetSync(job, history);
                break;
            case PRODUCT_UPDATE:
                executeProductUpdate(job, history);
                break;
            case AI_OPTIMIZATION:
                executeAIOptimization(job, history);
                break;
            default:
                throw new IllegalArgumentException("Unknown job type: " + job.getJobType());
        }

        // Mark job as completed
        job.setStatus(SyncJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        syncJobRepository.save(job);

        // Update history
        history.setStatus(SyncJobStatus.COMPLETED);
        history.setCompletedAt(LocalDateTime.now());
        history.setDurationSeconds(
                Duration.between(history.getStartedAt(), history.getCompletedAt()).getSeconds()
        );
        syncHistoryRepository.save(history);

        // Remove from running jobs
        runningJobs.remove(job.getId());

        log.info("Job {} completed successfully", job.getId());
    }

    /**
     * Execute dataset synchronization
     */
    private void executeDatasetSync(SyncJob job, SyncHistory history) {
        Optional<DataSet> datasetOpt = dataSetService.getDataset(job.getEntityId(),
                job.getUserId().toString());

        if (datasetOpt.isEmpty()) {
            throw new IllegalArgumentException("Dataset not found: " + job.getEntityId());
        }

        DataSet dataset = datasetOpt.get();
        int productsBefore = dataset.getTotalProducts();

        // Perform sync
        dataSetService.syncDatasetFromSource(dataset);

        // Update history metrics
        history.setProductsAdded(dataset.getTotalProducts() - productsBefore);
        history.setProductsUpdated(dataset.getActiveProducts());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourcePlatforms", dataset.getSourcePlatforms());
        metadata.put("syncType", "FULL");
        history.setMetadata(metadata);
    }

    /**
     * Execute product update
     */
    private void executeProductUpdate(SyncJob job, SyncHistory history) {
        // Implementation for product updates
        log.info("Executing product update for job {}", job.getId());

        Map<String, Object> metadata = job.getMetadata();
        if (metadata != null && metadata.containsKey("productIds")) {
            List<Long> productIds = (List<Long>) metadata.get("productIds");
            int updatedCount = 0;

            for (Long productId : productIds) {
                try {
                    // Update product prices, stock, etc.
                    dataSetService.updateProductFromSource(productId);
                    updatedCount++;
                } catch (Exception e) {
                    log.error("Failed to update product {}: {}", productId, e.getMessage());
                    history.setErrorsEncountered(history.getErrorsEncountered() + 1);
                }
            }

            history.setProductsUpdated(updatedCount);
        }
    }

    /**
     * Execute AI optimization
     */
    private void executeAIOptimization(SyncJob job, SyncHistory history) {
        log.info("Executing AI optimization for job {}", job.getId());

        // This will be implemented with the AI service
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("optimizationType", job.getMetadata().get("optimizationType"));
        metadata.put("status", "MOCK_COMPLETED");
        history.setMetadata(metadata);
    }

    /**
     * Handle job failure
     */
    private void handleJobFailure(SyncJob job, SyncHistory history, Exception e) {
        log.error("Job {} failed: {}", job.getId(), e.getMessage());

        job.setRetryCount(job.getRetryCount() + 1);

        if (job.getRetryCount() < job.getMaxRetries()) {
            // Schedule retry
            job.setStatus(SyncJobStatus.PENDING);
            job.setScheduledAt(LocalDateTime.now().plusMinutes(5 * job.getRetryCount()));
            job.setErrorMessage(e.getMessage());
        } else {
            // Mark as failed
            job.setStatus(SyncJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage("Max retries exceeded: " + e.getMessage());
        }

        syncJobRepository.save(job);

        // Update history
        history.setStatus(job.getStatus());
        history.setCompletedAt(LocalDateTime.now());
        history.setDurationSeconds(
                Duration.between(history.getStartedAt(), LocalDateTime.now()).getSeconds()
        );
        history.getMetadata().put("error", e.getMessage());
        syncHistoryRepository.save(history);

        // Remove from running jobs
        runningJobs.remove(job.getId());
    }

    /**
     * Create sync history entry
     */
    private SyncHistory createSyncHistory(SyncJob job) {
        SyncHistory history = new SyncHistory();
        history.setSyncJob(job);
        history.setEntityType(job.getEntityType());
        history.setEntityId(job.getEntityId());
        history.setUserId(job.getUserId());
        history.setSyncType(job.getJobType().toString());
        history.setStatus(SyncJobStatus.RUNNING);
        history.setStartedAt(LocalDateTime.now());
        history.setMetadata(new HashMap<>());

        return syncHistoryRepository.save(history);
    }

    /**
     * Check if dataset should be synced
     */
    private boolean shouldSync(DataSet dataset) {
        if (!dataset.getAutoSync() || dataset.getStatus() != DataSetStatus.ACTIVE) {
            return false;
        }

        LocalDateTime lastSync = dataset.getLastSync();
        if (lastSync == null) {
            return true;
        }

        LocalDateTime nextSync = lastSync.plusHours(dataset.getSyncIntervalHours());
        return LocalDateTime.now().isAfter(nextSync);
    }

    /**
     * Schedule timeout check for job
     */
    private void scheduleTimeoutCheck(Long jobId, int timeoutMinutes) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            Future<?> future = runningJobs.get(jobId);
            if (future != null && !future.isDone()) {
                log.warn("Job {} timed out after {} minutes", jobId, timeoutMinutes);
                future.cancel(true);

                // Mark job as failed
                syncJobRepository.findById(jobId).ifPresent(job -> {
                    job.setStatus(SyncJobStatus.FAILED);
                    job.setCompletedAt(LocalDateTime.now());
                    job.setErrorMessage("Job timed out after " + timeoutMinutes + " minutes");
                    syncJobRepository.save(job);
                });

                runningJobs.remove(jobId);
            }
        }, timeoutMinutes, TimeUnit.MINUTES);
    }

    /**
     * Get sync job status
     */
    public SyncJobStatus getJobStatus(Long jobId) {
        return syncJobRepository.findById(jobId)
                .map(SyncJob::getStatus)
                .orElse(null);
    }

    /**
     * Get sync history for entity
     */
    public Page<SyncHistory> getSyncHistory(String entityType, Long entityId, Pageable pageable) {
        return syncHistoryRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    /**
     * Cancel a pending job
     */
    @Transactional
    public boolean cancelJob(Long jobId, Long userId) {
        Optional<SyncJob> jobOpt = syncJobRepository.findById(jobId);

        if (jobOpt.isEmpty()) {
            return false;
        }

        SyncJob job = jobOpt.get();

        // Check authorization
        if (!job.getUserId().equals(userId)) {
            throw new SecurityException("User not authorized to cancel this job");
        }

        // Only pending jobs can be cancelled
        if (job.getStatus() != SyncJobStatus.PENDING) {
            return false;
        }

        job.setStatus(SyncJobStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        syncJobRepository.save(job);

        return true;
    }

    /**
     * Get user's sync jobs
     */
    public Page<SyncJob> getUserSyncJobs(Long userId, SyncJobStatus status, Pageable pageable) {
        if (status != null) {
            return syncJobRepository.findByUserIdAndStatus(userId, status, pageable);
        }
        return syncJobRepository.findByUserId(userId, pageable);
    }

    /**
     * Cleanup old completed jobs
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM daily
    @Transactional
    public void cleanupOldJobs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

        int deletedJobs = syncJobRepository.deleteOldCompletedJobs(cutoffDate);
        int deletedHistory = syncHistoryRepository.deleteOldHistory(cutoffDate);

        log.info("Cleanup completed: deleted {} jobs and {} history records",
                deletedJobs, deletedHistory);
    }

    private Long getUserIdFromCreatedBy(String createdBy) {
        // Convert string username to user ID
        return userService.getUserByUsername(createdBy)
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + createdBy));
    }
}
