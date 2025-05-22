package com.dropiq.engine.integration.exp.handler;

import com.dropiq.engine.integration.exp.model.Category;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class MyDropHandler extends PlatformHandler {

    public MyDropHandler(WebClient.Builder webClientBuilder) {
        super(webClientBuilder);
    }

    @Override
    public Flux<UnifiedProduct> fetchProducts(String url, Map<String, String> headers) {
        return fetchXmlFromUrl(url, headers)
                .flatMapMany(xmlContent -> parseProductsFromXml(xmlContent, url));
    }

    @Override
    public Flux<Category> fetchCategories(String url, Map<String, String> headers) {
        return fetchXmlFromUrl(url, headers)
                .flatMapMany(this::parseCategoriesFromXml);
    }

    /**
     * Parse products from XML
     */
    private Flux<UnifiedProduct> parseProductsFromXml(String xmlContent, String sourceUrl) {
        return parseXml(xmlContent)
                .flatMapMany(doc -> {
                    NodeList offerNodes = doc.getElementsByTagName("offer");

                    return Flux.range(0, offerNodes.getLength())
                            .flatMap(i -> {
                                Element element = (Element) offerNodes.item(i);
                                return parseProduct(element, sourceUrl);
                            });
                });
    }

    /**
     * Parse a single product
     */
    private Mono<UnifiedProduct> parseProduct(Element element, String sourceUrl) {
        return Mono.fromCallable(() -> {
            UnifiedProduct product = new UnifiedProduct();

            product.setExternalId(element.getAttribute("id"));
            product.setGroupId(element.getAttribute("group_id"));
            product.setName(getElementTextContent(element, "name"));
            product.setDescription(getElementCDataContent(element, "description"));

            String priceStr = getElementTextContent(element, "price");
            if (priceStr != null && !priceStr.isEmpty()) {
                product.setPrice(Double.parseDouble(priceStr));
            }

            product.setCategoryId(getElementTextContent(element, "categoryId"));

            String quantityStr = getElementTextContent(element, "quantity_in_stock");
            if (quantityStr != null && !quantityStr.isEmpty()) {
                product.setStock(Integer.parseInt(quantityStr));
            }

            product.setAvailable("true".equals(element.getAttribute("available")));
            product.setSourceType(getSourceType());
            product.setSourceUrl(sourceUrl);

            // Platform-specific data
            product.getPlatformSpecificData().put("vendorCode", getElementTextContent(element, "vendorCode"));
            product.getPlatformSpecificData().put("selling_type", element.getAttribute("selling_type"));

            String currencyId = getElementTextContent(element, "currencyId");
            if (currencyId != null && !currencyId.isEmpty()) {
                product.getPlatformSpecificData().put("currencyId", currencyId);
            }

            String vendor = getElementTextContent(element, "vendor");
            if (vendor != null && !vendor.isEmpty() && !"-".equals(vendor)) {
                product.getPlatformSpecificData().put("vendor", vendor);
            }

            // Parse image URL
            String imageUrl = getElementTextContent(element, "picture");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                product.getImageUrls().add(imageUrl);
            }

            // Parse attributes
            NodeList paramNodes = element.getElementsByTagName("param");
            for (int i = 0; i < paramNodes.getLength(); i++) {
                Element paramElement = (Element) paramNodes.item(i);
                String name = paramElement.getAttribute("name");
                String value = paramElement.getTextContent();
                if (name != null && !name.isEmpty()) {
                    product.getAttributes().put(name, value);
                }
            }

            product.setLastUpdated(LocalDateTime.now());

            return product;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Parse categories from XML
     */
    private Flux<Category> parseCategoriesFromXml(String xmlContent) {
        return parseXml(xmlContent)
                .flatMapMany(doc -> {
                    NodeList categoryNodes = doc.getElementsByTagName("category");

                    return Flux.range(0, categoryNodes.getLength())
                            .map(i -> {
                                Element element = (Element) categoryNodes.item(i);

                                Category category = new Category();
                                category.setId(element.getAttribute("id"));
                                category.setName(element.getTextContent());
                                category.setParentId(element.getAttribute("parentId"));
                                category.setSourceType(getSourceType());

                                return category;
                            });
                });
    }

    @Override
    public SourceType getSourceType() {
        return SourceType.MYDROP;
    }
}
