package com.dropiq.engine.product.service;

import com.dropiq.engine.integration.exp.model.DataSourceConfig;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import com.dropiq.engine.integration.exp.service.UnifiedProductService;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.model.*;
import com.dropiq.engine.product.repository.DataSetRepository;
import com.dropiq.engine.product.repository.ProductRepository;
import com.dropiq.engine.product.support.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DataSetService {

    private final DataSetRepository datasetRepository;
    private final UnifiedProductService productService;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    /**
     * Create a new dataset from data sources
     */
    public DataSet createDatasetFromSources(String name, String description, String createdBy,
                                            List<DataSourceConfig> configs) {
        log.info("Creating dataSet '{}' from {} sources for user '{}'", name, configs.size(), createdBy);

        DataSet dataSet = new DataSet();
        dataSet.setName(name);
        dataSet.setDescription(description);
        dataSet.setCreatedBy(createdBy);
        dataSet.setStatus(DataSetStatus.PROCESSING);

        // Save the dataSet first
        dataSet = datasetRepository.save(dataSet);

        try {
            // Fetch products from all configured sources
            List<UnifiedProduct> allProducts = productService.fetchProductsFromAllPlatforms(configs);

            // Add products to dataSet
            dataSet.addProducts(productMapper.toProductList(allProducts));

            // Update metadata
            dataSet.getMetadata().put("totalProducts", String.valueOf(allProducts.size()));
            dataSet.getMetadata().put("creationMethod", "fromSources");

            // Set status to active
            dataSet.setStatus(DataSetStatus.ACTIVE);

            dataSet = datasetRepository.save(dataSet);
            log.info("Dataset '{}' created successfully with {} products", name, allProducts.size());

            return dataSet;

        } catch (Exception e) {
            log.error("Error creating dataSet '{}': {}", name, e.getMessage());
            dataSet.setStatus(DataSetStatus.DRAFT);
            datasetRepository.save(dataSet);
            throw new RuntimeException("Failed to create dataSet: " + e.getMessage(), e);
        }
    }

    /**
     * Create empty dataset
     */
    public DataSet createEmptyDataset(String name, String description, String createdBy) {
        DataSet dataset = new DataSet();
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setCreatedBy(createdBy);
        dataset.setStatus(DataSetStatus.DRAFT);

        return datasetRepository.save(dataset);
    }

    /**
     * Get all datasets for a user
     */
    public List<DataSet> getUserDatasets(String createdBy) {
        return datasetRepository.findByCreatedBy(createdBy);
    }

    /**
     * Get dataset by ID (with ownership check)
     */
    public Optional<DataSet> getDataset(Long id, String createdBy) {
        return datasetRepository.findByIdAndCreatedBy(id, createdBy);
    }

    /**
     * Update dataset
     */
    public DataSet updateDataset(Long id, String createdBy, String name, String description) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(id, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        dataset.setName(name);
        dataset.setDescription(description);

        return datasetRepository.save(dataset);
    }

    /**
     * Delete dataset
     */
    public void deleteDataset(Long id, String createdBy) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(id, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        datasetRepository.delete(dataset);
        log.info("Dataset '{}' deleted by user '{}'", dataset.getName(), createdBy);
    }

    /**
     * Merge two datasets
     */
    public DataSet mergeDatasets(Long dataset1Id, Long dataset2Id, String newName, String createdBy) {
        DataSet dataset1 = datasetRepository.findByIdAndCreatedBy(dataset1Id, createdBy)
                .orElseThrow(() -> new RuntimeException("First dataset not found or access denied"));

        DataSet dataset2 = datasetRepository.findByIdAndCreatedBy(dataset2Id, createdBy)
                .orElseThrow(() -> new RuntimeException("Second dataset not found or access denied"));

        // Create new merged dataset
        DataSet mergedDataset = new DataSet();
        mergedDataset.setName(newName);
        mergedDataset.setDescription("Merged from: " + dataset1.getName() + " + " + dataset2.getName());
        mergedDataset.setCreatedBy(createdBy);
        mergedDataset.setStatus(DataSetStatus.PROCESSING);

        mergedDataset = datasetRepository.save(mergedDataset);

        // Merge products (avoiding duplicates)
        Set<UnifiedProduct> allProducts = new HashSet<>();
        allProducts.addAll(productMapper.toUnifiedProductList(dataset1.getProducts()));
        allProducts.addAll(productMapper.toUnifiedProductList(dataset2.getProducts()));

        mergedDataset.setProducts(productMapper.toProductSet(allProducts));

        // Merge source platforms
        mergedDataset.getSourcePlatforms().addAll(dataset1.getSourcePlatforms());
        mergedDataset.getSourcePlatforms().addAll(dataset2.getSourcePlatforms());

        // Update metadata
        mergedDataset.getMetadata().put("totalProducts", String.valueOf(allProducts.size()));
        mergedDataset.getMetadata().put("creationMethod", "merge");
        mergedDataset.getMetadata().put("mergedFrom", dataset1Id + "," + dataset2Id);

        mergedDataset.setStatus(DataSetStatus.ACTIVE);

        return datasetRepository.save(mergedDataset);
    }

    /**
     * Add products to dataset
     */
    public DataSet addProductsToDataset(Long datasetId, String createdBy, List<UnifiedProduct> products) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        dataset.addProducts(productMapper.toProductList(products));
        dataset.getMetadata().put("totalProducts", String.valueOf(dataset.getProductCount()));

        return datasetRepository.save(dataset);
    }

    /**
     * Remove products from dataset
     */
    public DataSet removeProductsFromDataset(Long datasetId, String createdBy, List<Long> productIds) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        Set<Product> productsToRemove = dataset.getProducts().stream()
                .filter(product -> productIds.contains(product.getId()))
                .collect(Collectors.toSet());

        productsToRemove.forEach(dataset::removeProduct);
        dataset.getMetadata().put("totalProducts", String.valueOf(dataset.getProductCount()));

        return datasetRepository.save(dataset);
    }

    /**
     * Filter products in dataset
     */
    public List<Product> filterDatasetProducts(Long datasetId, String createdBy, DataSetFilter filter) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        return dataset.getProducts().stream()
                .filter(product -> matchesFilter(product, filter))
                .collect(Collectors.toList());
    }

    /**
     * Get dataset statistics
     */
    public DataSetStatistics getDatasetStatistics(Long datasetId, String createdBy) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        DataSetStatistics stats = new DataSetStatistics();
        stats.setDatasetId(datasetId);
        stats.setDatasetName(dataset.getName());
        stats.setTotalProducts(dataset.getProductCount());

        // Calculate statistics
        Set<Product> products = dataset.getProducts();

        stats.setAvailableProducts((int) products.stream().mapToLong(p -> p.getAvailable() ? 1 : 0).sum());
        stats.setUnavailableProducts(stats.getTotalProducts() - stats.getAvailableProducts());

        // Group by source platform
        Map<SourceType, Long> platformStats = products.stream()
                .collect(Collectors.groupingBy(Product::getSourceType, Collectors.counting()));
        stats.setProductsByPlatform(platformStats);

        // Group by category
        Map<String, Long> categoryStats = products.stream()
                .filter(p -> p.getExternalCategoryId() != null)
                .collect(Collectors.groupingBy(Product::getExternalCategoryId, Collectors.counting()));
        stats.setProductsByCategory(categoryStats);

        // Price statistics
        OptionalDouble avgPrice = products.stream()
                .filter(p -> p.getSellingPrice() != null && p.getSellingPrice().intValue() > 0)
                .mapToDouble(p -> p.getSellingPrice().doubleValue())
                .average();
        stats.setAveragePrice(avgPrice.orElse(0.0));

        return stats;
    }

    /**
     * Search datasets
     */
    public List<DataSet> searchDatasets(String createdBy, String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getUserDatasets(createdBy);
        }

        return datasetRepository.findByCreatedByAndNameContaining(createdBy, searchTerm.trim());
    }

    // Add these missing methods to DataSetService.java

    /**
     * Find all datasets with auto-sync enabled
     */
    public List<DataSet> findAutoSyncDatasets() {
        return datasetRepository.findAll().stream()
                .filter(dataset -> dataset.getAutoSync() != null && dataset.getAutoSync())
                .filter(dataset -> dataset.getStatus() == DataSetStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    /**
     * Sync dataset from its original sources
     */
    @Transactional
    public DataSet syncDatasetFromSource(DataSet dataset) {
        log.info("Syncing dataset '{}' from sources", dataset.getName());

        try {
            dataset.setStatus(DataSetStatus.PROCESSING);
            dataset = datasetRepository.save(dataset);

            // Get source configurations from dataset metadata
            List<DataSourceConfig> configs = getDataSourceConfigsFromDataset(dataset);

            if (configs.isEmpty()) {
                throw new RuntimeException("No source configurations found for dataset");
            }

            // Fetch fresh products from sources
            List<UnifiedProduct> freshProducts = productService.fetchProductsFromAllPlatforms(configs);

            // Update existing products and add new ones
            int updatedCount = 0;
            int addedCount = 0;

            for (UnifiedProduct unifiedProduct : freshProducts) {
                Optional<Product> existingProduct = dataset.getProducts().stream()
                        .filter(p -> p.getExternalId().equals(unifiedProduct.getExternalId())
                                && p.getSourceType().equals(unifiedProduct.getSourceType()))
                        .findFirst();

                if (existingProduct.isPresent()) {
                    // Update existing product
                    productMapper.syncStockAndAvailable(existingProduct.get(), unifiedProduct);
                    updatedCount++;
                } else {
                    // Add new product
                    Product newProduct = productMapper.toProduct(unifiedProduct);
                    dataset.addProduct(newProduct);
                    addedCount++;
                }
            }

            // Update sync metadata
            dataset.setLastSync(LocalDateTime.now());
            dataset.setSyncCount(dataset.getSyncCount() + 1);
            dataset.setStatus(DataSetStatus.ACTIVE);

            dataset.getMetadata().put("lastSyncProductsAdded", String.valueOf(addedCount));
            dataset.getMetadata().put("lastSyncProductsUpdated", String.valueOf(updatedCount));
            dataset.getMetadata().put("lastSyncTime", LocalDateTime.now().toString());

            dataset = datasetRepository.save(dataset);
            log.info("Dataset '{}' synced successfully. Added: {}, Updated: {}",
                    dataset.getName(), addedCount, updatedCount);

            return dataset;

        } catch (Exception e) {
            log.error("Error syncing dataset '{}': {}", dataset.getName(), e.getMessage());
            dataset.setStatus(DataSetStatus.ERROR);
            dataset.setLastErrorMessage(e.getMessage());
            dataset.setErrorCount(dataset.getErrorCount() + 1);
            datasetRepository.save(dataset);
            throw new RuntimeException("Failed to sync dataset: " + e.getMessage(), e);
        }
    }

    /**
     * Update a single product from its source
     */
    @Transactional
    public void updateProductFromSource(Long productId) {
        // Find the product
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }

        Product product = productOpt.get();

        // Create a temporary data source config
        DataSourceConfig config = new DataSourceConfig();
        config.setPlatformType(product.getSourceType());
        config.setUrl(product.getSourceUrl());
        config.setHeaders(Map.of());

        try {
            // Fetch fresh product data
            List<UnifiedProduct> freshProducts = productService.fetchProductsFromPlatform(
                    product.getSourceType(),
                    product.getSourceUrl(),
                    Map.of()
            );

            // Find the specific product
            Optional<UnifiedProduct> freshProduct = freshProducts.stream()
                    .filter(p -> p.getExternalId().equals(product.getExternalId()))
                    .findFirst();

            if (freshProduct.isPresent()) {
                productMapper.updateProduct(product, freshProduct.get());
                product.setLastSync(LocalDateTime.now());
                productRepository.save(product);
                log.info("Product '{}' updated successfully", product.getName());
            } else {
                log.warn("Product '{}' not found in source data", product.getName());
            }

        } catch (Exception e) {
            log.error("Failed to update product '{}': {}", product.getName(), e.getMessage());
            throw new RuntimeException("Failed to update product: " + e.getMessage(), e);
        }
    }

    /**
     * Enable/disable auto-sync for dataset
     */
    @Transactional
    public DataSet toggleAutoSync(Long datasetId, String createdBy, boolean enabled, Integer intervalHours) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        dataset.setAutoSync(enabled);
        if (intervalHours != null && intervalHours > 0) {
            dataset.setSyncIntervalHours(intervalHours);
        }

        log.info("Auto-sync {} for dataset '{}' with interval {} hours",
                enabled ? "enabled" : "disabled", dataset.getName(), dataset.getSyncIntervalHours());

        return datasetRepository.save(dataset);
    }

    /**
     * Apply bulk price markup to dataset products
     */
    @Transactional
    public DataSet applyBulkPriceMarkup(Long datasetId, String createdBy, BigDecimal markupPercentage,
                                        List<Long> productIds) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        Set<Product> productsToUpdate = dataset.getProducts().stream()
                .filter(product -> productIds == null || productIds.isEmpty() || productIds.contains(product.getId()))
                .collect(Collectors.toSet());

        int updatedCount = 0;
        for (Product product : productsToUpdate) {
            product.setMarkupPercentage(markupPercentage);
            // The @PreUpdate method will automatically calculate selling price
            updatedCount++;
        }

        dataset.setDefaultMarkup(markupPercentage);
        dataset = datasetRepository.save(dataset);

        log.info("Applied {}% markup to {} products in dataset '{}'",
                markupPercentage, updatedCount, dataset.getName());

        return dataset;
    }

    /**
     * Activate/deactivate products in bulk
     */
    @Transactional
    public DataSet bulkUpdateProductStatus(Long datasetId, String createdBy, List<Long> productIds,
                                           ProductStatus status) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        Set<Product> productsToUpdate = dataset.getProducts().stream()
                .filter(product -> productIds.contains(product.getId()))
                .collect(Collectors.toSet());

        productsToUpdate.forEach(product -> product.setStatus(status));

        dataset = datasetRepository.save(dataset);

        log.info("Updated status to {} for {} products in dataset '{}'",
                status, productsToUpdate.size(), dataset.getName());

        return dataset;
    }

    /**
     * Get dataset products with pagination and filtering
     */
    public Page<Product> getDatasetProducts(Long datasetId, String createdBy, DataSetFilter filter,
                                            Pageable pageable) {
        DataSet dataset = datasetRepository.findByIdAndCreatedBy(datasetId, createdBy)
                .orElseThrow(() -> new RuntimeException("Dataset not found or access denied"));

        List<Product> filteredProducts = dataset.getProducts().stream()
                .filter(product -> matchesFilter(product, filter))
                .collect(Collectors.toList());

        // Manual pagination since we're filtering in memory
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredProducts.size());

        List<Product> pageContent = filteredProducts.subList(start, end);

        return new PageImpl<>(pageContent, pageable, filteredProducts.size());
    }

    /**
     * Extract data source configurations from dataset metadata
     */
    private List<DataSourceConfig> getDataSourceConfigsFromDataset(DataSet dataset) {
        // This would typically be stored in metadata when dataset is created
        // For now, we'll create a simple implementation
        List<DataSourceConfig> configs = new ArrayList<>();

        for (SourceType sourceType : dataset.getSourcePlatforms()) {
            DataSourceConfig config = new DataSourceConfig();
            config.setPlatformType(sourceType);

            // URLs would be stored in metadata in a real implementation
            String metadataKey = sourceType.name().toLowerCase() + "_url";
            String url = dataset.getMetadata().get(metadataKey);
            if (url != null) {
                config.setUrl(url);
                config.setHeaders(Map.of());
                configs.add(config);
            }
        }

        return configs;
    }

    // Helper method to check if product matches filter
    private boolean matchesFilter(Product product, DataSetFilter filter) {
        if (filter.getSourceTypes() != null && !filter.getSourceTypes().isEmpty()) {
            if (!filter.getSourceTypes().contains(product.getSourceType())) {
                return false;
            }
        }

        if (filter.getMinPrice() != null && (product.getSellingPrice() == null || product.getSellingPrice().intValue()
                < filter.getMinPrice())) {
            return false;
        }

        if (filter.getMaxPrice() != null && (product.getSellingPrice() == null || product.getSellingPrice().intValue()
                > filter.getMaxPrice())) {
            return false;
        }

        if (filter.getAvailableOnly() != null && filter.getAvailableOnly() && !product.getAvailable()) {
            return false;
        }

        if (filter.getCategoryIds() != null && !filter.getCategoryIds().isEmpty()) {
            if (product.getExternalCategoryId() == null || !filter.getCategoryIds().contains(product.getExternalCategoryId())) {
                return false;
            }
        }

        return true;
    }
}
