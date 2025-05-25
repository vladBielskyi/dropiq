package com.dropiq.engine.product.service;

import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.DatasetCategory;
import com.dropiq.engine.product.repository.DatasetCategoryRepository;
import com.dropiq.engine.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DynamicCategoryService {

    private final DatasetCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    private static final int MAX_CATEGORIES_PER_DATASET = 50;
    private static final int MAX_CHILDREN_PER_PARENT = 8;
    private static final int MAX_CATEGORY_DEPTH = 3;

    /**
     * Find or create category with group sharing logic
     */
    public DatasetCategory findOrCreateCategory(DataSet dataset, String nameUk, String nameRu, String nameEn,
                                                String subcategoryUk, String subcategoryRu, String subcategoryEn) {

        // First check if any product in same group+source already has category assigned
        DatasetCategory existingGroupCategory = findExistingGroupCategory(dataset, nameEn);
        if (existingGroupCategory != null) {
            log.info("Found existing category for group: {}", existingGroupCategory.getNameEn());
            return existingGroupCategory;
        }

        // Check dataset category limits
        long existingCategoriesCount = categoryRepository.countByDataset(dataset);
        if (existingCategoriesCount >= MAX_CATEGORIES_PER_DATASET) {
            log.warn("Dataset {} has reached maximum categories limit ({})",
                    dataset.getName(), MAX_CATEGORIES_PER_DATASET);
            return findBestMatchingCategory(dataset, nameEn);
        }

        // Create new category hierarchy
        DatasetCategory parentCategory = findOrCreateParentCategory(dataset, nameUk, nameRu, nameEn);

        if (subcategoryEn != null && !subcategoryEn.trim().isEmpty() &&
                !subcategoryEn.equalsIgnoreCase(nameEn)) {

            return findOrCreateChildCategory(parentCategory, subcategoryUk, subcategoryRu, subcategoryEn);
        }

        return parentCategory;
    }

    /**
     * Find existing category used by products with same characteristics
     */
    private DatasetCategory findExistingGroupCategory(DataSet dataset, String categoryName) {
        // This is a simplified approach - in real implementation you might want to
        // check for products with similar names/attributes that already have categories
        return categoryRepository
                .findByDatasetAndNameEnIgnoreCaseAndParentIsNull(dataset, categoryName)
                .orElse(null);
    }

    private DatasetCategory findOrCreateParentCategory(DataSet dataset, String nameUk, String nameRu, String nameEn) {
        Optional<DatasetCategory> existing = categoryRepository
                .findByDatasetAndNameEnIgnoreCaseAndParentIsNull(dataset, nameEn);

        if (existing.isPresent()) {
            log.debug("Found existing parent category: {}", nameEn);
            return existing.get();
        }

        // Check for similar categories
        List<DatasetCategory> similarCategories = categoryRepository
                .findSimilarCategories(dataset, nameEn, null, 0.8);

        if (!similarCategories.isEmpty()) {
            log.debug("Using similar category: {} for {}", similarCategories.get(0).getNameEn(), nameEn);
            return similarCategories.get(0);
        }

        // Create new parent category
        DatasetCategory newCategory = new DatasetCategory();
        newCategory.setDataset(dataset);
        newCategory.setNameUk(nameUk);
        newCategory.setNameRu(nameRu);
        newCategory.setNameEn(nameEn);
        newCategory.setLevel(0);
        newCategory.setAiGenerated(true);
        newCategory.setAiConfidence(0.8);

        newCategory = categoryRepository.save(newCategory);
        log.info("Created new parent category: {} for dataset: {}", nameEn, dataset.getName());

        return newCategory;
    }

    private DatasetCategory findOrCreateChildCategory(DatasetCategory parent, String nameUk, String nameRu, String nameEn) {
        if (parent.getChildren().size() >= MAX_CHILDREN_PER_PARENT) {
            log.warn("Parent category {} has reached maximum children limit", parent.getNameEn());
            return parent;
        }

        if (parent.getLevel() >= MAX_CATEGORY_DEPTH - 1) {
            log.warn("Cannot create child category - maximum depth reached for {}", parent.getNameEn());
            return parent;
        }

        Optional<DatasetCategory> existing = categoryRepository
                .findByParentAndNameEnIgnoreCase(parent, nameEn);

        if (existing.isPresent()) {
            return existing.get();
        }

        DatasetCategory childCategory = new DatasetCategory();
        childCategory.setDataset(parent.getDataset());
        childCategory.setParent(parent);
        childCategory.setNameUk(nameUk);
        childCategory.setNameRu(nameRu);
        childCategory.setNameEn(nameEn);
        childCategory.setLevel(parent.getLevel() + 1);
        childCategory.setAiGenerated(true);
        childCategory.setAiConfidence(0.7);

        childCategory = categoryRepository.save(childCategory);
        parent.getChildren().add(childCategory);

        log.info("Created new child category: {} under parent: {}", nameEn, parent.getNameEn());
        return childCategory;
    }

    private DatasetCategory findBestMatchingCategory(DataSet dataset, String categoryName) {
        List<DatasetCategory> allCategories = categoryRepository.findByDatasetOrderByProductCountDesc(dataset);
        return allCategories.isEmpty() ? createFallbackCategory(dataset) : allCategories.get(0);
    }

    private DatasetCategory createFallbackCategory(DataSet dataset) {
        DatasetCategory fallback = new DatasetCategory();
        fallback.setDataset(dataset);
        fallback.setNameUk("Інше");
        fallback.setNameRu("Прочее");
        fallback.setNameEn("Other");
        fallback.setLevel(0);
        fallback.setAiGenerated(true);
        fallback.setAiConfidence(0.5);

        return categoryRepository.save(fallback);
    }

    @Cacheable(value = "categoryTrees", key = "#dataset.id")
    public List<DatasetCategory> getCategoryTree(DataSet dataset) {
        return categoryRepository.findByDatasetAndParentIsNullOrderByProductCountDesc(dataset);
    }
}
