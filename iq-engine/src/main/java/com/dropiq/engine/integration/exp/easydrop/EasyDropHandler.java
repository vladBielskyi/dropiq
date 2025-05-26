package com.dropiq.engine.integration.exp.easydrop;

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
public class EasyDropHandler extends PlatformHandler {

    private final SizeNormalizerService sizeNormalizerService;

    public EasyDropHandler(RestTemplate restTemplate, SizeNormalizerService sizeNormalizerService) {
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
            NodeList itemNodes = doc.getElementsByTagName("item");

            if (itemNodes == null || itemNodes.getLength() == 0) {
                log.warn("No items found in XML from URL: {}", sourceUrl);
                return products;
            }

            log.info("Found {} items in XML from URL: {}", itemNodes.getLength(), sourceUrl);

            for (int i = 0; i < itemNodes.getLength(); i++) {
                try {
                    Element element = (Element) itemNodes.item(i);
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
     * Групуємо товари за group_id
     */
    private Map<String, List<Element>> groupItemsByGroupId(NodeList itemNodes) {
        Map<String, List<Element>> grouped = new HashMap<>();

        for (int i = 0; i < itemNodes.getLength(); i++) {
            try {
                Element element = (Element) itemNodes.item(i);
                String groupId = element.getAttribute("group_id");

                if (groupId == null || groupId.isEmpty()) {
                    groupId = element.getAttribute("id"); // Використовуємо ID як group_id
                }

                grouped.computeIfAbsent(groupId, k -> new ArrayList<>()).add(element);
            } catch (Exception e) {
                log.debug("Error processing item at index {}: {}", i, e.getMessage());
            }
        }

        return grouped;
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

            // Список популярних брендів
            String[] brands = {"yeezy", "nike", "adidas", "jordan", "without", "prada", "balenciaga",
                    "dr.martens", "new balance", "under armour", "reebok", "salomon", "osiris"};

            for (String brand : brands) {
                if (nameLower.contains(brand)) {
                    product.setBrand(brand.substring(0, 1).toUpperCase() + brand.substring(1).toLowerCase());
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting brand: {}", e.getMessage());
        }
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

    /**
     * Парсить опис товару
     */
    private void parseDescription(UnifiedProduct product, Element element) {
        try {
            String description = getElementTextContent(element, "description");
            if (description != null && !description.isEmpty()) {
                // Очищаємо HTML теги та форматуємо опис
                String cleanDescription = cleanHtmlDescription(description);
                product.setDescription(cleanDescription);

                // Витягуємо додаткову інформацію з опису
                extractInfoFromDescription(product, cleanDescription);
            }
        } catch (Exception e) {
            log.debug("Error parsing description: {}", e.getMessage());
        }
    }

    /**
     * Очищає HTML теги з опису
     */
    private String cleanHtmlDescription(String description) {
        if (description == null) return "";

        return description
                .replaceAll("<[^>]+>", "") // Видаляємо HTML теги
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ") // Замінюємо кілька пробілів на один
                .trim();
    }

    /**
     * Витягує додаткову інформацію з опису
     */
    private void extractInfoFromDescription(UnifiedProduct product, String description) {
        try {
            String descLower = description.toLowerCase();

            // Витягуємо матеріал - тільки коротку частину
            if (descLower.contains("матеріал:") || descLower.contains("материал:")) {
                String[] parts = description.split("(?i)(матеріал:|материал:)");
                if (parts.length > 1) {
                    String materialPart = parts[1].split("[\\n\\r]")[0].trim();
                    // Обрізаємо якщо занадто довгий
                    if (materialPart.length() > 100) {
                        materialPart = materialPart.substring(0, 97) + "...";
                    }
                    if (!materialPart.isEmpty()) {
                        product.setMaterial(materialPart);
                    }
                }
            }

            // Витягуємо країну виробника - тільки коротку частину
            if (descLower.contains("виробник:") || descLower.contains("производитель:")) {
                String[] parts = description.split("(?i)(виробник:|производитель:)");
                if (parts.length > 1) {
                    String countryPart = parts[1].split("[\\n\\r]")[0].trim();
                    // Обрізаємо якщо занадто довгий
                    if (countryPart.length() > 50) {
                        countryPart = countryPart.substring(0, 47) + "...";
                    }
                }
            }

            // Витягуємо колір (якщо згадується)
            String[] colors = {"black", "white", "red", "blue", "green", "yellow", "чорний", "білий", "червоний"};
            for (String color : colors) {
                if (descLower.contains(color)) {
                    product.setColor(color);
                    break;
                }
            }

        } catch (Exception e) {
            log.debug("Error extracting info from description: {}", e.getMessage());
        }
    }

    /**
     * Парсить ціну та наявність
     */
    private void parsePriceAndAvailability(UnifiedProduct product, Element element) {
        try {
            // Ціна
            String priceStr = getElementTextContent(element, "priceuah");
            if (priceStr != null && !priceStr.isEmpty()) {
                try {
                    product.setPrice(Double.parseDouble(priceStr.trim()));
                } catch (NumberFormatException e) {
                    log.debug("Invalid price format: {}", priceStr);
                }
            }

            // Кількість на складі
            String quantityStr = getElementTextContent(element, "quantity_in_stock");
            if (quantityStr != null && !quantityStr.isEmpty()) {
                try {
                    product.setStock(Integer.parseInt(quantityStr.trim()));
                } catch (NumberFormatException e) {
                    log.debug("Invalid stock quantity format: {}", quantityStr);
                }
            }

            // Доступність
            String availableStr = element.getAttribute("available");
            product.setAvailable("true".equals(availableStr) && product.getStock() > 0);

        } catch (Exception e) {
            log.debug("Error parsing price and availability: {}", e.getMessage());
        }
    }

    /**
     * Парсить зображення
     */
    private void parseImages(UnifiedProduct product, Element element) {
        try {
            List<String> imageUrls = getAllImageUrls(element, "image", "picture", "gallery", "photos", "img");
            product.getImageUrls().addAll(imageUrls);

            log.debug("Found {} images for product {}: {}",
                    imageUrls.size(), product.getName(), imageUrls);

        } catch (Exception e) {
            log.debug("Error parsing images: {}", e.getMessage());
        }
    }

    /**
     * Парсить атрибути товару включаючи розмір
     */
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

                                // Обробляємо розмір окремо
                                if ("розмір".equalsIgnoreCase(attrName) || "размер".equalsIgnoreCase(attrName)) {
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

    /**
     * Парсить платформо-специфічні дані
     */
    private void parsePlatformSpecificData(UnifiedProduct product, Element element) {
        try {
            String barcode = getElementTextContent(element, "barcode");
            if (barcode != null && !barcode.isEmpty()) {
                product.getPlatformSpecificData().put("barcode", barcode);
            }

            String sellingType = element.getAttribute("selling_type");
            if (sellingType != null && !sellingType.isEmpty()) {
                product.getPlatformSpecificData().put("selling_type", sellingType);
            }

        } catch (Exception e) {
            log.debug("Error parsing platform specific data: {}", e.getMessage());
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
                    category.setName(element.getTextContent() != null ? element.getTextContent().trim() : "Unknown Category");
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