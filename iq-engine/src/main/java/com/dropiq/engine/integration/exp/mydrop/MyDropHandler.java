package com.dropiq.engine.integration.exp.mydrop;

import com.dropiq.engine.integration.exp.PlatformHandler;
import com.dropiq.engine.integration.exp.model.Category;
import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import com.dropiq.engine.integration.exp.service.SizeNormalizerService;
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
import java.util.stream.Collectors;

@Slf4j
@Service
public class MyDropHandler extends PlatformHandler {

    private final SizeNormalizerService sizeNormalizerService;

    public MyDropHandler(RestTemplate restTemplate, SizeNormalizerService sizeNormalizerService) {
        super(restTemplate);
        this.sizeNormalizerService = sizeNormalizerService;
    }

    @Override
    public List<UnifiedProduct> fetchProducts(String url, Map<String, String> headers) {
        try {
            String xmlContent = fetchXmlFromUrl(url, headers);
            List<Category> categories = fetchCategories(xmlContent);
            return parseProductsFromXml(xmlContent, url, categories);
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

    @Override
    public List<Category> fetchCategories(String xmlContent) {
        return parseCategoriesFromXml(xmlContent);
    }

    /**
     * Parse products from XML with enhanced error handling and grouping
     */
    private List<UnifiedProduct> parseProductsFromXml(String xmlContent, String sourceUrl, List<Category> categories) {
        List<UnifiedProduct> products = new ArrayList<>();
        Map<String, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        try {
            Document doc = parseXml(xmlContent);
            NodeList offerNodes = doc.getElementsByTagName("offer");

            if (offerNodes == null || offerNodes.getLength() == 0) {
                log.warn("No offers found in XML from URL: {}", sourceUrl);
                return products;
            }

            log.info("Found {} offers in XML from URL: {}", offerNodes.getLength(), sourceUrl);

            for (int i = 0; i < offerNodes.getLength(); i++) {
                try {
                    Element element = (Element) offerNodes.item(i);
                    UnifiedProduct product = parseBaseProduct(element, sourceUrl, categoryMap);
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
     * Парсить базовий товар
     */
    private UnifiedProduct parseBaseProduct(Element element, String sourceUrl, Map<String, String> categories) {
        try {
            UnifiedProduct product = new UnifiedProduct();

            initializeProductDefaults(product, sourceUrl);
            parseBasicAttributes(product, element);
            parseCategory(product, element, categories);
            parseDescription(product, element);
            parsePriceAndAvailability(product, element);
            parseImages(product, element);
            parseAttributes(product, element);
            parsePlatformSpecificData(product, element);

            return product;
        } catch (Exception e) {
            log.error("Error creating base product: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Ініціалізує значення за замовчуванням
     */
    private void initializeProductDefaults(UnifiedProduct product, String sourceUrl) {
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
    }

    /**
     * Парсить основні атрибути
     */
    private void parseBasicAttributes(UnifiedProduct product, Element element) {
        try {
            String id = element.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                product.setExternalId(id);
            }

            String groupId = element.getAttribute("group_id");
            if (groupId != null && !groupId.isEmpty()) {
                product.setGroupId(groupId);
            }

            String name = getElementTextContent(element, "name");
            if (name != null && !name.isEmpty()) {
                product.setName(name.trim());

                // Витягуємо бренд з назви
                extractBrandFromName(product, name);
            }
        } catch (Exception e) {
            log.debug("Error parsing basic attributes: {}", e.getMessage());
        }
    }

    /**
     * Витягує бренд з назви товару
     */
    private void extractBrandFromName(UnifiedProduct product, String name) {
        try {
            String nameLower = name.toLowerCase();

            // Список популярних брендів (розширений для MyDrop)
            String[] brands = {"without", "nike", "adidas", "puma", "under armour", "reebok",
                    "new balance", "converse", "vans", "champion", "calvin klein",
                    "tommy hilfiger", "lacoste", "polo ralph lauren", "guess"};

            for (String brand : brands) {
                if (nameLower.contains(brand)) {
                    product.setBrand(capitalizeWords(brand));
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting brand: {}", e.getMessage());
        }
    }

    /**
     * Капіталізує слова
     */
    private String capitalizeWords(String text) {
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Парсить категорію
     */
    private void parseCategory(UnifiedProduct product, Element element, Map<String, String> categories) {
        try {
            String categoryId = getElementTextContent(element, "categoryId");
            if (categoryId != null && !categoryId.isEmpty()) {
                product.setCategoryId(categoryId);

                String categoryName = categories.get(categoryId);
                if (categoryName != null && !categoryName.isEmpty()) {
                    product.setCategoryName(categoryName);
                }
            }
        } catch (Exception e) {
            log.debug("Error parsing category: {}", e.getMessage());
        }
    }

    private void parseDescription(UnifiedProduct product, Element element) {
        try {
            // Спробуємо отримати CDATA контент спочатку
            String description = getElementCDataContent(element, "description");
            if (description == null || description.isEmpty()) {
                description = getElementTextContent(element, "description");
            }

            if (description != null && !description.isEmpty()) {
                // Очищаємо HTML теги та форматуємо опис
                String cleanDescription = cleanHtmlDescription(description);
                product.setDescription(cleanDescription);
            }
        } catch (Exception e) {
            log.debug("Error parsing description: {}", e.getMessage());
        }
    }

    private String cleanHtmlDescription(String description) {
        if (description == null) return "";

        return description
                .replaceAll("<br\\s*/?>", "\n") // Замінюємо <br> на нові рядки
                .replaceAll("<[^>]+>", "") // Видаляємо інші HTML теги
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ") // Замінюємо кілька пробілів на один
                .trim();
    }


    private void parsePriceAndAvailability(UnifiedProduct product, Element element) {
        try {
            String priceStr = getElementTextContent(element, "price");
            if (priceStr != null && !priceStr.isEmpty()) {
                try {
                    product.setPrice(Double.parseDouble(priceStr.trim()));
                } catch (NumberFormatException e) {
                    log.debug("Invalid price format: {}", priceStr);
                }
            }
            String quantityStr = getElementTextContent(element, "quantity_in_stock");
            if (quantityStr != null && !quantityStr.isEmpty()) {
                try {
                    product.setStock(Integer.parseInt(quantityStr.trim()));
                } catch (NumberFormatException e) {
                    log.debug("Invalid stock quantity format: {}", quantityStr);
                }
            }
            String availableStr = element.getAttribute("available");
            product.setAvailable("true".equals(availableStr) && product.getStock() > 0);

        } catch (Exception e) {
            log.debug("Error parsing price and availability: {}", e.getMessage());
        }
    }

    private void parseImages(UnifiedProduct product, Element element) {
        try {
            List<String> imageUrls = getAllImageUrls(element, "picture", "image", "gallery", "photos");
            product.getImageUrls().addAll(imageUrls);

            log.debug("Found {} images for product {}: {}",
                    imageUrls.size(), product.getName(), imageUrls);

        } catch (Exception e) {
            log.debug("Error parsing images: {}", e.getMessage());
        }
    }

    private void parseAttributes(UnifiedProduct product, Element element) {
        try {
            NodeList paramNodes = element.getElementsByTagName("param");
            if (paramNodes != null) {
                for (int i = 0; i < paramNodes.getLength(); i++) {
                    try {
                        Element paramElement = (Element) paramNodes.item(i);
                        if (paramElement != null) {
                            String attrName = paramElement.getAttribute("name");
                            String attrUnit = paramElement.getAttribute("unit");
                            String value = paramElement.getTextContent();

                            if (attrName != null && !attrName.isEmpty()) {
                                if ("размер".equalsIgnoreCase(attrName) || "розмір".equalsIgnoreCase(attrName)) {
                                    UnifiedProduct.ProductSize size = sizeNormalizerService.normalizeSize(
                                            value, product.getName(), product.getCategoryName());

                                    if (attrUnit != null && !attrUnit.isEmpty()) {
                                        size.setUnit(attrUnit);
                                    }

                                    product.setSize(size);
                                }
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
    }

    private void parsePlatformSpecificData(UnifiedProduct product, Element element) {
        try {
            String vendorCode = getElementTextContent(element, "vendorCode");
            if (vendorCode != null && !vendorCode.isEmpty()) {
                product.getPlatformSpecificData().put("vendorCode", vendorCode);
            }

            String sellingType = element.getAttribute("selling_type");
            if (sellingType != null && !sellingType.isEmpty()) {
                product.getPlatformSpecificData().put("selling_type", sellingType);
            }

            String currencyId = getElementTextContent(element, "currencyId");
            if (currencyId != null && !currencyId.isEmpty()) {
                product.getPlatformSpecificData().put("currencyId", currencyId);
            }

            String vendor = getElementTextContent(element, "vendor");
            if (vendor != null && !vendor.isEmpty() && !"-".equals(vendor)) {
                product.getPlatformSpecificData().put("vendor", vendor);
                // Якщо бренд ще не встановлено, використовуємо vendor
                if (product.getBrand() == null) {
                    product.setBrand(vendor);
                }
            }

        } catch (Exception e) {
            log.debug("Error parsing platform specific data: {}", e.getMessage());
        }
    }

    /**
     * Parse categories from XML with comprehensive error handling
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
                    if (element == null) {
                        continue;
                    }

                    Category category = new Category();

                    try {
                        String id = element.getAttribute("id");
                        if (id != null && !id.isEmpty()) {
                            category.setId(id);
                        } else {
                            // ID обов'язковий для категорії
                            log.debug("Category without ID found, skipping");
                            continue;
                        }
                    } catch (Exception e) {
                        log.debug("Error getting category ID: {}", e.getMessage());
                        continue;
                    }

                    try {
                        String name = element.getTextContent();
                        category.setName(name != null ? name.trim() : "Unknown Category");
                    } catch (Exception e) {
                        log.debug("Error getting category name: {}", e.getMessage());
                        category.setName("Unknown Category");
                    }

                    try {
                        String parentId = element.getAttribute("parentId");
                        if (parentId != null && !parentId.isEmpty()) {
                            category.setParentId(parentId);
                        }
                    } catch (Exception e) {
                        log.debug("Error getting parent ID: {}", e.getMessage());
                    }

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
        return SourceType.MYDROP;
    }
}