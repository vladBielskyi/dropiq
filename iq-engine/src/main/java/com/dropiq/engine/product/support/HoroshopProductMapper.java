package com.dropiq.engine.product.support;

import com.dropiq.engine.integration.imp.horoshop.model.HoroshopCharacteristic;
import com.dropiq.engine.integration.imp.horoshop.model.HoroshopImages;
import com.dropiq.engine.integration.imp.horoshop.model.HoroshopProduct;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mapper for converting between internal Product entities and Horoshop API format
 */
@Slf4j
@Component
public class HoroshopProductMapper {

    /**
     * Convert internal Product to Horoshop format
     */
    public HoroshopProduct toHoroshopProduct(Product product, DataSet dataset) {
        log.debug("Converting product {} to Horoshop format", product.getName());

        HoroshopProduct horoshopProduct = new HoroshopProduct();

        // Basic identification
        horoshopProduct.setArticle(product.getExternalId());
        horoshopProduct.setParentArticle(product.getGroupId());

        // Multilingual titles
        Map<String, String> titles = new HashMap<>();
        if (product.getSeoTitleUk() != null) {
            titles.put("ua", product.getSeoTitleUk());
        } else {
            titles.put("ua", product.getName());
        }

        if (product.getSeoTitleRu() != null) {
            titles.put("ru", product.getSeoTitleRu());
        } else {
            titles.put("ru", product.getName());
        }

        if (product.getSeoTitleEn() != null) {
            titles.put("en", product.getSeoTitleEn());
        } else {
            titles.put("en", product.getName());
        }
        horoshopProduct.setTitle(titles);

        // Multilingual descriptions
        Map<String, String> descriptions = new HashMap<>();
        if (product.getDescriptionUk() != null) {
            descriptions.put("ua", cleanHtmlTags(product.getDescriptionUk()));
        } else if (product.getOriginalDescription() != null) {
            descriptions.put("ua", cleanHtmlTags(product.getOriginalDescription()));
        }

        if (product.getDescriptionRu() != null) {
            descriptions.put("ru", cleanHtmlTags(product.getDescriptionRu()));
        } else if (product.getOriginalDescription() != null) {
            descriptions.put("ru", cleanHtmlTags(product.getOriginalDescription()));
        }

        if (product.getDescriptionEn() != null) {
            descriptions.put("en", cleanHtmlTags(product.getDescriptionEn()));
        } else if (product.getOriginalDescription() != null) {
            descriptions.put("en", cleanHtmlTags(product.getOriginalDescription()));
        }
        horoshopProduct.setDescription(descriptions);

        // Short descriptions (meta descriptions)
        Map<String, String> shortDescriptions = new HashMap<>();
        if (product.getMetaDescriptionUk() != null) {
            shortDescriptions.put("ua", product.getMetaDescriptionUk());
        }
        if (product.getMetaDescriptionRu() != null) {
            shortDescriptions.put("ru", product.getMetaDescriptionRu());
        }
        if (product.getMetaDescriptionEn() != null) {
            shortDescriptions.put("en", product.getMetaDescriptionEn());
        }
        horoshopProduct.setShortDescription(shortDescriptions);

        // Pricing
        if (product.getSellingPrice() != null) {
            horoshopProduct.setPrice(product.getSellingPrice().doubleValue());
        }
        if (product.getOriginalPrice() != null &&
                product.getSellingPrice() != null &&
                product.getOriginalPrice().compareTo(product.getSellingPrice()) < 0) {
            horoshopProduct.setPriceOld(product.getSellingPrice().doubleValue());
            horoshopProduct.setPrice(product.getOriginalPrice().doubleValue());
        }

        // Inventory
        horoshopProduct.setQuantity(product.getStock() != null ? product.getStock() : 0);
        horoshopProduct.setPresence(determinePresence(product));

        // Category
        if (product.getCategory() != null) {
            horoshopProduct.setParent(buildCategoryPath(product.getCategory()));
        }

        // Images
        if (!product.getImageUrls().isEmpty()) {
            HoroshopImages images = new HoroshopImages();
            images.setOverride(false); // Add to existing images
            images.setLinks(new ArrayList<>(product.getImageUrls()));
            horoshopProduct.setImages(images);
        }

        // SEO
        if (product.getCategory() != null) {
            horoshopProduct.setSlug(generateSlug(product.getCategory().getSlug()));
        }

        // Product attributes as characteristics
        List<HoroshopCharacteristic> characteristics = new ArrayList<>();

        // Add attributes from product
        if (product.getAttributes() != null) {
            product.getAttributes().forEach((key, value) -> {
                if (value != null && !value.trim().isEmpty()) {
                    HoroshopCharacteristic characteristic = new HoroshopCharacteristic();
                    characteristic.setName(translateAttributeName(key));
                    characteristic.setValue(value);
                    characteristics.add(characteristic);
                }
            });
        }

        // Add AI-generated attributes
        addAiGeneratedCharacteristics(product, characteristics);

        horoshopProduct.setCharacteristics(characteristics);

        // Marketing features
        List<String> icons = new ArrayList<>();
        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() > 8.0) {
            icons.add("Хит");
        }
        if (product.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusDays(30))) {
            icons.add("Новинка");
        }
        if (horoshopProduct.getPriceOld() != null && horoshopProduct.getPriceOld() > horoshopProduct.getPrice()) {
            icons.add("Распродажа");
        }
        horoshopProduct.setIcons(icons);

        // Display settings
        horoshopProduct.setDisplayInShowcase(true);
        horoshopProduct.setForceAliasUpdate(false);

        // Popularity based on trend score
        if (product.getTrendScore() != null) {
            horoshopProduct.setPopularity(Math.min(100, product.getTrendScore().intValue() * 10));
        }

        // Export to marketplaces (configurable)
        horoshopProduct.setExportToMarketplace("Facebook Feed;Rozetka Feed");

        log.debug("Successfully converted product {} to Horoshop format", product.getName());
        return horoshopProduct;
    }

    /**
     * Update internal Product from Horoshop data
     */
    public void updateFromHoroshopProduct(Product product, HoroshopProduct horoshopProduct) {
        log.debug("Updating product {} from Horoshop data", product.getName());

        // Update stock and availability
        if (horoshopProduct.getQuantity() != null) {
            product.setStock(horoshopProduct.getQuantity());
        }

        if (horoshopProduct.getPresence() != null) {
            product.setAvailable("В наличии".equals(horoshopProduct.getPresence()));
        }

        // Update pricing if changed
        if (horoshopProduct.getPrice() != null) {
            product.setOriginalPrice(java.math.BigDecimal.valueOf(horoshopProduct.getPrice()));
        }

        // Update images if provided
        if (horoshopProduct.getImages() != null && !horoshopProduct.getImages().getLinks().isEmpty()) {
            if (horoshopProduct.getImages().getOverride()) {
                product.getImageUrls().clear();
            }
            // Add new images that don't already exist
            horoshopProduct.getImages().getLinks().forEach(imageUrl -> {
                if (!product.getImageUrls().contains(imageUrl)) {
                    product.getImageUrls().add(imageUrl);
                }
            });
        }

        // Update characteristics/attributes
        if (horoshopProduct.getCharacteristics() != null) {
            horoshopProduct.getCharacteristics().forEach(characteristic -> {
                String attributeKey = translateCharacteristicName(characteristic.getName());
                product.getAttributes().put(attributeKey, characteristic.getValue());
            });
        }

        product.setLastSync(java.time.LocalDateTime.now());
        log.debug("Successfully updated product {} from Horoshop data", product.getName());
    }

    /**
     * Convert multiple products for batch operations
     */
    public List<HoroshopProduct> toHoroshopProducts(List<Product> products, DataSet dataset) {
        return products.stream()
                .map(product -> toHoroshopProduct(product, dataset))
                .collect(Collectors.toList());
    }

    /**
     * Create Horoshop product for variant groups
     */
    public List<HoroshopProduct> createVariantGroup(List<Product> variants, DataSet dataset) {
        if (variants.isEmpty()) {
            return new ArrayList<>();
        }

        // Find parent product (first or main variant)
        Product parentProduct = variants.stream()
                .min(Comparator.comparing(Product::getId))
                .orElse(variants.get(0));

        List<HoroshopProduct> result = new ArrayList<>();

        // Create parent product
        HoroshopProduct parent = toHoroshopProduct(parentProduct, dataset);
        parent.setParentArticle(parentProduct.getGroupId());
        result.add(parent);

        // Create child variants
        for (Product variant : variants) {
            if (!variant.getId().equals(parentProduct.getId())) {
                HoroshopProduct child = toHoroshopProduct(variant, dataset);
                child.setParentArticle(parentProduct.getExternalId());

                // Add variant-specific characteristics
                addVariantCharacteristics(child, variant, parentProduct);
                result.add(child);
            }
        }

        return result;
    }

    // Helper methods

    private String determinePresence(Product product) {
        if (!product.getAvailable()) {
            return "Нет в наличии";
        }

        if (product.getStock() != null && product.getStock() > 0) {
            return "В наличии";
        }

        return "Под заказ";
    }

    private String buildCategoryPath(com.dropiq.engine.product.entity.DatasetCategory category) {
        if (category.getParent() != null) {
            return buildCategoryPath(category.getParent()) + " / " + category.getNameEn();
        }
        return category.getNameEn();
    }

    private String generateSlug(String name) {
        if (name == null) return "";

        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String cleanHtmlTags(String html) {
        if (html == null) return "";

        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .trim();
    }

    private void addAiGeneratedCharacteristics(Product product, List<HoroshopCharacteristic> characteristics) {
        // Add brand if detected
//        if (product.getPlatformSpecificData().containsKey("detected_brand")) {
//            HoroshopCharacteristic brand = new HoroshopCharacteristic();
//            brand.setName("Бренд");
//            brand.setValue(product.getPlatformSpecificData().get("detected_brand"));
//            characteristics.add(brand);
//        }

        // Add model if detected
        if (product.getPlatformSpecificData().containsKey("model_name")) {
            HoroshopCharacteristic model = new HoroshopCharacteristic();
            model.setName("Модель");
            model.setValue(product.getPlatformSpecificData().get("model_name"));
            characteristics.add(model);
        }

        // Add color analysis
        if (product.getColorAnalysis() != null && !product.getColorAnalysis().trim().isEmpty()) {
            HoroshopCharacteristic color = new HoroshopCharacteristic();
            color.setName("Цвет");
            color.setValue(product.getColorAnalysis());
            characteristics.add(color);
        }

        // Add main features
        if (product.getMainFeatures() != null && !product.getMainFeatures().trim().isEmpty()) {
            String[] features = product.getMainFeatures().split(",");
            for (int i = 0; i < Math.min(features.length, 3); i++) {
                HoroshopCharacteristic feature = new HoroshopCharacteristic();
                feature.setName("Особенность " + (i + 1));
                feature.setValue(features[i].trim());
                characteristics.add(feature);
            }
        }

        // Add style tags
        if (product.getStyleTags() != null && !product.getStyleTags().trim().isEmpty()) {
            HoroshopCharacteristic style = new HoroshopCharacteristic();
            style.setName("Стиль");
            style.setValue(product.getStyleTags());
            characteristics.add(style);
        }
    }

    private void addVariantCharacteristics(HoroshopProduct child, Product variant, Product parent) {
        // Add size if different
        if (variant.getAttributes().containsKey("size") &&
                !Objects.equals(variant.getAttributes().get("size"), parent.getAttributes().get("size"))) {

            HoroshopCharacteristic size = new HoroshopCharacteristic();
            size.setName("Размер");
            size.setValue(variant.getAttributes().get("size"));
            child.getCharacteristics().add(size);
        }

        // Add color if different
        if (variant.getAttributes().containsKey("color") &&
                !Objects.equals(variant.getAttributes().get("color"), parent.getAttributes().get("color"))) {

            HoroshopCharacteristic color = new HoroshopCharacteristic();
            color.setName("Цвет");
            color.setValue(variant.getAttributes().get("color"));
            child.getCharacteristics().add(color);
        }

        // Add other variant-specific attributes
        Set<String> variantKeys = new HashSet<>(variant.getAttributes().keySet());
        Set<String> parentKeys = new HashSet<>(parent.getAttributes().keySet());

        variantKeys.removeAll(parentKeys);

        for (String key : variantKeys) {
            HoroshopCharacteristic characteristic = new HoroshopCharacteristic();
            characteristic.setName(translateAttributeName(key));
            characteristic.setValue(variant.getAttributes().get(key));
            child.getCharacteristics().add(characteristic);
        }
    }

    private String translateAttributeName(String attributeName) {
        // Map common attribute names to Ukrainian/Russian
        Map<String, String> translations = Map.of(
                "size", "Размер",
                "color", "Цвет",
                "material", "Материал",
                "brand", "Бренд",
                "model", "Модель",
                "weight", "Вес",
                "length", "Длина",
                "width", "Ширина",
                "height", "Высота",
                "capacity", "Объем"
        );

        return translations.getOrDefault(attributeName.toLowerCase(),
                capitalize(attributeName));
    }

    private String translateCharacteristicName(String characteristicName) {
        // Map Horoshop characteristic names back to internal attribute names
        Map<String, String> translations = Map.of(
                "Размер", "size",
                "Цвет", "color",
                "Материал", "material",
                "Бренд", "brand",
                "Модель", "model",
                "Вес", "weight",
                "Длина", "length",
                "Ширина", "width",
                "Высота", "height",
                "Объем", "capacity"
        );

        return translations.getOrDefault(characteristicName,
                characteristicName.toLowerCase().replaceAll("\\s+", "_"));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Validate Horoshop product before export
     */
    public List<String> validateHoroshopProduct(HoroshopProduct product) {
        List<String> errors = new ArrayList<>();

        // Required fields validation
        if (product.getArticle() == null || product.getArticle().trim().isEmpty()) {
            errors.add("Article/SKU is required");
        }

        if (product.getTitle() == null || product.getTitle().isEmpty()) {
            errors.add("Title is required");
        } else {
            // Check if at least one language is provided
            boolean hasTitle = product.getTitle().values().stream()
                    .anyMatch(title -> title != null && !title.trim().isEmpty());
            if (!hasTitle) {
                errors.add("At least one title language is required");
            }
        }

        if (product.getPrice() == null || product.getPrice() <= 0) {
            errors.add("Valid price is required");
        }

        // Business rule validations
        if (product.getQuantity() != null && product.getQuantity() < 0) {
            errors.add("Quantity cannot be negative");
        }

        if (product.getPrice() != null && product.getPriceOld() != null &&
                product.getPriceOld() <= product.getPrice()) {
            errors.add("Old price should be higher than current price");
        }

        // Image validation
        if (product.getImages() != null && product.getImages().getLinks() != null) {
            for (String imageUrl : product.getImages().getLinks()) {
                if (!isValidUrl(imageUrl)) {
                    errors.add("Invalid image URL: " + imageUrl);
                }
            }
        }

        return errors;
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }

    /**
     * Create test/sample Horoshop product
     */
    public HoroshopProduct createSampleProduct() {
        HoroshopProduct sample = new HoroshopProduct();

        sample.setArticle("SAMPLE_001");
        sample.setParentArticle("SAMPLE_GROUP");

        Map<String, String> titles = new HashMap<>();
        titles.put("ua", "Тестовий товар");
        titles.put("ru", "Тестовый товар");
        titles.put("en", "Test Product");
        sample.setTitle(titles);

        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("ua", "Опис тестового товару для перевірки інтеграції");
        descriptions.put("ru", "Описание тестового товара для проверки интеграции");
        descriptions.put("en", "Test product description for integration testing");
        sample.setDescription(descriptions);

        sample.setPrice(100.0);
        sample.setPriceOld(150.0);
        sample.setQuantity(10);
        sample.setPresence("В наличии");
        sample.setParent("Тестовая категория");

        HoroshopImages images = new HoroshopImages();
        images.setOverride(false);
        images.setLinks(Arrays.asList(
                "https://via.placeholder.com/400x400/0000FF/FFFFFF?text=Test+Product"
        ));
        sample.setImages(images);

        sample.setDisplayInShowcase(true);
        sample.setIcons(Arrays.asList("Тест"));

        List<HoroshopCharacteristic> characteristics = new ArrayList<>();
        HoroshopCharacteristic testChar = new HoroshopCharacteristic();
        testChar.setName("Тип");
        testChar.setValue("Тестовый");
        characteristics.add(testChar);
        sample.setCharacteristics(characteristics);

        return sample;
    }
}