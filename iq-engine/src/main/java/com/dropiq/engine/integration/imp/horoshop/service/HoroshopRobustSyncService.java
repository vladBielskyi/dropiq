package com.dropiq.engine.integration.imp.horoshop.service;

import com.dropiq.engine.integration.imp.horoshop.HoroshopApiClient;
import com.dropiq.engine.integration.imp.horoshop.model.*;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.model.ProductStatus;
import com.dropiq.engine.product.repository.DataSetRepository;
import com.dropiq.engine.product.repository.ProductRepository;
import com.dropiq.engine.product.support.HoroshopProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Robust synchronization service between local database and Horoshop
 * Focuses on stock availability and price synchronization
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HoroshopRobustSyncService {

    private final HoroshopApiClient horoshopClient;
    private final HoroshopProductMapper productMapper;
    private final ProductRepository productRepository;
    private final DataSetRepository dataSetRepository;

    @Value("${horoshop.sync.batch-size:20}")
    private int syncBatchSize;

    @Value("${horoshop.sync.max-retries:3}")
    private int maxRetries;

    @Value("${horoshop.sync.price-tolerance:0.01}")
    private double priceTolerance;

    @Value("${horoshop.sync.stock-threshold:5}")
    private int lowStockThreshold;

    /**
     * Main robust sync method - handles stock and price synchronization
     */
    @Async
    public CompletableFuture<HoroshopSyncResult> performRobustSync(Long datasetId, String userId,
                                                                   HoroshopConfig config,
                                                                   HoroshopSyncOptions options) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting robust sync for dataset {} with focus on stock and price", datasetId);

            HoroshopSyncResult result = new HoroshopSyncResult();
            result.setDatasetId(datasetId);
            result.setStartTime(LocalDateTime.now());
            result.setUserId(userId);
            result.setSyncType("ROBUST_STOCK_PRICE_SYNC");

            try {
                // Get dataset
                DataSet dataset = dataSetRepository.findById(datasetId)
                        .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

                // Get products that need sync
                List<Product> productsToSync = getProductsForSync(dataset, options);
                log.info("Found {} products to sync", productsToSync.size());

                if (productsToSync.isEmpty()) {
                    result.setMessage("No products require synchronization");
                    result.setEndTime(LocalDateTime.now());
                    return result;
                }

                // Step 1: Export local changes to Horoshop (stock & price updates)
                if (options.isExportToHoroshop()) {
                    HoroshopBulkResult exportResult = exportStockAndPriceUpdates(productsToSync, config, options);
                    result.setExportResult(exportResult);
                    log.info("Export completed: {} success, {} errors",
                            exportResult.getTotalSuccess(), exportResult.getTotalErrors());
                }

                // Step 2: Import current data from Horoshop
                if (options.isImportFromHoroshop()) {
                    HoroshopBulkResult importResult = importStockAndPriceUpdates(productsToSync, config, options);
                    result.setImportResult(importResult);
                    log.info("Import completed: {} success, {} errors",
                            importResult.getTotalSuccess(), importResult.getTotalErrors());
                }

                // Step 3: Handle conflicts and reconciliation
                List<HoroshopSyncConflict> conflicts = resolveConflicts(productsToSync, config, options);
                result.setConflicts(conflicts);

                if (!conflicts.isEmpty()) {
                    log.warn("Found {} sync conflicts", conflicts.size());
                }

                // Step 4: Update sync timestamps
                updateSyncTimestamps(dataset, productsToSync);

                // Calculate final statistics
                result.calculateStatistics();
                result.setEndTime(LocalDateTime.now());
                result.setSuccess(true);

                log.info("Robust sync completed successfully for dataset {}", datasetId);
                return result;

            } catch (Exception e) {
                log.error("Robust sync failed for dataset {}: {}", datasetId, e.getMessage(), e);
                result.setSuccess(false);
                result.setMessage("Sync failed: " + e.getMessage());
                result.setEndTime(LocalDateTime.now());
                return result;
            }
        });
    }

    /**
     * Export stock and price updates to Horoshop
     */
    private HoroshopBulkResult exportStockAndPriceUpdates(List<Product> products,
                                                          HoroshopConfig config,
                                                          HoroshopSyncOptions options) {
        log.info("Exporting stock and price updates for {} products", products.size());

        HoroshopBulkResult result = new HoroshopBulkResult();
        result.setStartTime(LocalDateTime.now());

        try {
            // Split into batches for processing
            List<List<Product>> batches = splitIntoBatches(products, syncBatchSize);

            for (int i = 0; i < batches.size(); i++) {
                List<Product> batch = batches.get(i);
                log.info("Processing export batch {}/{} with {} products", i + 1, batches.size(), batch.size());

                try {
                    // Convert to Horoshop format with focus on stock and price
                    List<HoroshopProduct> horoshopProducts = batch.stream()
                            .map(product -> createStockPriceUpdate(product, options))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!horoshopProducts.isEmpty()) {
                        // Import with update mode
                        HoroshopBatchResponse batchResult = importBatchWithRetry(config, horoshopProducts);
                        processBatchResult(batchResult, result);
                    }

                    // Rate limiting between batches
                    if (i < batches.size() - 1) {
                        Thread.sleep(1000); // 1 second delay
                    }

                } catch (Exception e) {
                    log.error("Export batch {} failed: {}", i + 1, e.getMessage());
                    // Mark batch products as failed
                    batch.forEach(product -> addFailedResult(result, product.getExternalId(),
                            "Export failed: " + e.getMessage()));
                }
            }

        } catch (Exception e) {
            log.error("Export process failed: {}", e.getMessage());
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }

        result.setEndTime(LocalDateTime.now());
        return result;
    }

    /**
     * Import current stock and price data from Horoshop
     */
    private HoroshopBulkResult importStockAndPriceUpdates(List<Product> products,
                                                          HoroshopConfig config,
                                                          HoroshopSyncOptions options) {
        log.info("Importing current stock and price data for {} products", products.size());

        HoroshopBulkResult result = new HoroshopBulkResult();
        result.setStartTime(LocalDateTime.now());

        try {
            // Get current data from Horoshop
            HoroshopExportSettings exportSettings = new HoroshopExportSettings();
            exportSettings.setIncludePrices(true);
            exportSettings.setIncludeStock(true);
            exportSettings.setIncludeImages(false); // Not needed for sync
            exportSettings.setIncludeCategories(false);

            List<HoroshopProduct> horoshopProducts = horoshopClient.exportProducts(config, exportSettings);
            Map<String, HoroshopProduct> horoshopProductMap = horoshopProducts.stream()
                    .collect(Collectors.toMap(HoroshopProduct::getArticle, p -> p));

            int updatedCount = 0;
            int conflictCount = 0;

            for (Product localProduct : products) {
                try {
                    HoroshopProduct remoteProduct = horoshopProductMap.get(localProduct.getExternalId());

                    if (remoteProduct != null) {
                        HoroshopSyncStatus status = updateLocalProductFromRemote(localProduct, remoteProduct, options);

                        if ("SUCCESS".equals(status.getStatus())) {
                            updatedCount++;
                        } else if ("CONFLICT".equals(status.getStatus())) {
                            conflictCount++;
                        }

                        addSyncResult(result, status);
                    } else {
                        // Product not found in Horoshop
                        HoroshopSyncStatus status = new HoroshopSyncStatus();
                        status.setProductArticle(localProduct.getExternalId());
                        status.setStatus("NOT_FOUND");
                        status.setMessage("Product not found in Horoshop");
                        status.setTimestamp(LocalDateTime.now());
                        addSyncResult(result, status);
                    }

                } catch (Exception e) {
                    log.error("Failed to import data for product {}: {}",
                            localProduct.getExternalId(), e.getMessage());

                    addFailedResult(result, localProduct.getExternalId(),
                            "Import failed: " + e.getMessage());
                }
            }

            log.info("Import completed: {} updated, {} conflicts", updatedCount, conflictCount);

        } catch (Exception e) {
            log.error("Import process failed: {}", e.getMessage());
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }

        result.setEndTime(LocalDateTime.now());
        return result;
    }

    /**
     * Create Horoshop product update focused on stock and price
     */
    private HoroshopProduct createStockPriceUpdate(Product product, HoroshopSyncOptions options) {
        HoroshopProduct horoshopProduct = new HoroshopProduct();

        // Essential identification
        horoshopProduct.setArticle(product.getExternalId());

        // Stock information
        if (options.isSyncStock()) {
            horoshopProduct.setQuantity(product.getStock() != null ? product.getStock() : 0);
            horoshopProduct.setPresence(determinePresence(product));
        }

        // Price information
        if (options.isSyncPrices()) {
            if (product.getSellingPrice() != null) {
                horoshopProduct.setPrice(product.getSellingPrice().doubleValue());
            }

            // Set old price if we have a discount
            if (product.getOriginalPrice() != null && product.getSellingPrice() != null &&
                    product.getOriginalPrice().compareTo(product.getSellingPrice()) > 0) {
                horoshopProduct.setPriceOld(product.getOriginalPrice().doubleValue());
            }
        }

        // Minimal title (required for updates)
        Map<String, String> title = new HashMap<>();
        title.put("en", product.getName());
        horoshopProduct.setTitle(title);

        return horoshopProduct;
    }

    /**
     * Update local product with remote data and detect conflicts
     */
    private HoroshopSyncStatus updateLocalProductFromRemote(Product localProduct,
                                                    HoroshopProduct remoteProduct,
                                                    HoroshopSyncOptions options) {
        HoroshopSyncStatus status = new HoroshopSyncStatus();
        status.setProductArticle(localProduct.getExternalId());
        status.setTimestamp(LocalDateTime.now());

        List<String> changes = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        try {
            // Stock synchronization
            if (options.isSyncStock() && remoteProduct.getQuantity() != null) {
                Integer remoteStock = remoteProduct.getQuantity();
                Integer localStock = localProduct.getStock() != null ? localProduct.getStock() : 0;

                if (!remoteStock.equals(localStock)) {
                    // Check for conflicts (both sides changed)
                    if (hasRecentLocalStockChange(localProduct) && options.getConflictResolution() == ConflictResolution.DETECT_ONLY) {
                        conflicts.add(String.format("Stock conflict: Local=%d, Remote=%d", localStock, remoteStock));
                    } else {
                        // Apply conflict resolution
                        switch (options.getConflictResolution()) {
                            case REMOTE_WINS:
                                localProduct.setStock(remoteStock);
                                changes.add(String.format("Stock updated: %d → %d", localStock, remoteStock));
                                break;
                            case LOCAL_WINS:
                                // Keep local value, no change
                                break;
                            case HIGHEST_WINS:
                                if (remoteStock > localStock) {
                                    localProduct.setStock(remoteStock);
                                    changes.add(String.format("Stock updated to higher value: %d → %d", localStock, remoteStock));
                                }
                                break;
                        }
                    }
                }

                // Update availability based on stock
                boolean remoteAvailable = "В наличии".equals(remoteProduct.getPresence());
                if (localProduct.getAvailable() != remoteAvailable) {
                    localProduct.setAvailable(remoteAvailable);
                    changes.add(String.format("Availability updated: %s → %s",
                            localProduct.getAvailable(), remoteAvailable));
                }
            }

            // Price synchronization
            if (options.isSyncPrices() && remoteProduct.getPrice() != null) {
                BigDecimal remotePrice = BigDecimal.valueOf(remoteProduct.getPrice());
                BigDecimal localPrice = localProduct.getSellingPrice();

                if (localPrice == null || !isPriceEqual(localPrice, remotePrice)) {
                    // Check for conflicts
                    if (hasRecentLocalPriceChange(localProduct) && options.getConflictResolution() == ConflictResolution.DETECT_ONLY) {
                        conflicts.add(String.format("Price conflict: Local=%.2f, Remote=%.2f",
                                localPrice != null ? localPrice.doubleValue() : 0.0, remotePrice.doubleValue()));
                    } else {
                        // Apply conflict resolution for prices
                        switch (options.getConflictResolution()) {
                            case REMOTE_WINS:
                                localProduct.setSellingPrice(remotePrice);
                                changes.add(String.format("Price updated: %.2f → %.2f",
                                        localPrice != null ? localPrice.doubleValue() : 0.0, remotePrice.doubleValue()));
                                break;
                            case LOCAL_WINS:
                                // Keep local value
                                break;
                            case LOWEST_WINS:
                                if (localPrice == null || remotePrice.compareTo(localPrice) < 0) {
                                    localProduct.setSellingPrice(remotePrice);
                                    changes.add(String.format("Price updated to lower value: %.2f → %.2f",
                                            localPrice != null ? localPrice.doubleValue() : 0.0, remotePrice.doubleValue()));
                                }
                                break;
                        }
                    }
                }
            }

            // Save changes
            if (!changes.isEmpty()) {
                localProduct.setLastSync(LocalDateTime.now());
                productRepository.save(localProduct);
            }

            // Set status
            if (!conflicts.isEmpty()) {
                status.setStatus("CONFLICT");
                status.setMessage("Conflicts detected: " + String.join("; ", conflicts));
                status.setWarnings(conflicts);
            } else if (!changes.isEmpty()) {
                status.setStatus("SUCCESS");
                status.setMessage("Updated: " + String.join("; ", changes));
            } else {
                status.setStatus("NO_CHANGES");
                status.setMessage("No changes needed");
            }

        } catch (Exception e) {
            log.error("Failed to update product {}: {}", localProduct.getExternalId(), e.getMessage());
            status.setStatus("ERROR");
            status.setMessage("Update failed: " + e.getMessage());
        }

        return status;
    }

    /**
     * Resolve conflicts between local and remote data
     */
    private List<HoroshopSyncConflict> resolveConflicts(List<Product> products,
                                                        HoroshopConfig config,
                                                        HoroshopSyncOptions options) {
        List<HoroshopSyncConflict> conflicts = new ArrayList<>();

        // This would contain logic to detect and resolve conflicts
        // For now, return empty list as conflicts are handled in the update methods

        return conflicts;
    }

    /**
     * Get products that need synchronization
     */
    private List<Product> getProductsForSync(DataSet dataset, HoroshopSyncOptions options) {
        List<Product> allProducts = new ArrayList<>(dataset.getProducts());

        return allProducts.stream()
                .filter(product -> shouldSyncProduct(product, options))
                .collect(Collectors.toList());
    }

    /**
     * Determine if product should be synchronized
     */
    private boolean shouldSyncProduct(Product product, HoroshopSyncOptions options) {
        // Always sync active products
        if (product.getStatus() != ProductStatus.ACTIVE) {
            return false;
        }

        // Check if product has external ID (required for sync)
        if (product.getExternalId() == null || product.getExternalId().trim().isEmpty()) {
            return false;
        }

        // Sync if last sync is old or never synced
        if (product.getLastSync() == null) {
            return true;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(options.getSyncIntervalHours());
        if (product.getLastSync().isBefore(cutoff)) {
            return true;
        }

        // Sync if product was updated recently
        if (product.getUpdatedAt() != null && product.getUpdatedAt().isAfter(product.getLastSync())) {
            return true;
        }

        // Sync if stock is low (urgent sync)
        if (product.getStock() != null && product.getStock() <= lowStockThreshold) {
            return true;
        }

        return false;
    }

    // Helper methods

    private String determinePresence(Product product) {
        if (!product.getAvailable()) {
            return "Нет в наличии";
        }

        if (product.getStock() != null && product.getStock() > 0) {
            return "В наличии";
        }

        return "Под заказ";
    }

    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    private HoroshopBatchResponse importBatchWithRetry(HoroshopConfig config,
                                                       List<HoroshopProduct> products) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HoroshopBatchImportRequest request = new HoroshopBatchImportRequest();
                request.setProducts(products);
                request.getSettings().setUpdateExisting(true);

                return horoshopClient.importProductBatch(config, products);

            } catch (Exception e) {
                lastException = e;
                log.warn("Import attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < maxRetries) {
                    Thread.sleep(2000 * attempt); // Exponential backoff
                }
            }
        }

        throw new RuntimeException("Import failed after " + maxRetries + " attempts", lastException);
    }

    private boolean isPriceEqual(BigDecimal price1, BigDecimal price2) {
        if (price1 == null && price2 == null) return true;
        if (price1 == null || price2 == null) return false;

        return Math.abs(price1.doubleValue() - price2.doubleValue()) <= priceTolerance;
    }

    private boolean hasRecentLocalStockChange(Product product) {
        // Check if stock was changed in the last hour
        return product.getUpdatedAt() != null &&
                product.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(1));
    }

    private boolean hasRecentLocalPriceChange(Product product) {
        // Check if price was changed in the last hour
        return product.getUpdatedAt() != null &&
                product.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(1));
    }

    private void updateSyncTimestamps(DataSet dataset, List<Product> products) {
        LocalDateTime now = LocalDateTime.now();

        // Update dataset last sync
        dataset.setLastSync(now);
        dataSetRepository.save(dataset);

        // Update product last sync timestamps
        products.forEach(product -> {
            product.setLastSync(now);
            productRepository.save(product);
        });
    }

    private void processBatchResult(HoroshopBatchResponse batchResponse, HoroshopBulkResult result) {
        if (batchResponse != null && batchResponse.getResponse() != null &&
                batchResponse.getResponse().getLog() != null) {

            batchResponse.getResponse().getLog().forEach(log -> {
                HoroshopSyncStatus status = new HoroshopSyncStatus();
                status.setProductArticle(log.getArticle());
                status.setTimestamp(LocalDateTime.now());

                boolean hasErrors = log.getInfo().stream()
                        .anyMatch(info -> info.getCode() > 20); // Error codes > 20

                status.setStatus(hasErrors ? "ERROR" : "SUCCESS");
                status.setMessage(hasErrors ? "Update failed" : "Updated successfully");

                addSyncResult(result, status);
            });
        }
    }

    private void addSyncResult(HoroshopBulkResult result, HoroshopSyncStatus status) {
        result.addResult(status);
    }

    private void addFailedResult(HoroshopBulkResult result, String productArticle, String message) {
        HoroshopSyncStatus status = new HoroshopSyncStatus();
        status.setProductArticle(productArticle);
        status.setStatus("ERROR");
        status.setMessage(message);
        status.setTimestamp(LocalDateTime.now());
        addSyncResult(result, status);
    }
}

// Supporting classes

enum ConflictResolution {
    LOCAL_WINS,
    REMOTE_WINS,
    HIGHEST_WINS,  // For stock
    LOWEST_WINS,   // For prices
    DETECT_ONLY
}

@lombok.Data
class HoroshopSyncOptions {
    private boolean syncStock = true;
    private boolean syncPrices = true;
    private boolean exportToHoroshop = true;
    private boolean importFromHoroshop = true;
    private ConflictResolution conflictResolution = ConflictResolution.REMOTE_WINS;
    private int syncIntervalHours = 6;
    private boolean urgentSync = false; // For low stock items
    private List<String> productFilter = new ArrayList<>(); // Filter by external IDs
}

@lombok.Data
class HoroshopSyncConflict {
    private String productArticle;
    private String field; // "stock", "price"
    private Object localValue;
    private Object remoteValue;
    private String resolution;
    private LocalDateTime detectedAt;
    private boolean resolved;
}
