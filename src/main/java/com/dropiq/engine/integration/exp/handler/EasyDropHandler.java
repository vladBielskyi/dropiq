package com.dropiq.engine.integration.exp.handler;

import com.dropiq.engine.integration.exp.model.Category;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class EasyDropHandler extends PlatformHandler {

    public EasyDropHandler(WebClient.Builder webClientBuilder) {
        super(webClientBuilder);
    }

    @Override
    public Flux<UnifiedProduct> fetchProducts(String url, Map<String, String> headers) {
        return fetchXmlFromUrl(url, headers)
                .flatMapMany(xmlContent -> parseProductsFromXml(xmlContent, url))
                .onErrorResume(e -> {
                    log.error("Error in fetchProducts: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    @Override
    public Flux<Category> fetchCategories(String url, Map<String, String> headers) {
        return fetchXmlFromUrl(url, headers)
                .flatMapMany(this::parseCategoriesFromXml)
                .onErrorResume(e -> {
                    log.error("Error in fetchCategories: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    /**
     * Parse products from XML with error handling
     */
    private Flux<UnifiedProduct> parseProductsFromXml(String xmlContent, String sourceUrl) {
        return parseXml(xmlContent)
                .flatMapMany(doc -> {
                    try {
                        NodeList itemNodes = doc.getElementsByTagName("item");
                        if (itemNodes == null || itemNodes.getLength() == 0) {
                            log.warn("No items found in XML from URL: {}", sourceUrl);
                            return Flux.empty();
                        }

                        log.info("Found {} items in XML from URL: {}", itemNodes.getLength(), sourceUrl);

                        return Flux.range(0, itemNodes.getLength())
                                .flatMap(i -> {
                                    try {
                                        Element element = (Element) itemNodes.item(i);
                                        return parseProduct(element, sourceUrl)
                                                .onErrorResume(e -> {
                                                    log.warn("Error parsing product at index {}: {}", i, e.getMessage());
                                                    return Mono.empty();
                                                });
                                    } catch (Exception e) {
                                        log.warn("Error getting item at index {}: {}", i, e.getMessage());
                                        return Mono.empty();
                                    }
                                });
                    } catch (Exception e) {
                        log.error("Error parsing products from XML: {}", e.getMessage());
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error in parseProductsFromXml: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Parse a single product with robust error handling
     */
    private Mono<UnifiedProduct> parseProduct(Element element, String sourceUrl) {
        return Mono.fromCallable(() -> {
                    try {
                        UnifiedProduct product = new UnifiedProduct();

                        // Set default values
                        product.setExternalId("unknown");
                        product.setName("Unknown Product");
                        product.setDescription("");
                        product.setPrice(0.0);
                        product.setStock(0);
                        product.setAvailable(false);
                        product.setSourceType(getSourceType());
                        product.setSourceUrl(sourceUrl);
                        product.setImageUrls(new ArrayList<>());
                        product.setAttributes(new HashMap<>());
                        product.setPlatformSpecificData(new HashMap<>());
                        product.setLastUpdated(LocalDateTime.now());

                        // Safely set actual values
                        try {
                            String id = element.getAttribute("id");
                            if (id != null && !id.isEmpty()) {
                                product.setExternalId(id);
                            }
                        } catch (Exception e) {
                            log.debug("Error getting ID attribute: {}", e.getMessage());
                        }

                        try {
                            String groupId = element.getAttribute("group_id");
                            if (groupId != null && !groupId.isEmpty()) {
                                product.setGroupId(groupId);
                            }
                        } catch (Exception e) {
                            log.debug("Error getting group_id attribute: {}", e.getMessage());
                        }

                        // Product name
                        String name = getElementTextContent(element, "name");
                        if (name != null && !name.isEmpty()) {
                            product.setName(name);
                        }

                        // Product description
                        String description = getElementTextContent(element, "description");
                        if (description != null && !description.isEmpty()) {
                            product.setDescription(description);
                        }

                        // Price
                        try {
                            String priceStr = getElementTextContent(element, "priceuah");
                            if (priceStr != null && !priceStr.isEmpty()) {
                                try {
                                    product.setPrice(Double.parseDouble(priceStr.trim()));
                                } catch (NumberFormatException e) {
                                    log.debug("Invalid price format: {}", priceStr);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Error getting price: {}", e.getMessage());
                        }

                        // Category ID
                        String categoryId = getElementTextContent(element, "categoryId");
                        if (categoryId != null && !categoryId.isEmpty()) {
                            product.setCategoryId(categoryId);
                        }

                        // Stock quantity
                        try {
                            String quantityStr = getElementTextContent(element, "quantity_in_stock");
                            if (quantityStr != null && !quantityStr.isEmpty()) {
                                try {
                                    product.setStock(Integer.parseInt(quantityStr.trim()));
                                } catch (NumberFormatException e) {
                                    log.debug("Invalid stock quantity format: {}", quantityStr);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Error getting stock quantity: {}", e.getMessage());
                        }

                        // Availability
                        try {
                            String availableStr = element.getAttribute("available");
                            product.setAvailable("true".equals(availableStr));
                        } catch (Exception e) {
                            log.debug("Error getting availability: {}", e.getMessage());
                        }

                        // Platform-specific data
                        try {
                            String barcode = getElementTextContent(element, "barcode");
                            if (barcode != null) {
                                product.getPlatformSpecificData().put("barcode", barcode);
                            }

                            String sellingType = element.getAttribute("selling_type");
                            if (sellingType != null && !sellingType.isEmpty()) {
                                product.getPlatformSpecificData().put("selling_type", sellingType);
                            }
                        } catch (Exception e) {
                            log.debug("Error getting platform specific data: {}", e.getMessage());
                        }

                        // Image URL
                        try {
                            String imageUrl = getElementTextContent(element, "image");
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                product.getImageUrls().add(imageUrl);
                            }
                        } catch (Exception e) {
                            log.debug("Error getting image URL: {}", e.getMessage());
                        }

                        // Parse attributes
                        try {
                            NodeList paramNodes = element.getElementsByTagName("param");
                            if (paramNodes != null) {
                                for (int i = 0; i < paramNodes.getLength(); i++) {
                                    try {
                                        Element paramElement = (Element) paramNodes.item(i);
                                        if (paramElement != null) {
                                            String value = paramElement.getTextContent();
                                            if (name != null && !name.isEmpty()) {
                                                product.getAttributes().put(name, value != null
                                                        ? value : "");
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.debug("Error parsing attribute at index {}: {}", i, e.getMessage());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Error parsing attributes: {}", e.getMessage());
                        }

                        return product;
                    } catch (Exception e) {
                        log.error("Error creating UnifiedProduct: {}", e.getMessage());
                        throw e;
                    }
                }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Error in parseProduct: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Parse categories from XML with error handling
     */
    private Flux<Category> parseCategoriesFromXml(String xmlContent) {
        return parseXml(xmlContent)
                .flatMapMany(doc -> {
                    try {
                        NodeList categoryNodes = doc.getElementsByTagName("category");
                        if (categoryNodes == null || categoryNodes.getLength() == 0) {
                            log.warn("No categories found in XML");
                            return Flux.empty();
                        }

                        return Flux.range(0, categoryNodes.getLength())
                                .map(i -> {
                                    try {
                                        Element element = (Element) categoryNodes.item(i);

                                        Category category = new Category();
                                        category.setId(element.getAttribute("id"));
                                        category.setName(element.getTextContent());
                                        category.setSourceType(getSourceType());

                                        return category;
                                    } catch (Exception e) {
                                        log.warn("Error parsing category at index {}: {}", i, e.getMessage());
                                        return null;
                                    }
                                })
                                .filter(category -> category != null);
                    } catch (Exception e) {
                        log.error("Error parsing categories from XML: {}", e.getMessage());
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error in parseCategoriesFromXml: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public SourceType getSourceType() {
        return SourceType.EASYDROP;
    }
}