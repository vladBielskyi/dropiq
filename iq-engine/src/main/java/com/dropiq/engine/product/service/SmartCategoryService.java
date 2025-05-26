package com.dropiq.engine.product.service;

import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.DatasetCategory;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.repository.DatasetCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SmartCategoryService {

    private final DatasetCategoryRepository categoryRepository;

    // Обмеження для побудови дерева
    private static final int MAX_ROOT_CATEGORIES = 15;
    private static final int MAX_CHILDREN_PER_CATEGORY = 8;
    private static final int MAX_DEPTH = 2; // Root -> Sub (2 рівні максимум)

    /**
     * Автоматично будуємо дерево категорій для датасету
     */
    @Transactional
    public void buildCategoryTreeForDataset(DataSet dataset) {
        log.info("Building smart category tree for dataset: {}", dataset.getName());

        // Збираємо всі унікальні категорії з продуктів
        Map<String, List<Product>> categoryGroups = dataset.getProducts().stream()
                .filter(p -> p.getExternalCategoryName() != null)
                .collect(Collectors.groupingBy(Product::getExternalCategoryName));

        log.info("Found {} unique categories in dataset", categoryGroups.size());

        // Аналізуємо та нормалізуємо назви категорій
        Map<String, CategoryInfo> normalizedCategories = analyzeAndNormalizeCategories(categoryGroups);

        // Будуємо дерево на основі аналізу
        buildOptimalTree(dataset, normalizedCategories);

        // Призначуємо продукти до категорій
        assignProductsToCategories(dataset, normalizedCategories);

        log.info("Category tree built successfully for dataset: {}", dataset.getName());
    }

    /**
     * Аналізуємо категорії та нормалізуємо їх
     */
    private Map<String, CategoryInfo> analyzeAndNormalizeCategories(
            Map<String, List<Product>> categoryGroups) {

        Map<String, CategoryInfo> normalized = new HashMap<>();

        for (Map.Entry<String, List<Product>> entry : categoryGroups.entrySet()) {
            String originalName = entry.getKey();
            List<Product> products = entry.getValue();

            CategoryInfo info = new CategoryInfo();
            info.originalName = originalName;
            info.productCount = products.size();
            info.products = products;
            info.normalizedName = normalizeCategoryName(originalName);
            info.parentCandidate = extractParentCategory(originalName);
            info.priority = calculateCategoryPriority(products);

            normalized.put(info.normalizedName, info);
        }

        return normalized;
    }

    /**
     * Будуємо оптимальне дерево категорій
     */
    private void buildOptimalTree(DataSet dataset, Map<String, CategoryInfo> categories) {
        // Групуємо категорії за потенційними батьківськими категоріями
        Map<String, List<CategoryInfo>> parentGroups = categories.values().stream()
                .collect(Collectors.groupingBy(info ->
                        info.parentCandidate != null ? info.parentCandidate : "ROOT"));

        // Створюємо кореневі категорії
        List<String> rootCategories = selectRootCategories(parentGroups);

        for (String rootName : rootCategories) {
            DatasetCategory rootCategory = createOrGetCategory(dataset, null, rootName);

            // Додаємо дочірні категорії
            List<CategoryInfo> children = parentGroups.getOrDefault(rootName, new ArrayList<>());
            children.sort((a, b) -> Integer.compare(b.productCount, a.productCount)); // За кількістю продуктів

            int childCount = 0;
            for (CategoryInfo child : children) {
                if (childCount >= MAX_CHILDREN_PER_CATEGORY) break;

                createOrGetCategory(dataset, rootCategory, child.normalizedName);
                childCount++;
            }
        }

        // Створюємо категорію "Інше" для решти
        if (parentGroups.containsKey("ROOT")) {
            DatasetCategory otherCategory = createOrGetCategory(dataset, null, "Інше");
            for (CategoryInfo orphan : parentGroups.get("ROOT")) {
                createOrGetCategory(dataset, otherCategory, orphan.normalizedName);
            }
        }
    }

    /**
     * Вибираємо найкращі кореневі категорії
     */
    private List<String> selectRootCategories(Map<String, List<CategoryInfo>> parentGroups) {
        return parentGroups.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("ROOT"))
                .sorted((a, b) -> {
                    // Сортуємо за загальною кількістю продуктів у групі
                    int countA = a.getValue().stream().mapToInt(info -> info.productCount).sum();
                    int countB = b.getValue().stream().mapToInt(info -> info.productCount).sum();
                    return Integer.compare(countB, countA);
                })
                .limit(MAX_ROOT_CATEGORIES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Призначуємо продукти до створених категорій
     */
    private void assignProductsToCategories(DataSet dataset, Map<String, CategoryInfo> categories) {
        for (CategoryInfo info : categories.values()) {
            Optional<DatasetCategory> categoryOpt = findCategoryByName(dataset, info.normalizedName);

            if (categoryOpt.isPresent()) {
                DatasetCategory category = categoryOpt.get();
                for (Product product : info.products) {
                    product.setCategory(category);
                    category.addProduct(product);
                }
            }
        }
    }

    /**
     * Створюємо або отримуємо існуючу категорію
     */
    private DatasetCategory createOrGetCategory(DataSet dataset, DatasetCategory parent, String name) {
        Optional<DatasetCategory> existing = findCategoryByName(dataset, name);
        if (existing.isPresent()) {
            return existing.get();
        }

        DatasetCategory category = new DatasetCategory();
        category.setDataset(dataset);
        category.setParent(parent);
        category.setNameUk(translateToUkrainian(name));
        category.setNameRu(name); // Основна мова
        category.setNameEn(translateToEnglish(name));
        category.setLevel(parent == null ? 0 : parent.getLevel() + 1);
        category.setAiGenerated(true);
        category.setAiConfidence(0.85);

        if (parent != null) {
            parent.getChildren().add(category);
        }

        category = categoryRepository.save(category);
        log.debug("Created category: {} (parent: {})", name,
                parent != null ? parent.getNameRu() : "ROOT");

        return category;
    }

    /**
     * Нормалізуємо назву категорії
     */
    private String normalizeCategoryName(String categoryName) {
        if (categoryName == null) return "Інше";

        // Видаляємо спеціальні символи та зайві пробіли
        String normalized = categoryName.trim()
                .replaceAll("[^а-яА-Яa-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", " ");

        // Стандартизуємо назви
        Map<String, String> replacements = Map.of(
                "Жіночий одяг", "Жіночий одяг",
                "Чоловічий одяг", "Чоловічий одяг",
                "Дитячий одяг", "Дитячий одяг",
                "Взуття", "Взуття",
                "Аксесуари", "Аксесуари",
                "Спорт", "Спорт і відпочинок",
                "Електроніка", "Електроніка",
                "Дім і сад", "Дім і сад",
                "Краса", "Краса і здоров'я"
        );

        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            if (normalized.toLowerCase().contains(replacement.getKey().toLowerCase())) {
                return replacement.getValue();
            }
        }

        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    /**
     * Витягуємо батьківську категорію з назви
     */
    private String extractParentCategory(String categoryName) {
        if (categoryName == null) return null;

        String lower = categoryName.toLowerCase();

        // Одяг
        if (lower.contains("жіноч") || lower.contains("женск")) return "Жіночий одяг";
        if (lower.contains("чолов") || lower.contains("мужск")) return "Чоловічий одяг";
        if (lower.contains("дитяч") || lower.contains("детск")) return "Дитячий одяг";

        // Взуття
        if (lower.contains("взутт") || lower.contains("обув") ||
                lower.contains("кросс") || lower.contains("сапог")) return "Взуття";

        // Аксесуари
        if (lower.contains("сумк") || lower.contains("аксес") ||
                lower.contains("ремен") || lower.contains("очк")) return "Аксесуари";

        // Спорт
        if (lower.contains("спорт") || lower.contains("фітнес")) return "Спорт і відпочинок";

        // Електроніка
        if (lower.contains("електр") || lower.contains("телеф") ||
                lower.contains("комп'ют")) return "Електроніка";

        // Дім
        if (lower.contains("дім") || lower.contains("дом") ||
                lower.contains("кухн") || lower.contains("ванн")) return "Дім і сад";

        // Краса
        if (lower.contains("крас") || lower.contains("косм") ||
                lower.contains("парфум")) return "Краса і здоров'я";

        return null; // Буде в ROOT
    }

    /**
     * Розраховуємо пріоритет категорії
     */
    private int calculateCategoryPriority(List<Product> products) {
        int priority = products.size(); // Базовий пріоритет за кількістю

        // Додаємо бонуси за якість товарів
        long availableCount = products.stream()
                .filter(Product::getAvailable)
                .count();

        priority += (int) availableCount; // Бонус за доступні товари

        // Бонус за товари з високим трендом
        long trendingCount = products.stream()
                .filter(Product::isPopular)
                .count();

        priority += (int) (trendingCount * 2); // Подвійний бонус за популярні

        return priority;
    }

    /**
     * Знаходимо категорію за назвою
     */
    private Optional<DatasetCategory> findCategoryByName(DataSet dataset, String name) {
        return categoryRepository.findByDataset(dataset).stream()
                .filter(cat -> name.equals(cat.getNameRu()) ||
                        name.equals(cat.getNameUk()) ||
                        name.equals(cat.getNameEn()))
                .findFirst();
    }

    /**
     * Переклад на українську (спрощений)
     */
    private String translateToUkrainian(String russian) {
        if (russian == null) return null;

        Map<String, String> translations = Map.of(
                "Женская одежда", "Жіночий одяг",
                "Мужская одежда", "Чоловічий одяг",
                "Детская одежда", "Дитячий одяг",
                "Обувь", "Взуття",
                "Аксессуары", "Аксесуари",
                "Спорт", "Спорт і відпочинок",
                "Электроника", "Електроніка",
                "Дом и сад", "Дім і сад",
                "Красота", "Краса і здоров'я",
                "Прочее", "Інше"
        );

        return translations.getOrDefault(russian, russian);
    }

    /**
     * Переклад на англійську (спрощений)
     */
    private String translateToEnglish(String russian) {
        if (russian == null) return null;

        Map<String, String> translations = Map.of(
                "Жіночий одяг", "Women's Clothing",
                "Чоловічий одяг", "Men's Clothing",
                "Дитячий одяг", "Kids' Clothing",
                "Взуття", "Footwear",
                "Аксесуари", "Accessories",
                "Спорт і відпочинок", "Sports & Recreation",
                "Електроніка", "Electronics",
                "Дім і сад", "Home & Garden",
                "Краса і здоров'я", "Beauty & Health",
                "Інше", "Other"
        );

        return translations.getOrDefault(russian, russian);
    }

    /**
     * Отримуємо дерево категорій для датасету
     */
    public List<DatasetCategory> getCategoryTree(DataSet dataset) {
        return categoryRepository.findByDatasetAndParentIsNullOrderByProductCountDesc(dataset);
    }

    /**
     * Допоміжний клас для інформації про категорію
     */
    private static class CategoryInfo {
        String originalName;
        String normalizedName;
        String parentCandidate;
        int productCount;
        int priority;
        List<Product> products;
    }
}

