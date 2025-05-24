package com.dropiq.engine.product.controller;

import com.dropiq.engine.product.dto.CreateDataSetRequest;
import com.dropiq.engine.product.dto.MergeDataSetRequest;
import com.dropiq.engine.product.dto.UpdateDataSetRequest;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.model.DataSetFilter;
import com.dropiq.engine.product.model.DataSetStatistics;
import com.dropiq.engine.product.model.SyncJobType;
import com.dropiq.engine.product.service.DataSetService;
import com.dropiq.engine.product.service.SyncSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DataSetController {

    private final DataSetService dataSetService;
    private SyncSchedulingService syncSchedulingService;

    /**
     * Create dataset from data sources
     */
    @PostMapping
    public ResponseEntity<DataSet> createDataset(@RequestBody CreateDataSetRequest request,
                                                 @RequestHeader("X-User-ID") String userId) {
        try {
            DataSet dataset = dataSetService.createDatasetFromSources(
                    request.getName(),
                    request.getDescription(),
                    userId,
                    request.getDataSources()
            );
            return ResponseEntity.ok(dataset);
        } catch (Exception e) {
            log.error("Error creating dataset: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create empty dataset
     */
    @PostMapping("/empty")
    public ResponseEntity<DataSet> createEmptyDataset(@RequestBody UpdateDataSetRequest request,
                                                      @RequestHeader("X-User-ID") String userId) {
        DataSet dataset = dataSetService.createEmptyDataset(
                request.getName(),
                request.getDescription(),
                userId
        );
        return ResponseEntity.ok(dataset);
    }

    /**
     * Get all user datasets
     */
    @GetMapping
    public ResponseEntity<List<DataSet>> getUserDatasets(@RequestHeader("X-User-ID") String userId,
                                                         @RequestParam(required = false) String search) {
        List<DataSet> datasets;
        if (search != null && !search.trim().isEmpty()) {
            datasets = dataSetService.searchDatasets(userId, search);
        } else {
            datasets = dataSetService.getUserDatasets(userId);
        }
        return ResponseEntity.ok(datasets);
    }

    /**
     * Get specific dataset
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataSet> getDataset(@PathVariable Long id,
                                              @RequestHeader("X-User-ID") String userId) {
        return dataSetService.getDataset(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update dataset
     */
    @PutMapping("/{id}")
    public ResponseEntity<DataSet> updateDataset(@PathVariable Long id,
                                                 @RequestBody UpdateDataSetRequest request,
                                                 @RequestHeader("X-User-ID") String userId) {
        try {
            DataSet dataset = dataSetService.updateDataset(id, userId, request.getName(), request.getDescription());
            return ResponseEntity.ok(dataset);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete dataset
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataset(@PathVariable Long id,
                                              @RequestHeader("X-User-ID") String userId) {
        try {
            dataSetService.deleteDataset(id, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Merge datasets
     */
    @PostMapping("/merge")
    public ResponseEntity<DataSet> mergeDatasets(@RequestBody MergeDataSetRequest request,
                                                 @RequestHeader("X-User-ID") String userId) {
        try {
            DataSet mergedDataset = dataSetService.mergeDatasets(
                    request.getDataset1Id(),
                    request.getDataset2Id(),
                    request.getNewName(),
                    userId
            );
            return ResponseEntity.ok(mergedDataset);
        } catch (Exception e) {
            log.error("Error merging datasets: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get dataset statistics
     */
    @GetMapping("/{id}/statistics")
    public ResponseEntity<DataSetStatistics> getDatasetStatistics(@PathVariable Long id,
                                                                  @RequestHeader("X-User-ID") String userId) {
        try {
            DataSetStatistics stats = dataSetService.getDatasetStatistics(id, userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Filter products in dataset
     */
    @PostMapping("/{id}/filter")
    public ResponseEntity<List<Product>> filterDatasetProducts(@PathVariable Long id,
                                                                      @RequestBody DataSetFilter filter,
                                                                      @RequestHeader("X-User-ID") String userId) {
        try {
            List<Product> filteredProducts = dataSetService.filterDatasetProducts(id, userId, filter);
            return ResponseEntity.ok(filteredProducts);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Remove products from dataset
     */
    @DeleteMapping("/{id}/products")
    public ResponseEntity<DataSet> removeProductsFromDataset(@PathVariable Long id,
                                                             @RequestBody List<Long> productIds,
                                                             @RequestHeader("X-User-ID") String userId) {
        try {
            DataSet dataset = dataSetService.removeProductsFromDataset(id, userId, productIds);
            return ResponseEntity.ok(dataset);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<String> syncDataset(@PathVariable Long id,
                                              @RequestHeader("X-User-ID") String userId) {
        try {
            log.info("Sync request received for dataset {} by user {}", id, userId);

            // Get the dataset
            Optional<DataSet> dataSetOpt = dataSetService.getDataset(id, userId);
            if (dataSetOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            DataSet dataSet = dataSetOpt.get();

            // Schedule sync job
            Long userIdLong = getUserIdFromUsername(userId); // Convert username to ID
            syncSchedulingService.scheduleSync(
                    userIdLong,
                    "DATASET",
                    id,
                    SyncJobType.DATASET_SYNC,
                    LocalDateTime.now(),
                    5 // Normal priority
            );

            log.info("Sync job scheduled for dataset: {}", dataSet.getName());
            return ResponseEntity.ok("Synchronization scheduled successfully");

        } catch (Exception e) {
            log.error("Error scheduling sync for dataset {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Failed to schedule synchronization: " + e.getMessage());
        }
    }

    /**
     * Get dataset sync status
     */
    @GetMapping("/{id}/sync/status")
    public ResponseEntity<String> getSyncStatus(@PathVariable Long id,
                                                @RequestHeader("X-User-ID") String userId) {
        try {
            Optional<DataSet> dataSetOpt = dataSetService.getDataset(id, userId);
            if (dataSetOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            DataSet dataSet = dataSetOpt.get();
            return ResponseEntity.ok(dataSet.getStatus().name());

        } catch (Exception e) {
            log.error("Error getting sync status for dataset {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body("Error getting sync status");
        }
    }

    /**
     * Convert username to user ID (simplified)
     */
    private Long getUserIdFromUsername(String username) {
        // In a real implementation, this would query the user service
        // For now, return a default ID
        return 1L;
    }
}