package com.dropiq.engine;

import com.dropiq.engine.integration.exp.model.ProductVariantGroup;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import com.dropiq.engine.integration.exp.service.UnifiedProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class UnifiedProductServiceTest {

    @Autowired
    private UnifiedProductService service;

    @Test
    public void testGetProductsAsList() {
        // When: Fetching products from EasyDrop
        Flux<UnifiedProduct> productFlux = service.fetchProductsFromPlatform(
                SourceType.EASYDROP,
                "https://easydrop.one/prom-export?key=96368875021347&pid=55541082991053",
                Map.of()
        );

        Flux<ProductVariantGroup> productVariantGroupFlux
                = service.groupProductsByVariant(productFlux);

        // Then: Convert flux to a blocking list and verify
        List<ProductVariantGroup> products = productVariantGroupFlux
                .collectList()
                .block(Duration.ofSeconds(60)); // Blocking call, only for testing

        // Print total count
        System.out.println("Total products fetched: " + products.size());

        // Print first few products as sample
        products.stream()
                .limit(5)
                .forEach(System.out::println);

        Flux<UnifiedProduct> myproductFlux = service.fetchProductsFromPlatform(
                SourceType.MYDROP,
                "https://backend.mydrop.com.ua/vendor/api/export/products/prom/yml?public_api_key=3c15e1e3250f59d703bc88175921945f778d68ca&price_field=price&param_name=%D0%A0%D0%B0%D0%B7%D0%BC%D0%B5%D1%80&stock_sync=true&only_available=true&static_sizes=true",
                Map.of()
        );

        Flux<ProductVariantGroup> myproductVariantGroupFlux
                = service.groupProductsByVariant(myproductFlux);

        // Then: Convert flux to a blocking list and verify
        List<ProductVariantGroup> myproducts = myproductVariantGroupFlux
                .collectList()
                .block(Duration.ofSeconds(5000)); // Blocking call, only for testing

        // Print total count
        System.out.println("Total products fetched: " + products.size());
    }
}
