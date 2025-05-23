package com.dropiq.admin.service;

import com.dropiq.admin.entity.DataSource;
import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.DataSourceType;
import com.dropiq.admin.model.DatasetStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EngineIntegrationService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${dropiq.engine.base-url:http://localhost:8081}")
    private String engineBaseUrl;

    /**
     * Create dataset in IQ-Engine and sync with admin panel
     */
    public DataSet createDatasetFromSource(DataSource dataSource, String datasetName, String description) {
        log.info("Creating dataset '{}' from data source '{}'", datasetName, dataSource.getName());

        try {
            // Prepare request for IQ-Engine
            Map<String, Object> createRequest = new HashMap<>();
            createRequest.put("name", datasetName);
            createRequest.put("description", description);
            createRequest.put("dataSources", Arrays.asList(convertToEngineDataSource(dataSource)));

            // Call IQ-Engine API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-ID", "admin"); // TODO: Get from UserSession

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(createRequest, headers);

            String url = engineBaseUrl + "/api/datasets";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Create corresponding dataset in admin panel
                DataSet dataset = new DataSet();
                dataset.setName(datasetName);
                dataset.setDescription(description);
                dataset.setDataSource(dataSource);
                dataset.setStatus(DatasetStatus.PROCESSING);
                dataset.setCreatedBy("admin"); // TODO: Get from UserSession

                // Save admin dataset
                dataset = dataManager.save(dataset);

                // Update with engine data
                Map<String, Object> engineDataset = response.getBody();
                updateDatasetFromEngineResponse(dataset, engineDataset);

                log.info("Dataset '{}' created successfully with ID: {}", datasetName, dataset.getId());
                return dataset;

            } else {
                throw new RuntimeException("Failed to create dataset in IQ-Engine: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error creating dataset from data source: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create dataset: " + e.getMessage(), e);
        }
    }

    /**
     * Sync dataset with IQ-Engine
     */
    public void syncDataset(DataSet dataset) {
        log.info("Syncing dataset '{}'", dataset.getName());

        try {
            dataset.setStatus(DatasetStatus.PROCESSING);
            dataset.setLastSync(LocalDateTime.now());
            dataManager.save(dataset);

            // Trigger sync in IQ-Engine if it has a corresponding dataset
            if (dataset.getMetadata().containsKey("engineDatasetId")) {
                String engineDatasetId = dataset.getMetadata().get("engineDatasetId");
                String url = engineBaseUrl + "/api/datasets/" + engineDatasetId + "/sync";

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-User-ID", "admin");

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    dataset.setStatus(DatasetStatus.ACTIVE);
                    dataset.setSyncCount(dataset.getSyncCount() + 1);
                } else {
                    dataset.setStatus(DatasetStatus.ERROR);
                    dataset.setLastErrorMessage("Sync failed with status: " + response.getStatusCode());
                }
            } else {
                // Create new dataset in engine if not exists
                createDatasetFromSource(dataset.getDataSource(), dataset.getName(), dataset.getDescription());
            }

        } catch (Exception e) {
            log.error("Error syncing dataset: {}", e.getMessage(), e);
            dataset.setStatus(DatasetStatus.ERROR);
            dataset.setLastErrorMessage(e.getMessage());
            dataset.setErrorCount(dataset.getErrorCount() + 1);
        } finally {
            dataManager.save(dataset);
        }
    }

    /**
     * Get dataset statistics from IQ-Engine
     */
    public Map<String, Object> getDatasetStatistics(DataSet dataset) {
        try {
            String engineDatasetId = dataset.getMetadata().get("engineDatasetId");
            if (engineDatasetId == null) {
                return Collections.emptyMap();
            }

            String url = engineBaseUrl + "/api/datasets/" + engineDatasetId + "/statistics";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-ID", "admin");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

        } catch (Exception e) {
            log.error("Error getting dataset statistics: {}", e.getMessage(), e);
        }

        return Collections.emptyMap();
    }

    /**
     * Trigger AI optimization for dataset
     */
    public void optimizeDataset(DataSet dataset) {
        log.info("Starting AI optimization for dataset '{}'", dataset.getName());

        try {
            dataset.setStatus(DatasetStatus.OPTIMIZING);
            dataManager.save(dataset);

            // Call AI optimization service (placeholder for now)
            Map<String, Object> optimizationRequest = new HashMap<>();
            optimizationRequest.put("datasetId", dataset.getId());
            optimizationRequest.put("enableSeoOptimization", dataset.getSeoOptimizationEnabled());
            optimizationRequest.put("enableImageAnalysis", dataset.getImageAnalysisEnabled());
            optimizationRequest.put("enableTrendAnalysis", dataset.getTrendAnalysisEnabled());

            // TODO: Implement actual AI optimization API call
            String url = engineBaseUrl + "/api/ai/optimize-dataset";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-ID", "admin");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(optimizationRequest, headers);

            // For now, simulate optimization completion
            Thread.sleep(2000); // Simulate processing time

            dataset.setStatus(DatasetStatus.READY_FOR_EXPORT);
            dataset.setOptimizedProducts(dataset.getActiveProducts());

            log.info("AI optimization completed for dataset '{}'", dataset.getName());

        } catch (Exception e) {
            log.error("Error optimizing dataset: {}", e.getMessage(), e);
            dataset.setStatus(DatasetStatus.ERROR);
            dataset.setLastErrorMessage("Optimization failed: " + e.getMessage());
        } finally {
            dataManager.save(dataset);
        }
    }

    /**
     * Export dataset to target platform
     */
    public void exportDataset(DataSet dataset, String targetPlatform) {
        log.info("Exporting dataset '{}' to platform '{}'", dataset.getName(), targetPlatform);

        try {
            Map<String, Object> exportRequest = new HashMap<>();
            exportRequest.put("datasetId", dataset.getId());
            exportRequest.put("targetPlatform", targetPlatform);
            exportRequest.put("includeInactiveProducts", false);
            exportRequest.put("applyMarkup", dataset.getDefaultMarkup());

            // TODO: Implement actual export API call
            String url = engineBaseUrl + "/api/export/" + targetPlatform.toLowerCase();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-ID", "admin");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(exportRequest, headers);

            // For now, simulate export completion
            Thread.sleep(1000);

            dataset.setStatus(DatasetStatus.EXPORTED);
            dataset.setExportedProducts(dataset.getActiveProducts());
            dataset.getMetadata().put("lastExportPlatform", targetPlatform);
            dataset.getMetadata().put("lastExportDate", LocalDateTime.now().toString());

            log.info("Export completed for dataset '{}' to '{}'", dataset.getName(), targetPlatform);

        } catch (Exception e) {
            log.error("Error exporting dataset: {}", e.getMessage(), e);
            dataset.setStatus(DatasetStatus.ERROR);
            dataset.setLastErrorMessage("Export failed: " + e.getMessage());
        } finally {
            dataManager.save(dataset);
        }
    }

    /**
     * Fetch products from IQ-Engine for a dataset
     */
    public List<Product> fetchDatasetProducts(DataSet dataset, int page, int size) {
        try {
            String engineDatasetId = dataset.getMetadata().get("engineDatasetId");
            if (engineDatasetId == null) {
                return Collections.emptyList();
            }

            String url = engineBaseUrl + "/api/datasets/" + engineDatasetId + "/products?page=" + page + "&size=" + size;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-ID", "admin");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> productData = (List<Map<String, Object>>) response.getBody().get("products");
                return productData.stream()
                        .map(this::convertEngineProductToAdmin)
                        .collect(Collectors.toList());
            }

        } catch (Exception e) {
            log.error("Error fetching dataset products: {}", e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    /**
     * Convert admin DataSource to engine DataSourceConfig
     */
    private Map<String, Object> convertToEngineDataSource(DataSource dataSource) {
        Map<String, Object> engineDataSource = new HashMap<>();

        // Map DataSourceType to engine SourceType
        String sourceType = mapDataSourceTypeToEngine(dataSource.getSourceType());
        engineDataSource.put("platformType", sourceType);
        engineDataSource.put("url", dataSource.getUrl());
        engineDataSource.put("headers", dataSource.getHeaders());

        return engineDataSource;
    }

    /**
     * Convert engine product data to admin Product entity
     */
    private Product convertEngineProductToAdmin(Map<String, Object> engineProduct) {
        Product product = new Product();

        product.setExternalId((String) engineProduct.get("externalId"));
        product.setGroupId((String) engineProduct.get("groupId"));
        product.setName((String) engineProduct.get("name"));
        product.setDescription((String) engineProduct.get("description"));

        if (engineProduct.get("price") != null) {
            product.setOriginalPrice(BigDecimal.valueOf(((Number) engineProduct.get("price")).doubleValue()));
        }

        if (engineProduct.get("stock") != null) {
            product.setStock((Integer) engineProduct.get("stock"));
        }

        product.setAvailable((Boolean) engineProduct.getOrDefault("available", false));
        product.setExternalCategoryId((String) engineProduct.get("externalCategoryId"));
        product.setExternalCategoryName((String) engineProduct.get("externalCategoryName"));
        product.setSourceType((String) engineProduct.get("sourceType"));
        product.setSourceUrl((String) engineProduct.get("sourceUrl"));

        // Set image URLs
        List<String> imageUrls = (List<String>) engineProduct.get("imageUrls");
        if (imageUrls != null && !imageUrls.isEmpty()) {
            product.setImageUrls(imageUrls);
            product.setPrimaryImageUrl(imageUrls.get(0));
        }

        // Set attributes
        Map<String, String> attributes = (Map<String, String>) engineProduct.get("attributes");
        if (attributes != null) {
            product.setAttributes(attributes);
        }

        return product;
    }

    /**
     * Update admin dataset with data from engine response
     */
    private void updateDatasetFromEngineResponse(DataSet dataset, Map<String, Object> engineDataset) {
        if (engineDataset.get("id") != null) {
            dataset.getMetadata().put("engineDatasetId", engineDataset.get("id").toString());
        }

        if (engineDataset.get("totalProducts") != null) {
            dataset.setTotalProducts((Integer) engineDataset.get("totalProducts"));
        }

        if (engineDataset.get("activeProducts") != null) {
            dataset.setActiveProducts((Integer) engineDataset.get("activeProducts"));
        }

        dataset.setStatus(DatasetStatus.ACTIVE);
        dataManager.save(dataset);
    }

    /**
     * Map admin DataSourceType to engine SourceType string
     */
    private String mapDataSourceTypeToEngine(DataSourceType dataSourceType) {
        switch (dataSourceType) {
            case MYDROP:
                return "MYDROP";
            case EASYDROP:
                return "EASYDROP";
            case CSV_FILE:
                return "CSV_FILE";
            case XML_FILE:
                return "XML_FILE";
            case CUSTOM_API:
                return "CUSTOM_API";
            case MANUAL_ENTRY:
                return "MANUAL_ENTRY";
            default:
                return "CUSTOM_API";
        }
    }
}
