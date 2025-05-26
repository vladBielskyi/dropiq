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
     * Групуємо товари за group_id
     */
    private Map<String, List<Element>> groupOffersByGroupId(NodeList offerNodes) {
        Map<String, List<Element>> grouped = new HashMap<>();

        for (int i = 0; i < offerNodes.getLength(); i++) {
            try {
                Element element = (Element) offerNodes.item(i);
                String groupId = element.getAttribute("group_id");

                if (groupId == null || groupId.isEmpty()) {
                    groupId = element.getAttribute("id"); // Використовуємо ID як group_id
                }

                grouped.computeIfAbsent(groupId, k -> new ArrayList<>()).add(element);
            } catch (Exception e) {
                log.debug("Error processing offer at index {}: {}", i, e.getMessage());
            }
        }

        return grouped;
    }

    /**
     * Парсить групу товарів (основний товар + варіанти)
     */
    private UnifiedProduct parseProductGroup(List<Element> elements, String sourceUrl, Map<String, String> categories) {
        if (elements == null || elements.isEmpty()) {
            return null;
        }

        try {
            // Використовуємо перший елемент як основу для товару
            Element mainElement = elements.getFirst();
            UnifiedProduct product = parseBaseProduct(mainElement, sourceUrl, categories);

            if (product == null) {
                return null;
            }

            // Якщо є кілька елементів, створюємо варіанти
            if (elements.size() > 1) {
                for (Element element : elements) {
                    try {
                        UnifiedProduct.ProductVariant variant = parseProductVariant(element, categories);
                        if (variant != null) {
                            product.addVariant(variant);
                        }
                    } catch (Exception e) {
                        log.debug("Error parsing variant: {}", e.getMessage());
                    }
                }
            }

            return product;
        } catch (Exception e) {
            log.error("Error parsing product group: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Парсить базовий товар
     */
    private UnifiedProduct parseBaseProduct(Element element, String sourceUrl, Map<String, String> categories) {
        try {
            UnifiedProduct product = new UnifiedProduct();

            // Встановлюємо значення за замовчуванням
            initializeProductDefaults(product, sourceUrl);

            // Основні атрибути
            parseBasicAttributes(product, element);

            // Категорія
            parseCategory(product, element, categories);

            // Опис товару
            parseDescription(product, element);

            // Ціна та наявність
            parsePriceAndAvailability(product, element);

            // Зображення
            parseImages(product, element);

            // Атрибути (включаючи розмір)
            parseAttributes(product, element);

            // Платформо-специфічні дані
            parsePlatformSpecificData(product, element);

            // Фізичні розміри товару
            parsePhysicalDimensions(product, element);

            // Додаткова інформація для SEO та аналітики
            parseAdditionalInfo(product, element);

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
        product.setVariants(new ArrayList<>());
        product.setTags(new ArrayList<>());
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
            if (sb.length() > 0) sb.append(" ");
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
                product.setExternalCategoryId(categoryId);

                String categoryName = categories.get(categoryId);
                if (categoryName != null && !categoryName.isEmpty()) {
                    product.setExternalCategoryName(categoryName);
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
            // Спробуємо отримати CDATA контент спочатку
            String description = getElementCDataContent(element, "description");
            if (description == null || description.isEmpty()) {
                description = getElementTextContent(element, "description");
            }

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

    /**
     * Витягує додаткову інформацію з опису
     */
    private void extractInfoFromDescription(UnifiedProduct product, String description) {
        try {
            String descLower = description.toLowerCase();

            // Витягуємо матеріал/склад
            extractMaterial(product, description, descLower);

            // Витягуємо країну виробника
            extractCountry(product, description, descLower);

            // Витягуємо колір
            extractColor(product, description, descLower);

            // Витягуємо розміри та характеристики
            extractSizeInfo(product, description, descLower);

        } catch (Exception e) {
            log.debug("Error extracting info from description: {}", e.getMessage());
        }
    }

    private void extractMaterial(UnifiedProduct product, String description, String descLower) {
        String[] materialPatterns = {"склад:", "состав:", "матеріал:", "материал:"};
        for (String pattern : materialPatterns) {
            if (descLower.contains(pattern)) {
                String[] parts = description.split("(?i)" + pattern);
                if (parts.length > 1) {
                    String materialPart = parts[1].split("[\\n\\r•]")[0].trim();
                    if (!materialPart.isEmpty()) {
                        product.setMaterial(materialPart);
                        break;
                    }
                }
            }
        }
    }

    private void extractCountry(UnifiedProduct product, String description, String descLower) {
        String[] countryPatterns = {"виробник:", "производитель:", "країна:"};
        for (String pattern : countryPatterns) {
            if (descLower.contains(pattern)) {
                String[] parts = description.split("(?i)" + pattern);
                if (parts.length > 1) {
                    String countryPart = parts[1].split("[\\n\\r•]")[0].trim();
                    if (!countryPart.isEmpty()) {
                        product.setCountry(countryPart);
                        break;
                    }
                }
            }
        }
    }

    private void extractColor(UnifiedProduct product, String description, String descLower) {
        // Український/російський кольори
        Map<String, String> colorMap = new HashMap<>();
        colorMap.put("чорний", "black");
        colorMap.put("черный", "black");
        colorMap.put("білий", "white");
        colorMap.put("белый", "white");
        colorMap.put("червоний", "red");
        colorMap.put("красный", "red");
        colorMap.put("синій", "blue");
        colorMap.put("синий", "blue");
        colorMap.put("зелений", "green");
        colorMap.put("зеленый", "green");
        colorMap.put("сірий", "gray");
        colorMap.put("серый", "gray");

        for (Map.Entry<String, String> entry : colorMap.entrySet()) {
            if (descLower.contains(entry.getKey())) {
                product.setColor(entry.getValue());
                break;
            }
        }

        // Англійські кольори
        String[] englishColors = {"black", "white", "red", "blue", "green", "yellow", "gray", "brown", "pink", "purple"};
        for (String color : englishColors) {
            if (descLower.contains(color)) {
                product.setColor(color);
                break;
            }
        }
    }

    private void extractSizeInfo(UnifiedProduct product, String description, String descLower) {
        // Шукаємо розмірні характеристики, але зберігаємо тільки коротку інформацію
        if (descLower.contains("розмір") || descLower.contains("размер")) {
            // Витягуємо тільки першу строку з розмірною інформацією
            String[] lines = description.split("[\\n\\r]");
            for (String line : lines) {
                String trimmedLine = line.trim();
                if ((trimmedLine.toLowerCase().contains("розмір") || trimmedLine.toLowerCase().contains("размер"))
                        && trimmedLine.length() <= 100) { // Тільки короткі рядки
                    product.addAttribute("size_info", trimmedLine);
                    break;
                }
            }
        }
    }

    /**
     * Парсить ціну та наявність
     */
    private void parsePriceAndAvailability(UnifiedProduct product, Element element) {
        try {
            // Ціна
            String priceStr = getElementTextContent(element, "price");
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
            List<String> imageUrls = getAllImageUrls(element, "picture", "image", "gallery", "photos");
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
                                product.addAttribute(attrName, value != null ? value : "");

                                // Обробляємо розмір окремо
                                if ("размер".equalsIgnoreCase(attrName) || "розмір".equalsIgnoreCase(attrName)) {
                                    UnifiedProduct.ProductSize size = sizeNormalizerService.normalizeSize(
                                            value, product.getName(), product.getExternalCategoryName());

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
     * Парсить фізичні розміри товару
     */
    private void parsePhysicalDimensions(UnifiedProduct product, Element element) {
        try {
            UnifiedProduct.PhysicalDimensions dimensions = new UnifiedProduct.PhysicalDimensions();
            boolean hasDimensions = false;

            String lengthStr = getElementTextContent(element, "length");
            if (lengthStr != null && !lengthStr.isEmpty()) {
                try {
                    dimensions.setLength(Double.parseDouble(lengthStr));
                    hasDimensions = true;
                } catch (NumberFormatException e) {
                    log.debug("Invalid length format: {}", lengthStr);
                }
            }

            String widthStr = getElementTextContent(element, "width");
            if (widthStr != null && !widthStr.isEmpty()) {
                try {
                    dimensions.setWidth(Double.parseDouble(widthStr));
                    hasDimensions = true;
                } catch (NumberFormatException e) {
                    log.debug("Invalid width format: {}", widthStr);
                }
            }

            String heightStr = getElementTextContent(element, "height");
            if (heightStr != null && !heightStr.isEmpty()) {
                try {
                    dimensions.setHeight(Double.parseDouble(heightStr));
                    hasDimensions = true;
                } catch (NumberFormatException e) {
                    log.debug("Invalid height format: {}", heightStr);
                }
            }

            String weightStr = getElementTextContent(element, "weight");
            if (weightStr != null && !weightStr.isEmpty()) {
                try {
                    product.setWeight(Double.parseDouble(weightStr));
                } catch (NumberFormatException e) {
                    log.debug("Invalid weight format: {}", weightStr);
                }
            }

            if (hasDimensions) {
                product.setDimensions(dimensions);
            }

        } catch (Exception e) {
            log.debug("Error parsing physical dimensions: {}", e.getMessage());
        }
    }

    /**
     * Парсить додаткову інформацію
     */
    private void parseAdditionalInfo(UnifiedProduct product, Element element) {
        try {
            // Генеруємо SEO заголовок
            generateSeoTitle(product);

            // Генеруємо SEO опис
            generateSeoDescription(product);

            // Генеруємо теги
            generateTags(product);

        } catch (Exception e) {
            log.debug("Error parsing additional info: {}", e.getMessage());
        }
    }

    private void generateSeoTitle(UnifiedProduct product) {
        StringBuilder seoTitle = new StringBuilder();

        if (product.getBrand() != null) {
            seoTitle.append(product.getBrand()).append(" ");
        }

        if (product.getName() != null) {
            seoTitle.append(product.getName());
        }

        if (product.getSize() != null && product.getSize().getNormalizedValue() != null
                && !"ONE_SIZE".equals(product.getSize().getNormalizedValue())) {
            seoTitle.append(" розмір ").append(product.getSize().getNormalizedValue());
        }

        if (product.getColor() != null) {
            seoTitle.append(" ").append(product.getColor());
        }

        product.setSeoTitle(seoTitle.toString().trim());
    }

    private void generateSeoDescription(UnifiedProduct product) {
        StringBuilder seoDesc = new StringBuilder();

        if (product.getName() != null) {
            seoDesc.append(product.getName());
        }

        if (product.getBrand() != null) {
            seoDesc.append(" від ").append(product.getBrand());
        }

        if (product.getPrice() != null && product.getPrice() > 0) {
            seoDesc.append(". Ціна: ").append(product.getFormattedPrice()).append(" грн");
        }

        if (product.getExternalCategoryName() != null) {
            seoDesc.append(". Категорія: ").append(product.getExternalCategoryName());
        }

        seoDesc.append(". Швидка доставка по Україні.");

        product.setSeoDescription(seoDesc.toString());
    }

    /**
     * Генерує теги для товару
     */
    private void generateTags(UnifiedProduct product) {
        try {
            if (product.getBrand() != null) {
                product.getTags().add(product.getBrand().toLowerCase());
            }

            if (product.getColor() != null) {
                product.getTags().add(product.getColor().toLowerCase());
            }

            if (product.getExternalCategoryName() != null) {
                product.getTags().add(product.getExternalCategoryName().toLowerCase());
            }

            if (product.getSize() != null && product.getSize().getType() != null) {
                product.getTags().add(product.getSize().getType().name().toLowerCase());
            }

            if (product.getMaterial() != null) {
                product.getTags().add("material_" + product.getMaterial().toLowerCase().replaceAll("\\s+", "_"));
            }

            if (product.getCountry() != null) {
                product.getTags().add("country_" + product.getCountry().toLowerCase().replaceAll("\\s+", "_"));
            }

        } catch (Exception e) {
            log.debug("Error generating tags: {}", e.getMessage());
        }
    }

    /**
     * Парсить варіант товару
     */
    private UnifiedProduct.ProductVariant parseProductVariant(Element element, Map<String, String> categories) {
        try {
            UnifiedProduct.ProductVariant variant = new UnifiedProduct.ProductVariant();

            // ID варіанту
            String id = element.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                variant.setId(id);
            }

            // Назва (зазвичай така ж як у основного товару)
            String name = getElementTextContent(element, "name");
            if (name != null && !name.isEmpty()) {
                variant.setName(name.trim());
            }

            // Ціна
            String priceStr = getElementTextContent(element, "price");
            if (priceStr != null && !priceStr.isEmpty()) {
                try {
                    variant.setPrice(Double.parseDouble(priceStr.trim()));
                } catch (NumberFormatException e) {
                    log.debug("Invalid variant price format: {}", priceStr);
                }
            }

            // Кількість
            String quantityStr = getElementTextContent(element, "quantity_in_stock");
            if (quantityStr != null && !quantityStr.isEmpty()) {
                try {
                    variant.setStock(Integer.parseInt(quantityStr.trim()));
                } catch (NumberFormatException e) {
                    log.debug("Invalid variant stock format: {}", quantityStr);
                }
            }

            // Доступність
            String availableStr = element.getAttribute("available");
            variant.setAvailable("true".equals(availableStr) && (variant.getStock() == null || variant.getStock() > 0));

            // Vendor Code як барcode
            String vendorCode = getElementTextContent(element, "vendorCode");
            if (vendorCode != null && !vendorCode.isEmpty()) {
                variant.setBarcode(vendorCode);
            }

            // Зображення
            List<String> variantImages = getAllImageUrls(element, "picture", "image", "gallery", "photos");
            variant.getImageUrls().addAll(variantImages);

            // Атрибути варіанту (особливо розмір)
            NodeList paramNodes = element.getElementsByTagName("param");
            if (paramNodes != null) {
                for (int i = 0; i < paramNodes.getLength(); i++) {
                    try {
                        Element paramElement = (Element) paramNodes.item(i);
                        if (paramElement != null) {
                            String attrName = paramElement.getAttribute("name");
                            String value = paramElement.getTextContent();

                            if (attrName != null && !attrName.isEmpty()) {
                                variant.getAttributes().put(attrName, value != null ? value : "");

                                // Обробляємо розмір варіанту
                                if ("размер".equalsIgnoreCase(attrName) || "розмір".equalsIgnoreCase(attrName)) {
                                    UnifiedProduct.ProductSize size = sizeNormalizerService.normalizeSize(
                                            value, variant.getName(), null);
                                    variant.setSize(size);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Error parsing variant attribute at index {}: {}", i, e.getMessage());
                    }
                }
            }

            return variant;
        } catch (Exception e) {
            log.error("Error creating product variant: {}", e.getMessage());
            return null;
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