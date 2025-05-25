package com.dropiq.engine;

import com.dropiq.engine.integration.exp.model.DataSourceConfig;
import com.dropiq.engine.integration.exp.model.ProductVariantGroup;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import com.dropiq.engine.integration.exp.service.UnifiedProductService;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.service.AIProductAnalysisService;
import com.dropiq.engine.product.service.DataSetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class UnifiedProductServiceTest {

    @Autowired
    private UnifiedProductService service;

    @Autowired
    private AIProductAnalysisService productAnalysisService;

    @Autowired
    private DataSetService dataSetService;

    // Test URLs
    private static final String EASYDROP_URL = "https://easydrop.one/prom-export?key=96368875021347&pid=55541082991053";
    private static final String MYDROP_URL = "https://backend.mydrop.com.ua/vendor/api/export/products/prom/yml?public_api_key=3c15e1e3250f59d703bc88175921945f778d68ca&price_field=price&param_name=Размер&stock_sync=true&only_available=true&static_sizes=true";

    @Test
    public void dataSet() {
        DataSourceConfig easyConfig = new DataSourceConfig();
        easyConfig.setUrl(EASYDROP_URL);
        easyConfig.setPlatformType(SourceType.EASYDROP);
        easyConfig.setHeaders(Map.of());


        DataSet dataSet =
                dataSetService
                        .createDatasetFromSources("Test 3", "Test", "Vlad", List.of(easyConfig));


        for (Product product : dataSet.getProducts()) {
            productAnalysisService.analyzeProduct(product.getId());
        }
    }

    @Test
    @DisplayName("Test 1: Fetch products from EasyDrop platform")
    public void testFetchProductsFromEasyDrop() {
        System.out.println("\n=== Testing EasyDrop Integration ===");

        // When: Fetching products from EasyDrop
        List<UnifiedProduct> products = service.fetchProductsFromPlatform(
                SourceType.EASYDROP,
                EASYDROP_URL,
                Map.of()
        );

        // Then: Verify and display results
        assertNotNull(products, "Products list should not be null");
        System.out.println("✓ EasyDrop products fetched: " + products.size());

        if (!products.isEmpty()) {
            // Verify product structure
            UnifiedProduct firstProduct = products.get(0);
            assertNotNull(firstProduct.getExternalId(), "Product should have external ID");
            assertNotNull(firstProduct.getName(), "Product should have name");
            assertEquals(SourceType.EASYDROP, firstProduct.getSourceType(), "Source type should be EASYDROP");

            // Display sample products
            System.out.println("\nSample EasyDrop products:");
            products.stream()
                    .limit(3)
                    .forEach(product -> {
                        System.out.printf("  - %s | Price: %.2f UAH | Stock: %d | Available: %s%n",
                                product.getName(),
                                product.getPrice(),
                                product.getStock(),
                                product.isAvailable() ? "Yes" : "No");
                    });
        } else {
            System.out.println("⚠ No products returned from EasyDrop");
        }
    }

    @Test
    @DisplayName("Test 2: Fetch products from MyDrop platform")
    public void testFetchProductsFromMyDrop() {
        System.out.println("\n=== Testing MyDrop Integration ===");

        // When: Fetching products from MyDrop
        List<UnifiedProduct> products = service.fetchProductsFromPlatform(
                SourceType.MYDROP,
                MYDROP_URL,
                Map.of()
        );

        // Then: Verify and display results
        assertNotNull(products, "Products list should not be null");
        System.out.println("✓ MyDrop products fetched: " + products.size());

        if (!products.isEmpty()) {
            // Verify product structure
            UnifiedProduct firstProduct = products.get(0);
            assertNotNull(firstProduct.getExternalId(), "Product should have external ID");
            assertNotNull(firstProduct.getName(), "Product should have name");
            assertEquals(SourceType.MYDROP, firstProduct.getSourceType(), "Source type should be MYDROP");

            // Display sample products
            System.out.println("\nSample MyDrop products:");
            products.stream()
                    .limit(3)
                    .forEach(product -> {
                        System.out.printf("  - %s | Price: %.2f UAH | Stock: %d | Available: %s%n",
                                product.getName(),
                                product.getPrice(),
                                product.getStock(),
                                product.isAvailable() ? "Yes" : "No");
                    });
        } else {
            System.out.println("⚠ No products returned from MyDrop");
        }
    }

    @Test
    @DisplayName("Test 3: Group products by variants")
    public void testGroupProductsByVariant() {
        System.out.println("\n=== Testing Product Variant Grouping ===");

        // Given: Get products from EasyDrop (known to have variants)
        List<UnifiedProduct> products = service.fetchProductsFromPlatform(
                SourceType.EASYDROP,
                EASYDROP_URL,
                Map.of()
        );

        // When: Grouping products by variant
        List<ProductVariantGroup> variantGroups = service.groupProductsByVariant(products);

        // Then: Verify grouping
        assertNotNull(variantGroups, "Variant groups should not be null");
        System.out.println("✓ Original products: " + products.size());
        System.out.println("✓ Variant groups created: " + variantGroups.size());

        if (!variantGroups.isEmpty()) {
            // Display sample variant groups
            System.out.println("\nSample variant groups:");
            variantGroups.stream()
                    .limit(3)
                    .forEach(group -> {
                        System.out.printf("  - Group: %s | Variants: %d | Platforms: %s%n",
                                group.getName(),
                                group.getVariants().size(),
                                group.getSourcePlatforms());
                    });

            // Verify group structure
            ProductVariantGroup firstGroup = variantGroups.get(0);
            assertNotNull(firstGroup.getGroupId(), "Group should have ID");
            assertNotNull(firstGroup.getName(), "Group should have name");
            assertFalse(firstGroup.getVariants().isEmpty(), "Group should have variants");
        }
    }

    @Test
    @DisplayName("Test 4: Aggregate products from multiple platforms")
    public void testAggregateFromMultiplePlatforms() {
        System.out.println("\n=== Testing Multi-Platform Aggregation ===");

        // Given: Configuration for both platforms
        DataSourceConfig easyDropConfig = new DataSourceConfig();
        easyDropConfig.setPlatformType(SourceType.EASYDROP);
        easyDropConfig.setUrl(EASYDROP_URL);

        DataSourceConfig myDropConfig = new DataSourceConfig();
        myDropConfig.setPlatformType(SourceType.MYDROP);
        myDropConfig.setUrl(MYDROP_URL);

        List<DataSourceConfig> configs = List.of(easyDropConfig, myDropConfig);

        // When: Aggregating products from both platforms
        List<ProductVariantGroup> aggregatedGroups = service.aggregateAndGroupProducts(configs);

        // Then: Verify aggregation
        assertNotNull(aggregatedGroups, "Aggregated groups should not be null");
        System.out.println("✓ Aggregated variant groups: " + aggregatedGroups.size());

        // Verify we have products from both platforms
        boolean hasEasyDrop = aggregatedGroups.stream()
                .anyMatch(group -> group.getSourcePlatforms().contains(SourceType.EASYDROP));
        boolean hasMyDrop = aggregatedGroups.stream()
                .anyMatch(group -> group.getSourcePlatforms().contains(SourceType.MYDROP));

        if (hasEasyDrop) System.out.println("✓ Contains EasyDrop products");
        if (hasMyDrop) System.out.println("✓ Contains MyDrop products");

        // Display platform distribution
        long easyDropGroups = aggregatedGroups.stream()
                .filter(group -> group.getSourcePlatforms().contains(SourceType.EASYDROP))
                .count();
        long myDropGroups = aggregatedGroups.stream()
                .filter(group -> group.getSourcePlatforms().contains(SourceType.MYDROP))
                .count();

        System.out.println("Groups with EasyDrop products: " + easyDropGroups);
        System.out.println("Groups with MyDrop products: " + myDropGroups);
    }

    @Test
    @DisplayName("Test 5: Get product statistics")
    public void testGetProductStatistics() {
        System.out.println("\n=== Testing Product Statistics ===");

        // Given: Configuration for both platforms
        List<DataSourceConfig> configs = createTestConfigs();

        // When: Getting statistics
        Map<String, Object> stats = service.getProductStatistics(configs);

        // Then: Verify statistics
        assertNotNull(stats, "Statistics should not be null");
        assertTrue(stats.containsKey("totalProducts"), "Should contain total products");
        assertTrue(stats.containsKey("productsByPlatform"), "Should contain platform breakdown");
        assertTrue(stats.containsKey("availableProducts"), "Should contain availability stats");

        // Display statistics
        System.out.println("📊 Product Statistics:");
        System.out.println("  Total products: " + stats.get("totalProducts"));
        System.out.println("  Available products: " + stats.get("availableProducts"));
        System.out.println("  Unavailable products: " + stats.get("unavailableProducts"));
        System.out.println("  By platform: " + stats.get("productsByPlatform"));

        if (stats.containsKey("productsByCategory")) {
            System.out.println("  By category: " + stats.get("productsByCategory"));
        }

        // Verify data types
        assertTrue(stats.get("totalProducts") instanceof Integer, "Total products should be integer");
        assertTrue(stats.get("productsByPlatform") instanceof Map, "Platform breakdown should be map");
    }

    @Test
    @DisplayName("Test 6: Search products by name")
    public void testSearchProductsByName() {
        System.out.println("\n=== Testing Product Search ===");

        // Given: Configuration for search
        List<DataSourceConfig> configs = createTestConfigs();
        String searchTerm = "350"; // Common in EasyDrop products

        // When: Searching for products
        List<UnifiedProduct> searchResults = service.searchProductsByName(configs, searchTerm);

        // Then: Verify search results
        assertNotNull(searchResults, "Search results should not be null");
        System.out.println("🔍 Search results for '" + searchTerm + "': " + searchResults.size());

        if (!searchResults.isEmpty()) {
            // Verify all results contain the search term
            searchResults.forEach(product -> {
                assertTrue(product.getName().toLowerCase().contains(searchTerm.toLowerCase()),
                        "Product name should contain search term: " + product.getName());
            });

            // Display sample results
            System.out.println("\nSample search results:");
            searchResults.stream()
                    .limit(5)
                    .forEach(product -> {
                        System.out.printf("  - %s | %s | %.2f UAH%n",
                                product.getName(),
                                product.getSourceType(),
                                product.getPrice());
                    });
        } else {
            System.out.println("⚠ No products found matching '" + searchTerm + "'");
        }
    }

    @Test
    @DisplayName("Test 7: Get products by category")
    public void testGetProductsByCategory() {
        System.out.println("\n=== Testing Category Filtering ===");

        // Given: Get all products first to find a category
        List<UnifiedProduct> allProducts = service.fetchProductsFromPlatform(
                SourceType.EASYDROP,
                EASYDROP_URL,
                Map.of()
        );

        if (!allProducts.isEmpty()) {
            // Find a category that has products
            String categoryId = allProducts.stream()
                    .filter(p -> p.getExternalCategoryId() != null)
                    .map(UnifiedProduct::getExternalCategoryId)
                    .findFirst()
                    .orElse(null);

            if (categoryId != null) {
                // When: Getting products by category
                List<UnifiedProduct> categoryProducts = service.getProductsByCategory(
                        SourceType.EASYDROP,
                        EASYDROP_URL,
                        categoryId,
                        Map.of()
                );

                // Then: Verify category filtering
                assertNotNull(categoryProducts, "Category products should not be null");
                System.out.println("✓ Products in category '" + categoryId + "': " + categoryProducts.size());

                // Verify all products belong to the category
                categoryProducts.forEach(product -> {
                    assertEquals(categoryId, product.getExternalCategoryId(),
                            "All products should belong to the specified category");
                });
            } else {
                System.out.println("⚠ No categories found in products");
            }
        } else {
            System.out.println("⚠ No products available for category testing");
        }
    }

    @Test
    @DisplayName("Test 8: Error handling for invalid platform")
    public void testErrorHandlingForInvalidPlatform() {
        System.out.println("\n=== Testing Error Handling ===");

        // When: Using invalid platform type
        try {
            List<UnifiedProduct> products = service.fetchProductsFromPlatform(
                    SourceType.XML_FILE, // This should not have a handler
                    "http://invalid-url.com",
                    Map.of()
            );

            // Then: Should handle gracefully
            assertNotNull(products, "Should return empty list, not null");
            assertTrue(products.isEmpty(), "Should return empty list for unsupported platform");
            System.out.println("✓ Error handling works correctly - returned empty list");

        } catch (RuntimeException e) {
            // This is also acceptable behavior
            System.out.println("✓ Error handling works correctly - threw exception: " + e.getMessage());
            assertTrue(e.getMessage().contains("Unsupported platform"),
                    "Exception message should indicate unsupported platform");
        }
    }

    // Helper method to create test configurations
    private List<DataSourceConfig> createTestConfigs() {
        DataSourceConfig easyDropConfig = new DataSourceConfig();
        easyDropConfig.setPlatformType(SourceType.EASYDROP);
        easyDropConfig.setUrl(EASYDROP_URL);

        DataSourceConfig myDropConfig = new DataSourceConfig();
        myDropConfig.setPlatformType(SourceType.MYDROP);
        myDropConfig.setUrl(MYDROP_URL);

        return List.of(easyDropConfig, myDropConfig);
    }
}