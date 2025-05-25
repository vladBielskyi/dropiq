package com.dropiq.engine.integration.imp.horoshop.controller;

import com.dropiq.engine.integration.imp.horoshop.model.*;
import com.dropiq.engine.integration.imp.horoshop.service.HoroshopIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for Horoshop integration operations
 */
@Slf4j
@RestController
@RequestMapping("/api/horoshop")
@RequiredArgsConstructor
public class HoroshopIntegrationController {

    private final HoroshopIntegrationService horoshopService;

    /**
     * Test Horoshop API connection
     */
    @PostMapping("/test-connection")
    public ResponseEntity<HoroshopConfigValidation> testConnection(
            @Valid @RequestBody HoroshopConfig config) {

        log.info("Testing Horoshop connection for domain: {}", config.getDomain());

        try {
            HoroshopConfigValidation validation = horoshopService.validateConfig(config);
            return ResponseEntity.ok(validation);
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Export dataset to Horoshop
     */
    @PostMapping("/datasets/{datasetId}/export")
    public ResponseEntity<String> exportDataset(
            @PathVariable Long datasetId,
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody HoroshopConfig config) {

        log.info("Starting export of dataset {} to Horoshop for user {}", datasetId, userId);

        try {
            // Start async export
            CompletableFuture<HoroshopBulkResult> future =
                    horoshopService.exportDatasetToHoroshop(datasetId, userId, config);

            return ResponseEntity.accepted()
                    .body("Export started successfully. Check status endpoint for progress.");

        } catch (Exception e) {
            log.error("Failed to start export for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Export failed: " + e.getMessage());
        }
    }

    /**
     * Sync dataset with Horoshop (two-way)
     */
    @PostMapping("/datasets/{datasetId}/sync")
    public ResponseEntity<String> syncDataset(
            @PathVariable Long datasetId,
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody HoroshopSyncRequest request) {

        log.info("Starting sync of dataset {} with Horoshop for user {}", datasetId, userId);

        try {
            CompletableFuture<HoroshopBulkResult> future =
                    horoshopService.syncDatasetWithHoroshop(datasetId, userId,
                            request.getConfig(), request.getSyncSettings());

            return ResponseEntity.accepted()
                    .body("Sync started successfully. Check status endpoint for progress.");

        } catch (Exception e) {
            log.error("Failed to start sync for dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Sync failed: " + e.getMessage());
        }
    }

    /**
     * Bulk update products in Horoshop
     */
    @PostMapping("/products/bulk-update")
    public ResponseEntity<String> bulkUpdateProducts(
            @RequestBody HoroshopBulkUpdateRequest request,
            @RequestHeader("X-User-ID") String userId) {

        log.info("Starting bulk update of {} products in Horoshop",
                request.getProductIds().size());

        try {
            CompletableFuture<HoroshopBulkResult> future =
                    horoshopService.bulkUpdateProductsInHoroshop(
                            request.getProductIds(), userId, request.getConfig());

            return ResponseEntity.accepted()
                    .body("Bulk update started successfully. Check status endpoint for progress.");

        } catch (Exception e) {
            log.error("Failed to start bulk update: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Bulk update failed: " + e.getMessage());
        }
    }

    /**
     * Get Horoshop statistics
     */
    @PostMapping("/statistics")
    public ResponseEntity<HoroshopExportStatistics> getStatistics(
            @Valid @RequestBody HoroshopConfig config) {

        log.info("Fetching statistics from Horoshop");

        try {
            HoroshopExportStatistics stats = horoshopService.getHoroshopStatistics(config);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to fetch statistics: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get supported marketplaces for export
     */
    @GetMapping("/marketplaces")
    public ResponseEntity<List<String>> getSupportedMarketplaces() {
        List<String> marketplaces = List.of(
                "Facebook Feed",
                "Rozetka Feed",
                "Prom.ua Feed",
                "Google Shopping",
                "Instagram Shopping",
                "Telegram Shop"
        );

        return ResponseEntity.ok(marketplaces);
    }

    /**
     * Get export templates for different product types
     */
    @GetMapping("/templates")
    public ResponseEntity<List<HoroshopProductTemplate>> getProductTemplates() {
        List<HoroshopProductTemplate> templates = List.of(
                createElectronicsTemplate(),
                createClothingTemplate(),
                createHomeGardenTemplate(),
                createSportsTemplate()
        );

        return ResponseEntity.ok(templates);
    }

    /**
     * Get integration status and health
     */
    @GetMapping("/health")
    public ResponseEntity<HoroshopIntegrationHealth> getIntegrationHealth() {
        HoroshopIntegrationHealth health = new HoroshopIntegrationHealth();
        health.setStatus("HEALTHY");
        health.setVersion("1.0.0");
        health.setLastCheck(java.time.LocalDateTime.now());
        health.setSupportedFeatures(List.of(
                "Product Export",
                "Category Management",
                "Bulk Operations",
                "Two-way Sync",
                "Multi-language Support",
                "Image Upload",
                "Price Management",
                "Stock Synchronization"
        ));

        return ResponseEntity.ok(health);
    }

    // Helper methods for creating templates

    private HoroshopProductTemplate createElectronicsTemplate() {
        HoroshopProductTemplate template = new HoroshopProductTemplate();
        template.setName("Electronics");
        template.setDescription("Template for electronic products");
        template.setCategory("Electronics");

        template.setRequiredFields(List.of(
                "article", "title", "description", "price", "brand", "model"
        ));

        template.setRecommendedCharacteristics(List.of(
                "Бренд", "Модель", "Гарантия", "Страна производитель", "Цвет"
        ));

        template.setSampleProduct(createSampleElectronicsProduct());
        return template;
    }

    private HoroshopProductTemplate createClothingTemplate() {
        HoroshopProductTemplate template = new HoroshopProductTemplate();
        template.setName("Clothing");
        template.setDescription("Template for clothing and fashion items");
        template.setCategory("Fashion");

        template.setRequiredFields(List.of(
                "article", "title", "description", "price", "size", "color"
        ));

        template.setRecommendedCharacteristics(List.of(
                "Размер", "Цвет", "Материал", "Сезон", "Пол", "Бренд"
        ));

        template.setSampleProduct(createSampleClothingProduct());
        return template;
    }

    private HoroshopProductTemplate createHomeGardenTemplate() {
        HoroshopProductTemplate template = new HoroshopProductTemplate();
        template.setName("Home & Garden");
        template.setDescription("Template for home and garden products");
        template.setCategory("Home & Garden");

        template.setRequiredFields(List.of(
                "article", "title", "description", "price", "material"
        ));

        template.setRecommendedCharacteristics(List.of(
                "Материал", "Размеры", "Вес", "Цвет", "Применение"
        ));

        template.setSampleProduct(createSampleHomeProduct());
        return template;
    }

    private HoroshopProductTemplate createSportsTemplate() {
        HoroshopProductTemplate template = new HoroshopProductTemplate();
        template.setName("Sports & Outdoors");
        template.setDescription("Template for sports and outdoor products");
        template.setCategory("Sports");

        template.setRequiredFields(List.of(
                "article", "title", "description", "price", "brand", "sport_type"
        ));

        template.setRecommendedCharacteristics(List.of(
                "Бренд", "Вид спорта", "Размер", "Материал", "Сезон"
        ));

        template.setSampleProduct(createSampleSportsProduct());
        return template;
    }

    private HoroshopProduct createSampleElectronicsProduct() {
        HoroshopProduct sample = new HoroshopProduct();
        sample.setArticle("PHONE_001");

        sample.setTitle(java.util.Map.of(
                "ua", "Смартфон Samsung Galaxy S23",
                "ru", "Смартфон Samsung Galaxy S23",
                "en", "Samsung Galaxy S23 Smartphone"
        ));

        sample.setPrice(25000.0);
        sample.setParent("Электроника / Смартфоны");

        return sample;
    }

    private HoroshopProduct createSampleClothingProduct() {
        HoroshopProduct sample = new HoroshopProduct();
        sample.setArticle("SHIRT_001");

        sample.setTitle(java.util.Map.of(
                "ua", "Сорочка чоловіча класична",
                "ru", "Рубашка мужская классическая",
                "en", "Men's Classic Shirt"
        ));

        sample.setPrice(1200.0);
        sample.setParent("Одежда / Мужская / Рубашки");

        return sample;
    }

    private HoroshopProduct createSampleHomeProduct() {
        HoroshopProduct sample = new HoroshopProduct();
        sample.setArticle("VASE_001");

        sample.setTitle(java.util.Map.of(
                "ua", "Ваза керамічна декоративна",
                "ru", "Ваза керамическая декоративная",
                "en", "Decorative Ceramic Vase"
        ));

        sample.setPrice(800.0);
        sample.setParent("Дом и сад / Декор");

        return sample;
    }

    private HoroshopProduct createSampleSportsProduct() {
        HoroshopProduct sample = new HoroshopProduct();
        sample.setArticle("SHOES_001");

        sample.setTitle(java.util.Map.of(
                "ua", "Кросівки для бігу Nike Air Max",
                "ru", "Кроссовки для бега Nike Air Max",
                "en", "Nike Air Max Running Shoes"
        ));

        sample.setPrice(3500.0);
        sample.setParent("Спорт / Обувь / Беговая");

        return sample;
    }
}

// Additional DTOs for the controller

@lombok.Data
class HoroshopSyncRequest {
    @Valid
    private HoroshopConfig config;
    private HoroshopSyncSettings syncSettings;
}

@lombok.Data
class HoroshopBulkUpdateRequest {
    @Valid
    private HoroshopConfig config;
    private List<Long> productIds;
}

@lombok.Data
class HoroshopProductValidation {
    private HoroshopProduct product;
    private Boolean valid;
    private List<String> errors;
    private java.time.LocalDateTime timestamp;
}

@lombok.Data
class HoroshopProductTemplate {
    private String name;
    private String description;
    private String category;
    private List<String> requiredFields;
    private List<String> recommendedCharacteristics;
    private HoroshopProduct sampleProduct;
}

@lombok.Data
class HoroshopIntegrationHealth {
    private String status;
    private String version;
    private java.time.LocalDateTime lastCheck;
    private List<String> supportedFeatures;
}