package com.dropiq.admin.service;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.DataSource;
import com.dropiq.admin.model.DatasetStatus;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class DataSetService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private EngineIntegrationService integrationService;

    /**
     * Create a new empty dataset
     */
    public DataSet createEmptyDataset(String name, String description, String createdBy) {
        DataSet dataset = dataManager.create(DataSet.class);
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setCreatedBy(createdBy);
        dataset.setStatus(DatasetStatus.DRAFT);
        dataset.setTotalProducts(0);
        dataset.setActiveProducts(0);
        dataset.setOptimizedProducts(0);

        return dataManager.save(dataset);
    }

    /**
     * Create dataset from data source
     */
    public DataSet createDatasetFromSource(DataSource dataSource, String name, String description, String createdBy) {
        log.info("Creating dataset '{}' from data source '{}'", name, dataSource.getName());

        DataSet dataset = dataManager.create(DataSet.class);
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setDataSource(dataSource);
        dataset.setCreatedBy(createdBy);
        dataset.setStatus(DatasetStatus.PROCESSING);

        // Save dataset first
        dataset = dataManager.save(dataset);

        try {
            // Use integration service to create in engine and sync back
            DataSet engineCreatedDataset = integrationService.createDatasetFromSource(dataSource, name, description);

            // Update local dataset with engine data
            dataset.setStatus(engineCreatedDataset.getStatus());
            dataset.setTotalProducts(engineCreatedDataset.getTotalProducts());
            dataset.setActiveProducts(engineCreatedDataset.getActiveProducts());
            dataset.getMetadata().putAll(engineCreatedDataset.getMetadata());

            dataset = dataManager.save(dataset);

            log.info("Dataset '{}' created successfully with {} products", name, dataset.getTotalProducts());
            return dataset;

        } catch (Exception e) {
            log.error("Error creating dataset from source: {}", e.getMessage(), e);
            dataset.setStatus(DatasetStatus.ERROR);
            dataset.setLastErrorMessage("Failed to create from source: " + e.getMessage());
            dataset.setErrorCount(dataset.getErrorCount() + 1);
            dataManager.save(dataset);
            throw new RuntimeException("Failed to create dataset: " + e.getMessage(), e);
        }
    }

    /**
     * Get all datasets for a user
     */
    public List<DataSet> getUserDatasets(String createdBy) {
        return dataManager.load(DataSet.class)
                .query("select d from Dataset d where d.createdBy = :createdBy order by d.createdAt desc")
                .parameter("createdBy", createdBy)
                .list();
    }

    /**
     * Get dataset by ID with ownership check
     */
    public Optional<DataSet> getDataset(UUID id, String createdBy) {
        try {
            Optional<DataSet> dataset = dataManager.load(DataSet.class)
                    .query("select d from Dataset d where d.id = :id and d.createdBy = :createdBy")
                    .parameter("id", id)
                    .parameter("createdBy", createdBy)
                    .optional();
            return dataset;
        } catch (Exception e) {
            log.error("Error loading dataset {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Update dataset
     */
    public DataSet updateDataset(DataSet dataset) {
        dataset.setUpdatedAt(LocalDateTime.now());
        return dataManager.save(dataset);
    }

    /**
     * Delete dataset
     */
    public void deleteDataset(UUID id, String createdBy) {
        Optional<DataSet> datasetOpt = getDataset(id, createdBy);
        if (datasetOpt.isPresent()) {
            DataSet dataset = datasetOpt.get();

            // TODO: Also delete from engine if exists
            if (dataset.getMetadata().containsKey("engineDatasetId")) {
                try {
                    // Call engine API to delete dataset
                    log.info("Dataset '{}' should be deleted from engine as well", dataset.getName());
                } catch (Exception e) {
                    log.warn("Failed to delete dataset from engine: {}", e.getMessage());
                }
            }

            dataManager.remove(Id.of(dataset));
            log.info("Dataset '{}' deleted by user '{}'", dataset.getName(), createdBy);
        } else {
            throw new RuntimeException("Dataset not found or access denied");
        }
    }

    /**
     * Sync dataset with data source
     */
    public void syncDataset(DataSet dataset) {
        log.info("Syncing dataset '{}'", dataset.getName());

        dataset.setStatus(DatasetStatus.PROCESSING);
        dataset.setLastSync(LocalDateTime.now());
        dataset = dataManager.save(dataset);

        try {
            // Use integration service to sync
            integrationService.syncDataset(dataset);

            // Reload to get updated data
            //dataset = dataManager.load(dataset, "dataset-with-stats");

            log.info("Dataset '{}' synced successfully", dataset.getName());

        } catch (Exception e) {
            log.error("Error syncing dataset: {}", e.getMessage(), e);
            dataset.setStatus(DatasetStatus.ERROR);
            dataset.setLastErrorMessage("Sync failed: " + e.getMessage());
            dataset.setErrorCount(dataset.getErrorCount() + 1);
            dataManager.save(dataset);
            throw new RuntimeException("Sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get dataset statistics
     */
    public DatasetStatistics getDatasetStatistics(UUID datasetId, String createdBy) {
        Optional<DataSet> datasetOpt = getDataset(datasetId, createdBy);
        if (!datasetOpt.isPresent()) {
            throw new RuntimeException("Dataset not found or access denied");
        }

        DataSet dataset = datasetOpt.get();
        DatasetStatistics stats = new DatasetStatistics();

        stats.setDatasetId(datasetId);
        stats.setDatasetName(dataset.getName());
        stats.setTotalProducts(dataset.getTotalProducts());
        stats.setActiveProducts(dataset.getActiveProducts());
        stats.setOptimizedProducts(dataset.getOptimizedProducts());
        stats.setStatus(dataset.getStatus());
        stats.setLastSync(dataset.getLastSync());
        stats.setCreatedAt(dataset.getCreatedAt());

        // Get additional statistics from integration service
        try {
            Map<String, Object> engineStats = integrationService.getDatasetStatistics(dataset);
            stats.getAdditionalMetrics().putAll(engineStats);
        } catch (Exception e) {
            log.warn("Failed to get engine statistics: {}", e.getMessage());
        }

        return stats;
    }

    /**
     * Apply bulk operations to dataset products
     */
    public void applyBulkOperation(UUID datasetId, String createdBy, BulkOperation operation) {
        Optional<DataSet> datasetOpt = getDataset(datasetId, createdBy);
        if (!datasetOpt.isPresent()) {
            throw new RuntimeException("Dataset not found or access denied");
        }

        DataSet dataset = datasetOpt.get();

        try {
            switch (operation.getType()) {
                case ACTIVATE_ALL:
                    activateAllProducts(dataset);
                    break;
                case DEACTIVATE_ALL:
                    deactivateAllProducts(dataset);
                    break;
                case APPLY_MARKUP:
                    applyMarkupToAll(dataset, operation.getMarkupPercentage());
                    break;
                case SET_CATEGORY:
                    setCategoryForAll(dataset, operation.getCategoryName());
                    break;
                case DELETE_INACTIVE:
                    deleteInactiveProducts(dataset);
                    break;
            }

            // Update dataset statistics
            updateDatasetStatistics(dataset);

        } catch (Exception e) {
            log.error("Error applying bulk operation: {}", e.getMessage(), e);
            throw new RuntimeException("Bulk operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Search datasets
     */
    public List<DataSet> searchDatasets(String createdBy, String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getUserDatasets(createdBy);
        }

        return dataManager.load(DataSet.class)
                .query("select d from Dataset d where d.createdBy = :createdBy and " +
                        "(lower(d.name) like :searchTerm or lower(d.description) like :searchTerm) " +
                        "order by d.createdAt desc")
                .parameter("createdBy", createdBy)
                .parameter("searchTerm", "%" + searchTerm.toLowerCase() + "%")
                .list();
    }

    /**
     * Get datasets by status
     */
    public List<DataSet> getDatasetsByStatus(String createdBy, DatasetStatus status) {
        return dataManager.load(DataSet.class)
                .query("select d from Dataset d where d.createdBy = :createdBy and d.status = :status " +
                        "order by d.updatedAt desc")
                .parameter("createdBy", createdBy)
                .parameter("status", status)
                .list();
    }

    /**
     * Merge two datasets
     */
    public DataSet mergeDatasets(UUID dataset1Id, UUID dataset2Id, String newName, String createdBy) {
        Optional<DataSet> dataset1Opt = getDataset(dataset1Id, createdBy);
        Optional<DataSet> dataset2Opt = getDataset(dataset2Id, createdBy);

        if (!dataset1Opt.isPresent() || !dataset2Opt.isPresent()) {
            throw new RuntimeException("One or both datasets not found or access denied");
        }

        DataSet dataset1 = dataset1Opt.get();
        DataSet dataset2 = dataset2Opt.get();

        try {
            // Create new merged dataset
            DataSet mergedDataset = dataManager.create(DataSet.class);
            mergedDataset.setName(newName);
            mergedDataset.setDescription("Merged from: " + dataset1.getName() + " + " + dataset2.getName());
            mergedDataset.setCreatedBy(createdBy);
            mergedDataset.setStatus(DatasetStatus.PROCESSING);

            // Merge settings (use dataset1 as base, override with dataset2 if not null)
            mergedDataset.setDefaultMarkup(dataset2.getDefaultMarkup() != null ?
                    dataset2.getDefaultMarkup() : dataset1.getDefaultMarkup());
            mergedDataset.setMinProfitMargin(dataset2.getMinProfitMargin() != null ?
                    dataset2.getMinProfitMargin() : dataset1.getMinProfitMargin());
            mergedDataset.setAiOptimizationEnabled(dataset1.getAiOptimizationEnabled() || dataset2.getAiOptimizationEnabled());
            mergedDataset.setSeoOptimizationEnabled(dataset1.getSeoOptimizationEnabled() || dataset2.getSeoOptimizationEnabled());
            mergedDataset.setTrendAnalysisEnabled(dataset1.getTrendAnalysisEnabled() || dataset2.getTrendAnalysisEnabled());

            mergedDataset = dataManager.save(mergedDataset);

            // Use integration service to merge in engine
            // TODO: Implement merge in integration service

            mergedDataset.setStatus(DatasetStatus.ACTIVE);
            mergedDataset.setTotalProducts(dataset1.getTotalProducts() + dataset2.getTotalProducts());
            mergedDataset.setActiveProducts(dataset1.getActiveProducts() + dataset2.getActiveProducts());

            // Add metadata
            mergedDataset.getMetadata().put("mergedFrom", dataset1Id + "," + dataset2Id);
            mergedDataset.getMetadata().put("mergeDate", LocalDateTime.now().toString());

            return dataManager.save(mergedDataset);

        } catch (Exception e) {
            log.error("Error merging datasets: {}", e.getMessage(), e);
            throw new RuntimeException("Merge failed: " + e.getMessage(), e);
        }
    }

    // Private helper methods
    private void activateAllProducts(DataSet dataset) {
        // TODO: Implement bulk activation
        log.info("Activating all products in dataset '{}'", dataset.getName());
    }

    private void deactivateAllProducts(DataSet dataset) {
        // TODO: Implement bulk deactivation
        log.info("Deactivating all products in dataset '{}'", dataset.getName());
    }

    private void applyMarkupToAll(DataSet dataset, BigDecimal markupPercentage) {
        // TODO: Implement bulk markup application
        log.info("Applying {}% markup to all products in dataset '{}'", markupPercentage, dataset.getName());
    }

    private void setCategoryForAll(DataSet dataset, String categoryName) {
        // TODO: Implement bulk category setting
        log.info("Setting category '{}' for all products in dataset '{}'", categoryName, dataset.getName());
    }

    private void deleteInactiveProducts(DataSet dataset) {
        // TODO: Implement bulk deletion of inactive products
        log.info("Deleting inactive products from dataset '{}'", dataset.getName());
    }

    private void updateDatasetStatistics(DataSet dataset) {
        // TODO: Implement statistics update
        dataset.setUpdatedAt(LocalDateTime.now());
        dataManager.save(dataset);
    }

    // Data classes
    public static class DatasetStatistics {
        private UUID datasetId;
        private String datasetName;
        private Integer totalProducts;
        private Integer activeProducts;
        private Integer optimizedProducts;
        private DatasetStatus status;
        private LocalDateTime lastSync;
        private LocalDateTime createdAt;
        private Map<String, Object> additionalMetrics = new HashMap<>();

        // Getters and setters
        public UUID getDatasetId() { return datasetId; }
        public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

        public String getDatasetName() { return datasetName; }
        public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

        public Integer getTotalProducts() { return totalProducts; }
        public void setTotalProducts(Integer totalProducts) { this.totalProducts = totalProducts; }

        public Integer getActiveProducts() { return activeProducts; }
        public void setActiveProducts(Integer activeProducts) { this.activeProducts = activeProducts; }

        public Integer getOptimizedProducts() { return optimizedProducts; }
        public void setOptimizedProducts(Integer optimizedProducts) { this.optimizedProducts = optimizedProducts; }

        public DatasetStatus getStatus() { return status; }
        public void setStatus(DatasetStatus status) { this.status = status; }

        public LocalDateTime getLastSync() { return lastSync; }
        public void setLastSync(LocalDateTime lastSync) { this.lastSync = lastSync; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public Map<String, Object> getAdditionalMetrics() { return additionalMetrics; }
        public void setAdditionalMetrics(Map<String, Object> additionalMetrics) { this.additionalMetrics = additionalMetrics; }
    }

    public static class BulkOperation {
        public enum Type {
            ACTIVATE_ALL,
            DEACTIVATE_ALL,
            APPLY_MARKUP,
            SET_CATEGORY,
            DELETE_INACTIVE
        }

        private Type type;
        private BigDecimal markupPercentage;
        private String categoryName;

        // Getters and setters
        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }

        public BigDecimal getMarkupPercentage() { return markupPercentage; }
        public void setMarkupPercentage(BigDecimal markupPercentage) { this.markupPercentage = markupPercentage; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    }
}
