package com.dropiq.engine.integration.imp.horoshop;

import com.dropiq.engine.integration.imp.horoshop.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Horoshop API Client for product and category management
 */
@Slf4j
@Component
public class HoroshopApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // API Endpoints
    private static final String AUTH_ENDPOINT = "/auth";
    private static final String CATALOG_IMPORT_ENDPOINT = "/catalog/import/";
    private static final String CATALOG_EXPORT_ENDPOINT = "/catalog/export/";
    private static final String PAGES_EXPORT_ENDPOINT = "/pages/export/"; // For categories
    private static final String PRODUCT_UPDATE_ENDPOINT = "/catalog/update/";
    private static final String PRODUCT_DELETE_ENDPOINT = "/catalog/delete/";
    private static final String CATEGORIES_ENDPOINT = "/categories/";

    public HoroshopApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Authenticate and get API token
     */
    public String authenticate(HoroshopConfig config) {
        try {
            log.info("Authenticating with Horoshop API: {}", config.getDomain());

            Map<String, String> authData = new HashMap<>();
            authData.put("login", config.getUsername());
            authData.put("password", config.getPassword());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(authData, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    config.getApiUrl() + AUTH_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> authResponse = objectMapper.readValue(response.getBody(), Map.class);
                String token = ((LinkedHashMap) authResponse.get("response")).get("token").toString();
                log.info("Successfully authenticated with Horoshop");
                return token;
            } else {
                throw new RuntimeException("Authentication failed: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Authentication failed: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate with Horoshop: " + e.getMessage(), e);
        }
    }

    /**
     * Import products in batch
     */
    public HoroshopBulkResult importProducts(HoroshopConfig config, List<HoroshopProduct> products) {
        log.info("Starting batch import of {} products to Horoshop", products.size());

        HoroshopBulkResult result = new HoroshopBulkResult();
        result.setStartTime(LocalDateTime.now());

        try {
            // Split into batches
            List<List<HoroshopProduct>> batches = splitIntoBatches(products, config.getBatchSize());

            for (int i = 0; i < batches.size(); i++) {
                List<HoroshopProduct> batch = batches.get(i);
                log.info("Processing batch {}/{} with {} products", i + 1, batches.size(), batch.size());

                try {
                    HoroshopBatchResponse batchResult = importProductBatch(config, batch);
                    processBatchResult(batchResult, result);

                    // Rate limiting - wait between batches
                    if (i < batches.size() - 1) {
                        Thread.sleep(2000); // 2 second delay between batches
                    }

                } catch (Exception e) {
                    log.error("Batch {} failed: {}", i + 1, e.getMessage());
                    // Mark all products in batch as failed
                    batch.forEach(product -> {
                        HoroshopSyncStatus status = new HoroshopSyncStatus();
                        status.setProductArticle(product.getArticle());
                        status.setStatus("ERROR");
                        status.setMessage("Batch failed: " + e.getMessage());
                        status.setTimestamp(LocalDateTime.now());
                        result.addResult(status);
                    });
                }
            }

        } catch (Exception e) {
            log.error("Import process failed: {}", e.getMessage());
            throw new RuntimeException("Failed to import products: " + e.getMessage(), e);
        }

        result.setEndTime(LocalDateTime.now());
        log.info("Import completed. Success: {}, Errors: {}, Success rate: {:.1f}%",
                result.getTotalSuccess(), result.getTotalErrors(), result.getSuccessRate());

        return result;
    }

    /**
     * Import single batch of products - FIXED: Token now passed correctly in body
     */
    public HoroshopBatchResponse importProductBatch(HoroshopConfig config, List<HoroshopProduct> products) {
        try {
            // Token should be passed in the request body, not headers
            HoroshopBatchImportRequest request = new HoroshopBatchImportRequest();
            request.setProducts(products);
            request.setToken(config.getToken()); // Token in body

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<HoroshopBatchImportRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<HoroshopBatchResponse> response = restTemplate.exchange(
                    config.getApiUrl() + CATALOG_IMPORT_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    HoroshopBatchResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new RuntimeException("Import failed with status: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to call Horoshop API: " + e.getMessage(), e);
        }
    }

    /**
     * Update existing product - FIXED: Token now passed correctly in body
     */
    public HoroshopSyncStatus updateProduct(HoroshopConfig config, HoroshopProduct product) {
        log.debug("Updating product: {}", product.getArticle());

        HoroshopSyncStatus status = new HoroshopSyncStatus();
        status.setProductArticle(product.getArticle());
        status.setTimestamp(LocalDateTime.now());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create single-product batch with token in body
            HoroshopBatchImportRequest request = new HoroshopBatchImportRequest();
            request.setProducts(List.of(product));
            request.setToken(config.getToken()); // Token in body
            request.getSettings().setUpdateExisting(true);

            HttpEntity<HoroshopBatchImportRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<HoroshopBatchResponse> response = restTemplate.exchange(
                    config.getApiUrl() + CATALOG_IMPORT_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    HoroshopBatchResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                HoroshopBatchResponse batchResponse = response.getBody();

                if ("OK".equals(batchResponse.getStatus()) || "WARNING".equals(batchResponse.getStatus())) {
                    status.setStatus("SUCCESS");
                    status.setMessage("Product updated successfully");

                    // Check for warnings
                    if (batchResponse.getResponse() != null && batchResponse.getResponse().getLog() != null) {
                        batchResponse.getResponse().getLog().forEach(log -> {
                            if (product.getArticle().equals(log.getArticle())) {
                                log.getInfo().forEach(info -> {
                                    if (info.getCode() != 0) { // 0 = success
                                        status.getWarnings().add(info.getMessage());
                                    }
                                });
                            }
                        });
                    }
                } else {
                    status.setStatus("ERROR");
                    status.setMessage("Update failed: " + batchResponse.getStatus());
                }
            } else {
                status.setStatus("ERROR");
                status.setMessage("Invalid response from API");
            }

        } catch (Exception e) {
            log.error("Failed to update product {}: {}", product.getArticle(), e.getMessage());
            status.setStatus("ERROR");
            status.setMessage("Update failed: " + e.getMessage());
        }

        return status;
    }

    /**
     * Delete product by article/SKU - FIXED: Token now passed correctly in body
     */
    public HoroshopSyncStatus deleteProduct(HoroshopConfig config, String article) {
        log.debug("Deleting product: {}", article);

        HoroshopSyncStatus status = new HoroshopSyncStatus();
        status.setProductArticle(article);
        status.setTimestamp(LocalDateTime.now());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Token should be in body
            Map<String, String> deleteRequest = new HashMap<>();
            deleteRequest.put("article", article);
            deleteRequest.put("token", config.getToken()); // Token in body

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(deleteRequest, headers);

            ResponseEntity<HoroshopApiResponse> response = restTemplate.exchange(
                    config.getApiUrl() + PRODUCT_DELETE_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    HoroshopApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                HoroshopApiResponse apiResponse = response.getBody();

                if ("OK".equals(apiResponse.getStatus())) {
                    status.setStatus("SUCCESS");
                    status.setMessage("Product deleted successfully");
                } else {
                    status.setStatus("ERROR");
                    status.setMessage("Delete failed: " + apiResponse.getStatus());
                }
            } else {
                status.setStatus("ERROR");
                status.setMessage("Invalid response from API");
            }

        } catch (Exception e) {
            log.error("Failed to delete product {}: {}", article, e.getMessage());
            status.setStatus("ERROR");
            status.setMessage("Delete failed: " + e.getMessage());
        }

        return status;
    }

    /**
     * Get all categories from Horoshop - FIXED: Token now passed correctly in body or query param
     */
    public List<HoroshopCategory> getCategories(HoroshopConfig config) {
        log.info("Fetching categories from Horoshop");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // For GET requests, token might need to be in query params or body
            // Using query param approach for GET requests
            String url = config.getApiUrl() + PAGES_EXPORT_ENDPOINT + "?token=" + config.getToken();

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // Parse categories from response
                return parseCategoriesFromResponse(response.getBody());
            } else {
                throw new RuntimeException("Failed to fetch categories: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to fetch categories: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch categories: " + e.getMessage(), e);
        }
    }

    /**
     * Create or update category - FIXED: Token now passed correctly in body
     */
    public HoroshopCategory createCategory(HoroshopConfig config, HoroshopCategory category) {
        log.debug("Creating/updating category: {}", category.getName());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create request body with token
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("category", category);
            requestBody.put("token", config.getToken()); // Token in body

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<HoroshopCategory> response = restTemplate.exchange(
                    config.getApiUrl() + CATEGORIES_ENDPOINT,
                    HttpMethod.POST,
                    entity,
                    HoroshopCategory.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to create category: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to create category: {}", e.getMessage());
            throw new RuntimeException("Failed to create category: " + e.getMessage(), e);
        }
    }

    /**
     * Export products from Horoshop - FIXED: Token now passed correctly in query params
     */
    public List<HoroshopProduct> exportProducts(HoroshopConfig config, HoroshopExportSettings settings) {
        log.info("Exporting products from Horoshop");

        try {
            HttpHeaders headers = new HttpHeaders();

            // Build query parameters including token
            String queryParams = buildExportQueryParams(settings);
            String url = config.getApiUrl() + CATALOG_EXPORT_ENDPOINT + "?token=" + config.getToken() + "&" + queryParams;

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseProductsFromResponse(response.getBody());
            } else {
                throw new RuntimeException("Failed to export products: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to export products: {}", e.getMessage());
            throw new RuntimeException("Failed to export products: " + e.getMessage(), e);
        }
    }

    /**
     * Async bulk update products
     */
    public CompletableFuture<HoroshopBulkResult> updateProductsAsync(HoroshopConfig config,
                                                                     List<HoroshopProduct> products) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting async bulk update of {} products", products.size());

            HoroshopBulkResult result = new HoroshopBulkResult();
            result.setStartTime(LocalDateTime.now());

            for (HoroshopProduct product : products) {
                HoroshopSyncStatus status = updateProduct(config, product);
                result.addResult(status);

                // Rate limiting
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            result.setEndTime(LocalDateTime.now());
            return result;
        });
    }

    /**
     * Test API connection - FIXED: Token now passed correctly in query params
     */
    public boolean testConnection(HoroshopConfig config) {
        try {
            log.info("Testing connection to Horoshop API");

            HttpHeaders headers = new HttpHeaders();
            String url = config.getApiUrl() + PAGES_EXPORT_ENDPOINT + "?token=" + config.getToken() + "&limit=1";

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            boolean isConnected = response.getStatusCode().is2xxSuccessful();
            log.info("Connection test result: {}", isConnected ? "SUCCESS" : "FAILED");
            return isConnected;

        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    // Helper methods

    /**
     * REMOVED: createAuthHeaders method as token is now passed in body/query params
     * Token authentication is handled differently for each endpoint type
     */

    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    private void processBatchResult(HoroshopBatchResponse batchResponse, HoroshopBulkResult result) {
        if (batchResponse == null || batchResponse.getResponse() == null) {
            return;
        }

        if (batchResponse.getResponse().getLog() != null) {
            batchResponse.getResponse().getLog().forEach(productLog -> {
                HoroshopSyncStatus status = new HoroshopSyncStatus();
                status.setProductArticle(productLog.getArticle());
                status.setTimestamp(LocalDateTime.now());

                boolean hasErrors = false;
                List<String> warnings = new ArrayList<>();

                if (productLog.getInfo() != null) {
                    for (HoroshopLogEntry entry : productLog.getInfo()) {
                        if (entry.getCode() == 0) {
                            // Success message
                            continue;
                        } else if (entry.getCode() > 20) {
                            // Error codes are typically > 20
                            hasErrors = true;
                            status.setMessage(entry.getMessage());
                        } else {
                            // Warning
                            warnings.add(entry.getMessage());
                        }
                    }
                }

                status.setStatus(hasErrors ? "ERROR" : "SUCCESS");
                status.setWarnings(warnings);

                if (!hasErrors && status.getMessage() == null) {
                    status.setMessage("Product processed successfully");
                }

                result.addResult(status);
            });
        }
    }

    private List<HoroshopCategory> parseCategoriesFromResponse(String response) {
        try {
            // This would need to be implemented based on the actual response format
            // Placeholder implementation
            List<HoroshopCategory> categories = new ArrayList<>();

            // Parse JSON or XML response to extract categories
            // Implementation depends on Horoshop's actual response format

            return categories;
        } catch (Exception e) {
            log.error("Failed to parse categories response: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<HoroshopProduct> parseProductsFromResponse(String response) {
        try {
            // This would need to be implemented based on the actual response format
            // Placeholder implementation
            List<HoroshopProduct> products = new ArrayList<>();

            // Parse JSON or XML response to extract products
            // Implementation depends on Horoshop's actual response format

            return products;
        } catch (Exception e) {
            log.error("Failed to parse products response: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String buildExportQueryParams(HoroshopExportSettings settings) {
        List<String> params = new ArrayList<>();

        if (!settings.getCategoryIds().isEmpty()) {
            params.add("categories=" + String.join(",",
                    settings.getCategoryIds().stream().map(String::valueOf).toList()));
        }

        if (!settings.getIncludeImages()) {
            params.add("exclude_images=1");
        }

        if (!settings.getIncludePrices()) {
            params.add("exclude_prices=1");
        }

        params.add("format=json");

        return String.join("&", params);
    }
}