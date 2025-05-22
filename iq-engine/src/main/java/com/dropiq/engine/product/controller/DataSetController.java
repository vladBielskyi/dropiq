package com.dropiq.engine.product.controller;

import com.dropiq.engine.product.dto.CreateDataSetRequest;
import com.dropiq.engine.product.dto.MergeDataSetRequest;
import com.dropiq.engine.product.dto.UpdateDataSetRequest;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.model.DataSetFilter;
import com.dropiq.engine.product.model.DataSetStatistics;
import com.dropiq.engine.product.service.DataSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DataSetController {

    private final DataSetService dataSetService;

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
}