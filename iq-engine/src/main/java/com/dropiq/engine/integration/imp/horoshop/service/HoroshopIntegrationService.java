package com.dropiq.engine.integration.imp.horoshop.service;

import com.dropiq.engine.integration.imp.horoshop.HoroshopApiClient;
import com.dropiq.engine.integration.imp.horoshop.model.*;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.DatasetCategory;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.repository.DataSetRepository;
import com.dropiq.engine.product.service.DataSetService;
import com.dropiq.engine.product.support.HoroshopProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Main service for Horoshop integration
 * Handles product export, category management, and synchronization
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HoroshopIntegrationService {

    private final HoroshopApiClient horoshopClient;
    private final HoroshopProductMapper productMapper;
    private final DataSetService dataSetService;
    private final DataSetRepository dataSetRepository;
    private final HoroshopCategoryService categoryService;

    @Value("${horoshop.default.batch-size:50}")
    private int defaultBatchSize;

    @Value("${horoshop.default.retry-attempts:3}")
    private int defaultRetryAttempts;

    /**
     * Export dataset products to Horoshop
     */
    @Async
    public CompletableFuture<HoroshopBulkResult> exportDatasetToHoroshop(Long datasetId, String userId,
                                                                         HoroshopConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting export of dataset {} to Horoshop for user {}", datasetId, userId);

            try {
                // Get dataset
                DataSet dataset = dataSetService.getDataset(datasetId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

                // Test connection first
                if (!horoshopClient.testConnection(config)) {
                    throw new RuntimeException("Cannot connect to Horoshop API");
                }

                // Authenticate if token is not provided
                if (config.getToken() == null && config.getUsername() != null) {
                    String token = horoshopClient.authenticate(config);
                    config.setToken(token);
                }

                // Export categories first
                exportCategoriesToHoroshop(dataset, config);

                // Convert products to Horoshop format
                List<HoroshopProduct> horoshopProducts = dataset.getProducts().stream()
                        .filter(product -> product.getStatus().name().equals("DRAFT"))
                        .map(product -> productMapper.toHoroshopProduct(product, dataset))
                        .collect(Collectors.toList());

                log.info("Converted {} products to Horoshop format", horoshopProducts.size());

                // Import products to Horoshop
                HoroshopBulkResult result = horoshopClient.importProducts(config, horoshopProducts);

                // Update dataset metadata
                updateDatasetMetadata(dataset, result);

                log.info("Export completed. Success: {}, Errors: {}",
                        result.getTotalSuccess(), result.getTotalErrors());

                return result;

            } catch (Exception e) {
                log.error("Failed to export dataset {} to Horoshop: {}", datasetId, e.getMessage(), e);

                HoroshopBulkResult errorResult = new HoroshopBulkResult();
                errorResult.setStartTime(LocalDateTime.now());
                errorResult.setEndTime(LocalDateTime.now());

                HoroshopSyncStatus errorStatus = new HoroshopSyncStatus();
                errorStatus.setStatus("ERROR");
                errorStatus.setMessage("Export failed: " + e.getMessage());
                errorStatus.setTimestamp(LocalDateTime.now());
                errorResult.addResult(errorStatus);

                return errorResult;
            }
        });
    }

    /**
     * Export categories to Horoshop
     */
    private void exportCategoriesToHoroshop(DataSet dataset, HoroshopConfig config) {
        log.info("Exporting categories for dataset: {}", dataset.getName());

        try {
            // Get existing categories from Horoshop
            List<HoroshopCategory> existingCategories = horoshopClient.getCategories(config);
            Map<String, HoroshopCategory> existingCategoryMap = existingCategories.stream()
                    .collect(Collectors.toMap(cat -> cat.getName().toLowerCase(), cat -> cat));

            // Get dataset categories
            Set<DatasetCategory> datasetCategories = getDatasetCategories(dataset);

            for (DatasetCategory category : datasetCategories) {
                if (!existingCategoryMap.containsKey(category.getNameEn().toLowerCase())) {
                    HoroshopCategory horoshopCategory = categoryService.convertToHoroshopCategory(category);
                    horoshopClient.createCategory(config, horoshopCategory);
                    log.debug("Created category: {}", category.getNameEn());
                }
            }

        } catch (Exception e) {
            log.warn("Failed to export categories: {}", e.getMessage());
            // Continue with product export even if categories fail
        }
    }

    /**
     * Update single product in Horoshop
     */
    public HoroshopSyncStatus updateProductInHoroshop(Long productId, String userId, HoroshopConfig config) {
        log.info("Updating single product {} in Horoshop", productId);

        try {
            // Get product with dataset context
            Product product = getProductWithDataset(productId, userId);

            // Convert to Horoshop format
            HoroshopProduct horoshopProduct = productMapper.toHoroshopProduct(product,
                    product.getDatasets().iterator().next());

            // Update in Horoshop
            return horoshopClient.updateProduct(config, horoshopProduct);

        } catch (Exception e) {
            log.error("Failed to update product {} in Horoshop: {}", productId, e.getMessage());

            HoroshopSyncStatus status = new HoroshopSyncStatus();
            status.setProductArticle(productId.toString());
            status.setStatus("ERROR");
            status.setMessage("Update failed: " + e.getMessage());
            status.setTimestamp(LocalDateTime.now());

            return status;
        }
    }

    /**
     * Delete product from Horoshop
     */
    public HoroshopSyncStatus deleteProductFromHoroshop(String externalId, HoroshopConfig config) {
        log.info("Deleting product {} from Horoshop", externalId);

        try {
            return horoshopClient.deleteProduct(config, externalId);
        } catch (Exception e) {
            log.error("Failed to delete product {} from Horoshop: {}", externalId, e.getMessage());

            HoroshopSyncStatus status = new HoroshopSyncStatus();
            status.setProductArticle(externalId);
            status.setStatus("ERROR");
            status.setMessage("Delete failed: " + e.getMessage());
            status.setTimestamp(LocalDateTime.now());

            return status;
        }
    }

    /**
     * Sync dataset with Horoshop (two-way sync)
     */
    @Async
    public CompletableFuture<HoroshopBulkResult> syncDatasetWithHoroshop(Long datasetId, String userId,
                                                                         HoroshopConfig config,
                                                                         HoroshopSyncSettings syncSettings) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting two-way sync of dataset {} with Horoshop", datasetId);

            HoroshopBulkResult result = new HoroshopBulkResult();
            result.setStartTime(LocalDateTime.now());

            try {
                DataSet dataset = dataSetService.getDataset(datasetId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

                // Step 1: Export local changes to Horoshop
                if (syncSettings.isExportToHoroshop()) {
                    log.info("Exporting local changes to Horoshop");
                    List<Product> changedProducts = getChangedProducts(dataset, syncSettings.getLastSyncTime());

                    if (!changedProducts.isEmpty()) {
                        List<HoroshopProduct> horoshopProducts = changedProducts.stream()
                                .map(product -> productMapper.toHoroshopProduct(product, dataset))
                                .collect(Collectors.toList());

                        HoroshopBulkResult exportResult = horoshopClient.importProducts(config, horoshopProducts);
                        mergeResults(result, exportResult);
                    }
                }

                // Step 2: Import changes from Horoshop
                if (syncSettings.isImportFromHoroshop()) {
                    log.info("Importing changes from Horoshop");
                    importChangesFromHoroshop(dataset, config, syncSettings, result);
                }

                // Step 3: Update sync timestamp
                updateLastSyncTime(dataset);

                result.setEndTime(LocalDateTime.now());
                log.info("Sync completed. Total operations: {}, Success: {}, Errors: {}",
                        result.getTotalProcessed(), result.getTotalSuccess(), result.getTotalErrors());

                return result;

            } catch (Exception e) {
                log.error("Sync failed for dataset {}: {}", datasetId, e.getMessage(), e);

                HoroshopSyncStatus errorStatus = new HoroshopSyncStatus();
                errorStatus.setStatus("ERROR");
                errorStatus.setMessage("Sync failed: " + e.getMessage());
                errorStatus.setTimestamp(LocalDateTime.now());
                result.addResult(errorStatus);
                result.setEndTime(LocalDateTime.now());

                return result;
            }
        });
    }

    /**
     * Bulk update products in Horoshop
     */
    @Async
    public CompletableFuture<HoroshopBulkResult> bulkUpdateProductsInHoroshop(List<Long> productIds,
                                                                              String userId,
                                                                              HoroshopConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting bulk update of {} products in Horoshop", productIds.size());

            HoroshopBulkResult result = new HoroshopBulkResult();
            result.setStartTime(LocalDateTime.now());

            try {
                List<HoroshopProduct> horoshopProducts = new ArrayList<>();

                for (Long productId : productIds) {
                    try {
                        Product product = getProductWithDataset(productId, userId);
                        HoroshopProduct horoshopProduct = productMapper.toHoroshopProduct(product,
                                product.getDatasets().iterator().next());
                        horoshopProducts.add(horoshopProduct);
                    } catch (Exception e) {
                        log.warn("Failed to convert product {}: {}", productId, e.getMessage());

                        HoroshopSyncStatus status = new HoroshopSyncStatus();
                        status.setProductArticle(productId.toString());
                        status.setStatus("ERROR");
                        status.setMessage("Conversion failed: " + e.getMessage());
                        status.setTimestamp(LocalDateTime.now());
                        result.addResult(status);
                    }
                }

                if (!horoshopProducts.isEmpty()) {
                    HoroshopBulkResult updateResult = horoshopClient.importProducts(config, horoshopProducts);
                    mergeResults(result, updateResult);
                }

                result.setEndTime(LocalDateTime.now());
                return result;

            } catch (Exception e) {
                log.error("Bulk update failed: {}", e.getMessage(), e);

                HoroshopSyncStatus errorStatus = new HoroshopSyncStatus();
                errorStatus.setStatus("ERROR");
                errorStatus.setMessage("Bulk update failed: " + e.getMessage());
                errorStatus.setTimestamp(LocalDateTime.now());
                result.addResult(errorStatus);
                result.setEndTime(LocalDateTime.now());

                return result;
            }
        });
    }

    /**
     * Get product statistics from Horoshop
     */
    public HoroshopExportStatistics getHoroshopStatistics(HoroshopConfig config) {
        log.info("Fetching statistics from Horoshop");

        try {
            HoroshopExportSettings settings = new HoroshopExportSettings();
            List<HoroshopProduct> products = horoshopClient.exportProducts(config, settings);
            List<HoroshopCategory> categories = horoshopClient.getCategories(config);

            HoroshopExportStatistics stats = new HoroshopExportStatistics();
            stats.setTotalProducts(products.size());
            stats.setTotalCategories(categories.size());

            // Calculate additional statistics
            Map<String, Long> productsByCategory = products.stream()
                    .filter(p -> p.getParent() != null)
                    .collect(Collectors.groupingBy(HoroshopProduct::getParent, Collectors.counting()));
            stats.setProductsByCategory(productsByCategory);

            long activeProducts = products.stream()
                    .filter(p -> "В наличии".equals(p.getPresence()))
                    .count();
            stats.setActiveProducts((int) activeProducts);
            stats.setInactiveProducts(products.size() - (int) activeProducts);

            OptionalDouble avgPrice = products.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice() > 0)
                    .mapToDouble(HoroshopProduct::getPrice)
                    .average();
            stats.setAveragePrice(avgPrice.orElse(0.0));

            return stats;

        } catch (Exception e) {
            log.error("Failed to fetch Horoshop statistics: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch statistics: " + e.getMessage(), e);
        }
    }

    /**
     * Validate Horoshop configuration
     */
    public HoroshopConfigValidation validateConfig(HoroshopConfig config) {
        log.info("Validating Horoshop configuration");

        HoroshopConfigValidation validation = new HoroshopConfigValidation();
        validation.setConfig(config);
        validation.setTimestamp(LocalDateTime.now());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Basic validation
        if (config.getDomain() == null || config.getDomain().trim().isEmpty()) {
            errors.add("Domain is required");
        }

        if (config.getApiUrl() == null || config.getApiUrl().trim().isEmpty()) {
            errors.add("API URL is required");
        }

        if (config.getToken() == null &&
                (config.getUsername() == null || config.getPassword() == null)) {
            errors.add("Either token or username/password is required");
        }

        // Connection test
        if (errors.isEmpty()) {
            try {
                boolean connected = horoshopClient.testConnection(config);
                validation.setConnectionSuccess(connected);

                if (!connected) {
                    errors.add("Cannot connect to Horoshop API");
                }
            } catch (Exception e) {
                validation.setConnectionSuccess(false);
                errors.add("Connection test failed: " + e.getMessage());
            }
        }

        // Performance warnings
        if (config.getBatchSize() > 100) {
            warnings.add("Large batch size may cause timeouts");
        }

        if (config.getTimeout() < 30) {
            warnings.add("Short timeout may cause request failures");
        }

        validation.setErrors(errors);
        validation.setWarnings(warnings);
        validation.setValid(errors.isEmpty());

        return validation;
    }

    // Helper methods

    private Set<DatasetCategory> getDatasetCategories(DataSet dataset) {
        return dataset.getProducts().stream()
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Product getProductWithDataset(Long productId, String userId) {
        // This would need to be implemented to get product with proper dataset context
        // For now, simplified implementation
        return dataSetService.getUserDatasets(userId).stream()
                .flatMap(dataset -> dataset.getProducts().stream())
                .filter(product -> product.getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    }

    private List<Product> getChangedProducts(DataSet dataset, LocalDateTime since) {
        if (since == null) {
            return new ArrayList<>(dataset.getProducts());
        }

        return dataset.getProducts().stream()
                .filter(product -> product.getUpdatedAt().isAfter(since))
                .collect(Collectors.toList());
    }

    private void importChangesFromHoroshop(DataSet dataset, HoroshopConfig config,
                                           HoroshopSyncSettings syncSettings, HoroshopBulkResult result) {
        try {
            HoroshopExportSettings exportSettings = new HoroshopExportSettings();
            List<HoroshopProduct> horoshopProducts = horoshopClient.exportProducts(config, exportSettings);

            for (HoroshopProduct horoshopProduct : horoshopProducts) {
                try {
                    // Find matching local product
                    Optional<Product> localProduct = dataset.getProducts().stream()
                            .filter(p -> p.getExternalId().equals(horoshopProduct.getArticle()))
                            .findFirst();

                    if (localProduct.isPresent()) {
                        // Update existing product
                        productMapper.updateFromHoroshopProduct(localProduct.get(), horoshopProduct);

                        HoroshopSyncStatus status = new HoroshopSyncStatus();
                        status.setProductArticle(horoshopProduct.getArticle());
                        status.setStatus("SUCCESS");
                        status.setMessage("Product updated from Horoshop");
                        status.setTimestamp(LocalDateTime.now());
                        result.addResult(status);
                    }
                } catch (Exception e) {
                    log.warn("Failed to import product {}: {}", horoshopProduct.getArticle(), e.getMessage());

                    HoroshopSyncStatus status = new HoroshopSyncStatus();
                    status.setProductArticle(horoshopProduct.getArticle());
                    status.setStatus("ERROR");
                    status.setMessage("Import failed: " + e.getMessage());
                    status.setTimestamp(LocalDateTime.now());
                    result.addResult(status);
                }
            }

        } catch (Exception e) {
            log.error("Failed to import changes from Horoshop: {}", e.getMessage());
            throw new RuntimeException("Import from Horoshop failed: " + e.getMessage(), e);
        }
    }

    private void mergeResults(HoroshopBulkResult target, HoroshopBulkResult source) {
        target.getResults().addAll(source.getResults());
        target.setTotalProcessed(target.getTotalProcessed() + source.getTotalProcessed());
        target.setTotalSuccess(target.getTotalSuccess() + source.getTotalSuccess());
        target.setTotalErrors(target.getTotalErrors() + source.getTotalErrors());
        target.setTotalSkipped(target.getTotalSkipped() + source.getTotalSkipped());
    }

    private void updateDatasetMetadata(DataSet dataset, HoroshopBulkResult result) {
//        Hibernate.initialize(dataset.getMetadata());
//        dataset.getMetadata().put("horoshop_last_export", LocalDateTime.now().toString());
//        dataset.getMetadata().put("horoshop_exported_products", String.valueOf(result.getTotalSuccess()));
//        dataset.getMetadata().put("horoshop_export_errors", String.valueOf(result.getTotalErrors()));
//        dataSetRepository.save(dataset);
    }

    private void updateLastSyncTime(DataSet dataset) {
        dataset.getMetadata().put("horoshop_last_sync", LocalDateTime.now().toString());
        dataSetRepository.save(dataset);
    }
}