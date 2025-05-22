package com.dropiq.engine.integration.exp.handler;

import com.dropiq.engine.integration.exp.model.Category;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EasyDropHandler extends PlatformHandler {

    public EasyDropHandler(RestTemplate restTemplate) {
        super(restTemplate);
    }

    @Override
    public List<UnifiedProduct> fetchProducts(String url, Map<String, String> headers) {
        try {
            String xmlContent = fetchXmlFromUrl(url, headers);
            return parseProductsFromXml(xmlContent, url);
        } catch (Exception e) {
            log.error("Error in fetchProducts: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Category> fetchCategories(String url, Map<String, String> headers) {
        try {
            String xmlContent = fetchXmlFromUrl(url, headers);
            return parseCategoriesFromXml(xmlContent);
        } catch (Exception e) {
            log.error("Error in fetchCategories: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Parse products from XML with error handling
     */
    private List<UnifiedProduct> parseProductsFromXml(String xmlContent, String sourceUrl) {
        List<UnifiedProduct> products = new ArrayList<>();

        try {
            Document doc = parseXml(xmlContent);
            NodeList itemNodes = doc.getElementsByTagName("item");

            if (itemNodes == null || itemNodes.getLength() == 0) {
                log.warn("No items found in XML from URL: {}", sourceUrl);
                return products;
            }

            log.info("Found {} items in XML from URL: {}", itemNodes.getLength(), sourceUrl);

            for (int i = 0; i < itemNodes.getLength(); i++) {
                try {
                    Element element = (Element) itemNodes.item(i);
                    UnifiedProduct product = parseProduct(element, sourceUrl);
                    if (product != null) {
                        products.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing product at index {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error parsing products from XML: {}", e.getMessage());
        }

        return products;
    }

    /**
     * Parse a single product with robust error handling
     */
    private UnifiedProduct parseProduct(Element element, String sourceUrl) {
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
                                String attrName = paramElement.getAttribute("name");
                                String value = paramElement.getTextContent();
                                if (attrName != null && !attrName.isEmpty()) {
                                    product.getAttributes().put(attrName, value != null ? value : "");
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
            return null;
        }
    }

    /**
     * Parse categories from XML with error handling
     */
    private List<Category> parseCategoriesFromXml(String xmlContent) {
        List<Category> categories = new ArrayList<>();

        try {
            Document doc = parseXml(xmlContent);
            NodeList categoryNodes = doc.getElementsByTagName("category");

            if (categoryNodes == null || categoryNodes.getLength() == 0) {
                log.warn("No categories found in XML");
                return categories;
            }

            for (int i = 0; i < categoryNodes.getLength(); i++) {
                try {
                    Element element = (Element) categoryNodes.item(i);

                    Category category = new Category();
                    category.setId(element.getAttribute("id"));
                    category.setName(element.getTextContent());
                    category.setSourceType(getSourceType());

                    categories.add(category);
                } catch (Exception e) {
                    log.warn("Error parsing category at index {}: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error parsing categories from XML: {}", e.getMessage());
        }

        return categories;
    }

    @Override
    public SourceType getSourceType() {
        return SourceType.EASYDROP;
    }
}