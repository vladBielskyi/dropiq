package com.dropiq.engine.integration.exp.service;

import com.dropiq.engine.integration.exp.handler.PlatformHandler;
import com.dropiq.engine.integration.exp.model.DataSourceConfig;
import com.dropiq.engine.integration.exp.model.ProductVariantGroup;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class UnifiedProductService {

    private final Map<SourceType, PlatformHandler> platformHandlers;

    public UnifiedProductService(List<PlatformHandler> handlers) {

        Map<SourceType, PlatformHandler> handlersMap = new HashMap<>();
        for (PlatformHandler handler : handlers) {
            handlersMap.put(handler.getSourceType(), handler);
        }

        this.platformHandlers = Collections.unmodifiableMap(handlersMap);
    }

    /**
     * Fetch products from a single platform
     */
    public Flux<UnifiedProduct> fetchProductsFromPlatform(SourceType platformType, String url, Map<String, String> headers) {
        PlatformHandler handler = platformHandlers.get(platformType);

        if (handler == null) {
            return Flux.error(new RuntimeException("Unsupported platform: " + platformType));
        }

        return handler.fetchProducts(url, headers);
    }

    /**
     * Fetch products from all configured platforms
     */
    public Flux<UnifiedProduct> fetchProductsFromAllPlatforms(List<DataSourceConfig> configs) {
        return Flux.fromIterable(configs)
                .flatMap(config -> {
                    PlatformHandler handler = platformHandlers.get(config.getPlatformType());

                    if (handler == null) {
                        log.warn("Unsupported platform: {}", config.getPlatformType());
                        return Flux.empty();
                    }

                    return handler.fetchProducts(config.getUrl(), config.getHeaders())
                            .doOnError(e -> log.error("Error fetching products from {}: {}",
                                    config.getPlatformType(), e.getMessage()));
                });
    }

    /**
     * Group products by variant group
     */
    public Flux<ProductVariantGroup> groupProductsByVariant(Flux<UnifiedProduct> products) {
        return products
                .filter(product -> product.getGroupId() != null && !product.getGroupId().isEmpty())
                .groupBy(UnifiedProduct::getGroupId)
                .flatMap(group -> group
                        .collectList()
                        .map(productList -> {
                            ProductVariantGroup variantGroup = new ProductVariantGroup();
                            variantGroup.setGroupId(group.key());

                            if (!productList.isEmpty()) {
                                UnifiedProduct firstProduct = productList.get(0);
                                variantGroup.setName(firstProduct.getName());
                                variantGroup.setDescription(firstProduct.getDescription());
                                variantGroup.setCategoryId(firstProduct.getCategoryId());
                                variantGroup.setCategoryName(firstProduct.getCategoryName());
                                variantGroup.getImageUrls().addAll(firstProduct.getImageUrls());
                            }

                            for (UnifiedProduct product : productList) {
                                variantGroup.getVariants().add(product);
                                variantGroup.getSourcePlatforms().add(product.getSourceType());
                            }

                            variantGroup.setLastUpdated(LocalDateTime.now());

                            return variantGroup;
                        })
                );
    }

    /**
     * Aggregate products from multiple platforms and group by variant
     */
    public Mono<List<ProductVariantGroup>> aggregateAndGroupProducts(List<DataSourceConfig> configs) {
        return fetchProductsFromAllPlatforms(configs)
                .collectMultimap(UnifiedProduct::getGroupId)
                .map(groupMap -> {
                    List<ProductVariantGroup> groups = new ArrayList<>();

                    for (Map.Entry<String, Collection<UnifiedProduct>> entry : groupMap.entrySet()) {
                        if (entry.getKey() == null || entry.getKey().isEmpty()) {
                            // Products without group ID, each is its own "group"
                            for (UnifiedProduct product : entry.getValue()) {
                                ProductVariantGroup singleGroup = ProductVariantGroup.fromProduct(product);
                                groups.add(singleGroup);
                            }
                        } else {
                            // Products with the same group ID
                            ProductVariantGroup group = new ProductVariantGroup();
                            group.setGroupId(entry.getKey());

                            Iterator<UnifiedProduct> iterator = entry.getValue().iterator();
                            if (iterator.hasNext()) {
                                UnifiedProduct firstProduct = iterator.next();
                                group.setName(firstProduct.getName());
                                group.setDescription(firstProduct.getDescription());
                                group.setCategoryId(firstProduct.getCategoryId());
                                group.setCategoryName(firstProduct.getCategoryName());
                                group.getImageUrls().addAll(firstProduct.getImageUrls());
                                group.getVariants().add(firstProduct);
                                group.getSourcePlatforms().add(firstProduct.getSourceType());
                            }

                            while (iterator.hasNext()) {
                                UnifiedProduct product = iterator.next();
                                group.getVariants().add(product);
                                group.getSourcePlatforms().add(product.getSourceType());
                            }

                            group.setLastUpdated(LocalDateTime.now());
                            groups.add(group);
                        }
                    }

                    return groups;
                });
    }
}
