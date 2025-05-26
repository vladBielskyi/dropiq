package com.dropiq.engine.product.support;

import com.dropiq.engine.integration.imp.horoshop.model.HoroshopCharacteristic;
import com.dropiq.engine.integration.imp.horoshop.model.HoroshopImages;
import com.dropiq.engine.integration.imp.horoshop.model.HoroshopProduct;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Покращений маппер для конвертації між Product та Horoshop API
 * - Оптимізований для швидкості
 * - Повністю сумісний з Horoshop API
 * - Підтримка всіх Horoshop функцій
 */
@Slf4j
@Component
public class HoroshopProductMapper {

    private static final DateTimeFormatter HOROSHOP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Конвертація Product в HoroshopProduct (оптимізовано)
     */
    public HoroshopProduct toHoroshopProduct(Product product, DataSet dataset) {
        log.debug("Converting product {} to Horoshop format", product.getName());

        // Підготовка продукту до експорту
        product.prepareForHoroshopExport();

        HoroshopProduct horoshopProduct = new HoroshopProduct();

        // ==============================================
        // БАЗОВА ІДЕНТИФІКАЦІЯ
        // ==============================================

        horoshopProduct.setArticle(product.getHoroshopArticleOrDefault());

        if (product.isVariant()) {
            // Для варіантів встановлюємо parent_article
            horoshopProduct.setParentArticle(product.getGroupId());
        }

        // ==============================================
        // МУЛЬТИМОВНІ ЗАГОЛОВКИ (ОПТИМІЗОВАНО)
        // ==============================================

        Map<String, String> titles = new HashMap<>();
        titles.put("ua", product.getOptimizedTitle("ua"));
        titles.put("ru", product.getOptimizedTitle("ru"));
        horoshopProduct.setTitle(titles);

        // ==============================================
        // МУЛЬТИМОВНІ ОПИСИ
        // ==============================================

        Map<String, String> descriptions = new HashMap<>();
        String descUa = product.getOptimizedDescription("ua");
        String descRu = product.getOptimizedDescription("ru");

        if (descUa != null) {
            descriptions.put("ua", cleanHtmlAndTruncate(descUa, 4000));
        }
        if (descRu != null) {
            descriptions.put("ru", cleanHtmlAndTruncate(descRu, 4000));
        }
        horoshopProduct.setDescription(descriptions);

        // ==============================================
        // КОРОТКІ ОПИСИ (META)
        // ==============================================

        Map<String, String> shortDescriptions = new HashMap<>();
        String metaUa = product.getOptimizedMetaDescription("ua");
        String metaRu = product.getOptimizedMetaDescription("ru");

        if (metaUa != null) {
            shortDescriptions.put("ua", metaUa);
        }
        if (metaRu != null) {
            shortDescriptions.put("ru", metaRu);
        }
        horoshopProduct.setShortDescription(shortDescriptions);

        // ==============================================
        // ЦІНИ
        // ==============================================

        if (product.getSellingPrice() != null) {
            horoshopProduct.setPrice(product.getSellingPrice().doubleValue());
        }

        // Стара ціна (якщо є знижка)
        if (product.getOriginalPrice() != null &&
                product.getSellingPrice() != null &&
                product.getOriginalPrice().compareTo(product.getSellingPrice()) > 0) {
            horoshopProduct.setPriceOld(product.getOriginalPrice().doubleValue());
        }

        // ==============================================
        // НАЯВНІСТЬ ТА СКЛАД
        // ==============================================

        horoshopProduct.setQuantity(product.getStock() != null ? product.getStock() : 0);
        horoshopProduct.setPresence(product.getPresence());

        // ==============================================
        // КАТЕГОРІЇ
        // ==============================================

        if (product.getHoroshopCategoryPath() != null) {
            horoshopProduct.setParent(product.getHoroshopCategoryPath());
        } else if (product.getCategory() != null) {
            horoshopProduct.setParent(buildCategoryPath(product.getCategory()));
        }

        // ==============================================
        // ЗОБРАЖЕННЯ
        // ==============================================

        if (!product.getImageUrls().isEmpty()) {
            HoroshopImages images = new HoroshopImages();
            images.setOverride(false); // Додаємо до існуючих
            images.setLinks(new ArrayList<>(product.getImageUrls()));
            horoshopProduct.setImages(images);
        }

        // ==============================================
        // SEO ТА URL
        // ==============================================

        if (product.getCategory() != null && product.getCategory().getSlug() != null) {
            horoshopProduct.setSlug(generateProductSlug(product));
        }

        // ==============================================
        // ХАРАКТЕРИСТИКИ (HOROSHOP СПЕЦИФІЧНІ)
        // ==============================================

        List<HoroshopCharacteristic> characteristics = buildHoroshopCharacteristics(product);
        horoshopProduct.setCharacteristics(characteristics);

        // ==============================================
        // МАРКЕТИНГОВІ ІКОНКИ
        // ==============================================

        List<String> icons = generateMarketingIcons(product);
        horoshopProduct.setIcons(icons);

        // ==============================================
        // НАЛАШТУВАННЯ ВІДОБРАЖЕННЯ
        // ==============================================

        horoshopProduct.setDisplayInShowcase(true);
        horoshopProduct.setForceAliasUpdate(false);

        // Популярність на основі trend score
        if (product.getTrendScore() != null) {
            int popularity = Math.min(100,
                    (int) (product.getTrendScore().doubleValue() * 10));
            horoshopProduct.setPopularity(popularity);
        }

        // ==============================================
        // ЕКСПОРТ НА МАРКЕТПЛЕЙСИ
        // ==============================================

        horoshopProduct.setExportToMarketplace(generateMarketplaceExport(product));

        // ==============================================
        // ГАРАНТІЇ ТА АКЦІЇ
        // ==============================================

        setupGuaranteesAndPromotions(product, horoshopProduct);

        log.debug("Successfully converted product {} to Horoshop format", product.getName());
        return horoshopProduct;
    }

    /**
     * Конвертація HoroshopProduct назад в Product
     */
    public void updateFromHoroshopProduct(Product product, HoroshopProduct horoshopProduct) {
        log.debug("Updating product {} from Horoshop data", product.getName());

        // Оновлення наявності та складу
        if (horoshopProduct.getQuantity() != null) {
            product.setStock(horoshopProduct.getQuantity());
        }

        if (horoshopProduct.getPresence() != null) {
            product.setPresence(horoshopProduct.getPresence());
            product.setAvailable("В наличии".equals(horoshopProduct.getPresence()));
        }

        // Оновлення цін
        if (horoshopProduct.getPrice() != null) {
            product.setSellingPrice(java.math.BigDecimal.valueOf(horoshopProduct.getPrice()));
        }

        // Оновлення зображень
        if (horoshopProduct.getImages() != null &&
                !horoshopProduct.getImages().getLinks().isEmpty()) {

            if (horoshopProduct.getImages().getOverride()) {
                product.getImageUrls().clear();
            }

            // Додаєм нові зображення
            horoshopProduct.getImages().getLinks().forEach(imageUrl -> {
                product.addImageUrl(imageUrl);
            });
        }

        // Оновлення статусу синхронізації
        product.setLastSync(LocalDateTime.now());
        product.updateHoroshopExportStatus(true, "Updated from Horoshop");

        log.debug("Successfully updated product {} from Horoshop data", product.getName());
    }

    /**
     * Створення характеристик для Horoshop
     */
    private List<HoroshopCharacteristic> buildHoroshopCharacteristics(Product product) {
        List<HoroshopCharacteristic> characteristics = new ArrayList<>();

        // Основні характеристики товару
//        if (product.getBrand() != null) {
//            characteristics.add(new HoroshopCharacteristic("Бренд", product.getBrand()));
//        }
//
//        if (product.getModel() != null) {
//            characteristics.add(new HoroshopCharacteristic("Модель", product.getModel()));
//        }
//
//        if (product.getColor() != null) {
//            characteristics.add(new HoroshopCharacteristic("Цвет", product.getColor()));
//        }
//
//        if (product.getSize() != null) {
//            characteristics.add(new HoroshopCharacteristic("Размер", product.getSize()));
//        }
//
//        if (product.getMaterial() != null) {
//            characteristics.add(new HoroshopCharacteristic("Материал", product.getMaterial()));
//        }
//
//        if (product.getGender() != null) {
//            String genderText = translateGender(product.getGender());
//            characteristics.add(new HoroshopCharacteristic("Пол", genderText));
//        }
//
//        if (product.getSeason() != null) {
//            String seasonText = translateSeason(product.getSeason());
//            characteristics.add(new HoroshopCharacteristic("Сезон", seasonText));
//        }

        // Додаткові атрибути з мапи
        product.getAttributes().forEach((key, value) -> {
            if (value != null && !value.trim().isEmpty() &&
                    !isBasicAttribute(key)) {
                String translatedKey = translateAttributeName(key);
                // characteristics.add(new HoroshopCharacteristic(translatedKey, value));
            }
        });

        // AI згенеровані характеристики
//        if (product.getTrendScore() != null && product.getTrendScore().doubleValue() >= 7.0) {
//            characteristics.add(new HoroshopCharacteristic("Рейтинг", "Популярный товар"));
//        }
//
//        if (product.isNew()) {
//            characteristics.add(new HoroshopCharacteristic("Новинка", "Да"));
//        }

        return characteristics.stream()
                .limit(15) // Обмежуємо кількість характеристик
                .collect(Collectors.toList());
    }

    /**
     * Генерація маркетингових іконок
     */
    private List<String> generateMarketingIcons(Product product) {
        List<String> icons = new ArrayList<>();

        // Хіт продажів
        if (product.isPopular()) {
            icons.add("Хит");
        }

        // Новинка
        if (product.isNew()) {
            icons.add("Новинка");
        }

        // Розпродаж
        if (product.getOriginalPrice() != null &&
                product.getSellingPrice() != null &&
                product.getOriginalPrice().compareTo(product.getSellingPrice()) > 0) {
            icons.add("Распродажа");
        }

        // Низький залишок
        if (product.hasLowStock()) {
            icons.add("Последние");
        }

        // Преміум якість
        if (product.getTrendScore() != null &&
                product.getTrendScore().doubleValue() >= 8.0) {
            icons.add("Премиум");
        }

        return icons;
    }

    /**
     * Налаштування експорту на маркетплейси
     */
    private String generateMarketplaceExport(Product product) {
        List<String> marketplaces = new ArrayList<>();

        // Базові маркетплейси для всіх товарів
        marketplaces.add("Facebook Feed");

        // Популярні товари на більше маркетплейсів
        if (product.isPopular()) {
            marketplaces.add("Rozetka Feed");
            marketplaces.add("Prom.ua Feed");
        }

        // Преміумні товари на Google Shopping
        if (product.getSellingPrice() != null &&
                product.getSellingPrice().doubleValue() > 1000) {
            marketplaces.add("Google Shopping");
        }

        return String.join(";", marketplaces);
    }

    /**
     * Налаштування гарантій та акцій
     */
    private void setupGuaranteesAndPromotions(Product product, HoroshopProduct horoshopProduct) {
        // Гарантія магазину
        if (product.getBrand() != null &&
                !product.getBrand().toLowerCase().contains("noname")) {
            horoshopProduct.setGuaranteeShop("Гарантия качества");
            horoshopProduct.setGuaranteeLength(12); // 12 місяців
        }

        // Акційні таймери для популярних товарів
        if (product.isPopular() && product.hasLowStock()) {
            LocalDateTime endTime = LocalDateTime.now().plusDays(3);
            horoshopProduct.setCountdownEndTime(endTime.format(HOROSHOP_DATE_FORMAT));

            Map<String, String> countdownDesc = new HashMap<>();
            countdownDesc.put("ua", "Встигни купити! Залишилось мало!");
            countdownDesc.put("ru", "Успей купить! Осталось мало!");
            horoshopProduct.setCountdownDescription(countdownDesc);
        }
    }

    /**
     * Створення варіантної групи продуктів
     */
    public List<HoroshopProduct> createVariantGroup(List<Product> variants, DataSet dataset) {
        if (variants.isEmpty()) {
            return new ArrayList<>();
        }

        List<HoroshopProduct> result = new ArrayList<>();

        // Знаходимо головний продукт (з найкращими зображеннями або описом)
        Product mainProduct = variants.stream()
                .max(Comparator.comparing(this::calculateProductQuality))
                .orElse(variants.get(0));

        // Створюємо головний продукт
        HoroshopProduct parentProduct = toHoroshopProduct(mainProduct, dataset);
        parentProduct.setParentArticle(mainProduct.getGroupId());
        result.add(parentProduct);

        // Створюємо варіанти
        for (Product variant : variants) {
            if (!variant.getId().equals(mainProduct.getId())) {
                HoroshopProduct childProduct = toHoroshopProduct(variant, dataset);
                childProduct.setParentArticle(mainProduct.getHoroshopArticleOrDefault());

                // Додаємо варіант-специфічні характеристики
                addVariantSpecificCharacteristics(childProduct, variant, mainProduct);
                result.add(childProduct);
            }
        }

        return result;
    }

    /**
     * Додавання варіант-специфічних характеристик
     */
    private void addVariantSpecificCharacteristics(HoroshopProduct childProduct,
                                                   Product variant, Product mainProduct) {

        List<HoroshopCharacteristic> variantCharacteristics = new ArrayList<>(childProduct.getCharacteristics());

        // Розмір (якщо відрізняється)
//        if (variant.getSize() != null && !variant.getSize().equals(mainProduct.getSize())) {
//            variantCharacteristics.add(new HoroshopCharacteristic("Размер варианта", variant.getSize()));
//        }
//
//        // Колір (якщо відрізняється)
//        if (variant.getColor() != null && !variant.getColor().equals(mainProduct.getColor())) {
//            variantCharacteristics.add(new HoroshopCharacteristic("Цвет варианта", variant.getColor()));
//        }

        // Інші відмінності
        variant.getAttributes().forEach((key, value) -> {
            String mainValue = mainProduct.getAttribute(key);
            if (value != null && !value.equals(mainValue)) {
                String translatedKey = translateAttributeName(key) + " варианта";
              //  variantCharacteristics.add(new HoroshopCharacteristic(translatedKey, value));
            }
        });

        childProduct.setCharacteristics(variantCharacteristics);
    }

    /**
     * Batch конвертація продуктів
     */
    public List<HoroshopProduct> toHoroshopProducts(List<Product> products, DataSet dataset) {
        return products.parallelStream()
                .filter(Product::isReadyForHoroshop)
                .map(product -> toHoroshopProduct(product, dataset))
                .collect(Collectors.toList());
    }

    /**
     * Валідація HoroshopProduct перед експортом
     */
    public ValidationResult validateHoroshopProduct(HoroshopProduct product) {
        ValidationResult result = new ValidationResult();

        // Обов'язкові поля
        if (product.getArticle() == null || product.getArticle().trim().isEmpty()) {
            result.addError("Article/SKU обов'язковий");
        }

        if (product.getTitle() == null || product.getTitle().isEmpty()) {
            result.addError("Назва товару обов'язкова");
        } else {
            boolean hasValidTitle = product.getTitle().values().stream()
                    .anyMatch(title -> title != null && title.trim().length() >= 10);
            if (!hasValidTitle) {
                result.addError("Потрібна назва довжиною мінімум 10 символів");
            }
        }

        if (product.getPrice() == null || product.getPrice() <= 0) {
            result.addError("Потрібна коректна ціна");
        }

        // Бізнес-правила
        if (product.getQuantity() != null && product.getQuantity() < 0) {
            result.addError("Кількість не може бути від'ємною");
        }

        if (product.getPrice() != null && product.getPriceOld() != null &&
                product.getPriceOld() <= product.getPrice()) {
            result.addWarning("Стара ціна повинна бути вищою за поточну");
        }

        // Валідація зображень
        if (product.getImages() != null && product.getImages().getLinks() != null) {
            for (String imageUrl : product.getImages().getLinks()) {
                if (!isValidImageUrl(imageUrl)) {
                    result.addWarning("Невалідний URL зображення: " + imageUrl);
                }
            }
        }

        return result;
    }

    // ===============================================
    // ДОПОМІЖНІ МЕТОДИ
    // ===============================================

    private String buildCategoryPath(com.dropiq.engine.product.entity.DatasetCategory category) {
        if (category.getParent() != null) {
            return buildCategoryPath(category.getParent()) + " / " + category.getNameUk();
        }
        return category.getNameUk();
    }

    private String generateProductSlug(Product product) {
        String base = product.getName().toLowerCase()
                .replaceAll("[^a-z0-9а-я\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (product.getCategory() != null) {
            return product.getCategory().getSlug() + "/" + base;
        }

        return base;
    }

    private String cleanHtmlAndTruncate(String html, int maxLength) {
        if (html == null) return "";

        String cleaned = html.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();

        return cleaned.length() > maxLength ?
                cleaned.substring(0, maxLength - 3) + "..." : cleaned;
    }

    private String translateGender(String gender) {
        return switch (gender.toLowerCase()) {
            case "male", "чоловічий", "мужской" -> "Мужской";
            case "female", "жіночий", "женский" -> "Женский";
            case "unisex", "унісекс" -> "Унисекс";
            default -> gender;
        };
    }

    private String translateSeason(String season) {
        return switch (season.toLowerCase()) {
            case "spring", "весна" -> "Весна";
            case "summer", "літо", "лето" -> "Лето";
            case "autumn", "fall", "осінь", "осень" -> "Осень";
            case "winter", "зима" -> "Зима";
            case "all-season", "всесезон" -> "Всесезонный";
            default -> season;
        };
    }

    private String translateAttributeName(String attributeName) {
        Map<String, String> translations = Map.of(
        );

        return translations.getOrDefault(attributeName.toLowerCase(),
                capitalize(attributeName));
    }

    private boolean isBasicAttribute(String key) {
        Set<String> basicAttributes = Set.of(
                "size", "color", "material", "brand", "model", "gender", "season"
        );
        return basicAttributes.contains(key.toLowerCase());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private boolean isValidImageUrl(String url) {
        try {
            new java.net.URL(url);
            return url.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|webp).*");
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }

    private int calculateProductQuality(Product product) {
        int score = 0;

        // Кількість зображень
        score += product.getImageUrls().size() * 5;

        // Якість опису
        if (product.getDescriptionUa() != null) {
            score += Math.min(product.getDescriptionUa().length() / 10, 50);
        }

        // AI аналіз
        if (product.getAiAnalyzed()) {
            score += 20;
        }

        // SEO оптимізація
        if (product.getSeoOptimized()) {
            score += 15;
        }

        return score;
    }

    /**
     * Створення тестового HoroshopProduct
     */
    public HoroshopProduct createTestProduct() {
        HoroshopProduct testProduct = new HoroshopProduct();

        testProduct.setArticle("TEST_001");
        testProduct.setParentArticle("TEST_GROUP");

        Map<String, String> titles = new HashMap<>();
        titles.put("ua", "Тестовий товар для інтеграції");
        titles.put("ru", "Тестовый товар для интеграции");
        testProduct.setTitle(titles);

        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("ua", "Детальний опис тестового товару для перевірки роботи API");
        descriptions.put("ru", "Подробное описание тестового товара для проверки работы API");
        testProduct.setDescription(descriptions);

        testProduct.setPrice(500.0);
        testProduct.setPriceOld(600.0);
        testProduct.setQuantity(10);
        testProduct.setPresence("В наличии");
        testProduct.setParent("Тестовая категория");

        HoroshopImages images = new HoroshopImages();
        images.setOverride(false);
        images.setLinks(List.of("https://via.placeholder.com/400x400/FF0000/FFFFFF?text=TEST"));
        testProduct.setImages(images);

        testProduct.setDisplayInShowcase(true);
        testProduct.setIcons(List.of("Тест", "Новинка"));

        List<HoroshopCharacteristic> characteristics = new ArrayList<>();
//        characteristics.add(new HoroshopCharacteristic("Тип", "Тестовый"));
//        characteristics.add(new HoroshopCharacteristic("Назначение", "Проверка API"));
        testProduct.setCharacteristics(characteristics);

        return testProduct;
    }

    // ===============================================
    // ДОПОМІЖНІ КЛАСИ
    // ===============================================

    @lombok.Data
    public static class ValidationResult {
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}