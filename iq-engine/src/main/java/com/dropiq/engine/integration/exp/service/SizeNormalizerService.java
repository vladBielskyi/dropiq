package com.dropiq.engine.integration.exp.service;

import com.dropiq.engine.integration.exp.model.UnifiedProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SizeNormalizerService {

    // Паттерни для розпізнавання розмірів
    private static final Pattern CLOTHING_ALPHA_PATTERN = Pattern.compile("^(\\d*)(XS|S|M|L|XL|XXL|3XL|4XL|5XL)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOTHING_NUMERIC_PATTERN = Pattern.compile("^(\\d{2})$");
    private static final Pattern SHOES_PATTERN = Pattern.compile("^(\\d{2}(?:\\.5)?)$");
    private static final Pattern COMBINED_PATTERN = Pattern.compile("^(\\w+)/(\\w+)$");
    private static final Pattern PANTS_PATTERN = Pattern.compile("^(\\d{2,3})$");

    // Мапінг синонімів розмірів
    private static final Map<String, String> SIZE_SYNONYMS = new HashMap<>();
    static {
        // Альфа розміри
        SIZE_SYNONYMS.put("SMALL", "S");
        SIZE_SYNONYMS.put("MEDIUM", "M");
        SIZE_SYNONYMS.put("LARGE", "L");
        SIZE_SYNONYMS.put("EXTRA LARGE", "XL");
        SIZE_SYNONYMS.put("EXTRA-LARGE", "XL");

        // Комбіновані розміри
        SIZE_SYNONYMS.put("S-M", "S/M");
        SIZE_SYNONYMS.put("M-L", "M/L");
        SIZE_SYNONYMS.put("L-XL", "L/XL");
        SIZE_SYNONYMS.put("XL-XXL", "XL/XXL");
        SIZE_SYNONYMS.put("2XL/3XL", "XXL/3XL");

        // Відсутність розміру
        SIZE_SYNONYMS.put("-", "ONE_SIZE");
        SIZE_SYNONYMS.put("", "ONE_SIZE");
        SIZE_SYNONYMS.put("ONE SIZE", "ONE_SIZE");
        SIZE_SYNONYMS.put("ONESIZE", "ONE_SIZE");
        SIZE_SYNONYMS.put("FREE SIZE", "ONE_SIZE");
        SIZE_SYNONYMS.put("УНИВЕРСАЛЬНЫЙ", "ONE_SIZE");
    }

    // Категорії товарів для визначення типу розміру
    private static final Map<String, UnifiedProduct.ProductSize.SizeType> CATEGORY_SIZE_MAPPING = new HashMap<>();
    static {
        // Одяг
        CATEGORY_SIZE_MAPPING.put("футболк", UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);
        CATEGORY_SIZE_MAPPING.put("куртк", UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);
        CATEGORY_SIZE_MAPPING.put("худі", UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);
        CATEGORY_SIZE_MAPPING.put("свитш", UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);
        CATEGORY_SIZE_MAPPING.put("жилет", UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);
        CATEGORY_SIZE_MAPPING.put("білизн", UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);

        // Штани
        CATEGORY_SIZE_MAPPING.put("штан", UnifiedProduct.ProductSize.SizeType.PANTS_WAIST);
        CATEGORY_SIZE_MAPPING.put("джинс", UnifiedProduct.ProductSize.SizeType.PANTS_WAIST);
        CATEGORY_SIZE_MAPPING.put("шорт", UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);

        // Взуття
        CATEGORY_SIZE_MAPPING.put("кросівк", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("черевик", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("туфл", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("босоніж", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("сандал", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("шлепк", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("yeezy", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("nike", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("adidas", UnifiedProduct.ProductSize.SizeType.SHOES_EU);
        CATEGORY_SIZE_MAPPING.put("jordan", UnifiedProduct.ProductSize.SizeType.SHOES_EU);

        // Аксесуари
        CATEGORY_SIZE_MAPPING.put("окуляр", UnifiedProduct.ProductSize.SizeType.ACCESSORIES);
        CATEGORY_SIZE_MAPPING.put("сумк", UnifiedProduct.ProductSize.SizeType.ACCESSORIES);
        CATEGORY_SIZE_MAPPING.put("рюкзак", UnifiedProduct.ProductSize.SizeType.ACCESSORIES);
        CATEGORY_SIZE_MAPPING.put("гаман", UnifiedProduct.ProductSize.SizeType.ACCESSORIES);
        CATEGORY_SIZE_MAPPING.put("шапк", UnifiedProduct.ProductSize.SizeType.ACCESSORIES);
        CATEGORY_SIZE_MAPPING.put("панамк", UnifiedProduct.ProductSize.SizeType.ACCESSORIES);
    }

    /**
     * Нормалізує розмір товару
     */
    public UnifiedProduct.ProductSize normalizeSize(String originalSize, String productName, String categoryName) {
        if (originalSize == null) {
            originalSize = "";
        }

        UnifiedProduct.ProductSize productSize = new UnifiedProduct.ProductSize();
        productSize.setOriginalValue(originalSize.trim());

        try {
            // Визначаємо тип розміру на основі категорії та назви товару
            UnifiedProduct.ProductSize.SizeType expectedType = determineSizeType(productName, categoryName);

            // Нормалізуємо значення
            String normalized = normalizeSizeValue(originalSize);

            // Парсимо розмір
            parseSizeValue(productSize, normalized, expectedType);

            log.debug("Normalized size '{}' -> Type: {}, Value: {}",
                    originalSize, productSize.getType(), productSize.getNormalizedValue());

        } catch (Exception e) {
            log.warn("Error normalizing size '{}': {}", originalSize, e.getMessage());
            productSize.setType(UnifiedProduct.ProductSize.SizeType.UNKNOWN);
            productSize.setNormalizedValue("UNKNOWN");
        }

        return productSize;
    }

    /**
     * Визначає тип розміру на основі назви товару та категорії
     */
    private UnifiedProduct.ProductSize.SizeType determineSizeType(String productName, String categoryName) {
        String searchText = ((productName != null ? productName : "") + " " +
                (categoryName != null ? categoryName : "")).toLowerCase();

        // Шукаємо збіги в мапінгу категорій
        for (Map.Entry<String, UnifiedProduct.ProductSize.SizeType> entry : CATEGORY_SIZE_MAPPING.entrySet()) {
            if (searchText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return UnifiedProduct.ProductSize.SizeType.UNKNOWN;
    }

    /**
     * Нормалізує значення розміру (очищає, приводить до єдиного формату)
     */
    private String normalizeSizeValue(String size) {
        if (size == null || size.trim().isEmpty()) {
            return "ONE_SIZE";
        }

        String normalized = size.trim().toUpperCase();

        // Перевіряємо синоніми
        if (SIZE_SYNONYMS.containsKey(normalized)) {
            return SIZE_SYNONYMS.get(normalized);
        }

        // Очищаємо від зайвих символів
        normalized = normalized.replaceAll("[^A-Z0-9/.-]", "");

        return normalized;
    }

    /**
     * Парсить значення розміру та визначає його тип
     */
    private void parseSizeValue(UnifiedProduct.ProductSize productSize, String normalized,
                                UnifiedProduct.ProductSize.SizeType expectedType) {

        // Спеціальні випадки
        if ("ONE_SIZE".equals(normalized) || "-".equals(normalized) || normalized.isEmpty()) {
            productSize.setType(UnifiedProduct.ProductSize.SizeType.ACCESSORIES);
            productSize.setNormalizedValue("ONE_SIZE");
            return;
        }

        // Перевіряємо комбіновані розміри (S/M, L/XL, etc.)
        Matcher combinedMatcher = COMBINED_PATTERN.matcher(normalized);
        if (combinedMatcher.matches()) {
            productSize.setType(UnifiedProduct.ProductSize.SizeType.COMBINED);
            productSize.setNormalizedValue(normalized);

            // Розділяємо на окремі розміри
            String[] sizes = normalized.split("/");
            for (int i = 0; i < sizes.length; i++) {
                productSize.getAdditionalSizes().put("size" + (i + 1), sizes[i]);
            }
            return;
        }

        // Перевіряємо альфа розміри (S, M, L, XL, etc.)
        Matcher alphaMatcher = CLOTHING_ALPHA_PATTERN.matcher(normalized);
        if (alphaMatcher.matches()) {
            productSize.setType(UnifiedProduct.ProductSize.SizeType.CLOTHING_ALPHA);
            productSize.setNormalizedValue(alphaMatcher.group(2));
            return;
        }

        // Перевіряємо числові розміри
        if (normalized.matches("\\d+")) {
            int sizeNum = Integer.parseInt(normalized);

            if (expectedType == UnifiedProduct.ProductSize.SizeType.SHOES_EU && sizeNum >= 35 && sizeNum <= 50) {
                productSize.setType(UnifiedProduct.ProductSize.SizeType.SHOES_EU);
            } else if (sizeNum >= 28 && sizeNum <= 46 && sizeNum % 2 == 0) {
                productSize.setType(UnifiedProduct.ProductSize.SizeType.CLOTHING_NUMERIC);
            } else if (sizeNum >= 28 && sizeNum <= 40) {
                productSize.setType(UnifiedProduct.ProductSize.SizeType.PANTS_WAIST);
            } else {
                productSize.setType(UnifiedProduct.ProductSize.SizeType.UNKNOWN);
            }

            productSize.setNormalizedValue(normalized);
            return;
        }

        // Якщо нічого не підійшло
        productSize.setType(UnifiedProduct.ProductSize.SizeType.UNKNOWN);
        productSize.setNormalizedValue(normalized.isEmpty() ? "UNKNOWN" : normalized);
    }

    /**
     * Перевіряє, чи є розмір валідним для заданого типу
     */
    public boolean isValidSizeForType(String size, UnifiedProduct.ProductSize.SizeType type) {
        if (size == null || type == null) {
            return false;
        }

        String[] commonValues = type.getCommonValues();
        if (commonValues != null) {
            return Arrays.asList(commonValues).contains(size.toUpperCase());
        }

        return true;
    }

    /**
     * Отримує рекомендовані розміри для типу товару
     */
    public String[] getRecommendedSizes(UnifiedProduct.ProductSize.SizeType type) {
        return type.getCommonValues();
    }
}