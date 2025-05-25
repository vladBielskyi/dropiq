package com.dropiq.engine.product.service;

import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.DatasetCategory;
import com.dropiq.engine.product.repository.DatasetCategoryRepository;
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

    private static final int MAX_CATEGORIES_PER_DATASET = 50;
    private static final int MAX_CHILDREN_PER_PARENT = 8;
    private static final int MAX_CATEGORY_DEPTH = 3;

    /**
     * Find or create category with parent-child relationship
     */
    public DatasetCategory findOrCreateCategory(DataSet dataset, String nameUk, String nameRu, String nameEn,
                                                String subcategoryUk, String subcategoryRu, String subcategoryEn) {

        // Check dataset category limits
        long existingCategoriesCount = categoryRepository.countByDataset(dataset);
        if (existingCategoriesCount >= MAX_CATEGORIES_PER_DATASET) {
            log.warn("Dataset {} has reached maximum categories limit ({})",
                    dataset.getName(), MAX_CATEGORIES_PER_DATASET);
            return findBestMatchingCategory(dataset, nameEn);
        }

        // First, find or create parent category
        DatasetCategory parentCategory = findOrCreateParentCategory(dataset, nameUk, nameRu, nameEn);

        // If subcategory is specified, create child category
        if (subcategoryEn != null && !subcategoryEn.trim().isEmpty() &&
                !subcategoryEn.equalsIgnoreCase(nameEn)) {

            return findOrCreateChildCategory(parentCategory, subcategoryUk, subcategoryRu, subcategoryEn);
        }

        return parentCategory;
    }

    private DatasetCategory findOrCreateParentCategory(DataSet dataset, String nameUk, String nameRu, String nameEn) {
        // Try to find existing parent category
        Optional<DatasetCategory> existing = categoryRepository
                .findByDatasetAndNameEnIgnoreCaseAndParentIsNull(dataset, nameEn);

        if (existing.isPresent()) {
            log.debug("Found existing parent category: {}", nameEn);
            return existing.get();
        }

        // Check for similar categories using fuzzy matching
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
        // Check child category limits
        if (parent.getChildren().size() >= MAX_CHILDREN_PER_PARENT) {
            log.warn("Parent category {} has reached maximum children limit ({})",
                    parent.getNameEn(), MAX_CHILDREN_PER_PARENT);
            return parent; // Return parent instead of creating child
        }

        // Check depth limits
        if (parent.getLevel() >= MAX_CATEGORY_DEPTH - 1) {
            log.warn("Cannot create child category - maximum depth reached for {}", parent.getNameEn());
            return parent;
        }

        // Try to find existing child category
        Optional<DatasetCategory> existing = categoryRepository
                .findByParentAndNameEnIgnoreCase(parent, nameEn);

        if (existing.isPresent()) {
            log.debug("Found existing child category: {}", nameEn);
            return existing.get();
        }

        // Create new child category
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

    /**
     * Find best matching category when limits are reached
     */
    private DatasetCategory findBestMatchingCategory(DataSet dataset, String categoryName) {
        List<DatasetCategory> allCategories = categoryRepository.findByDatasetOrderByProductCountDesc(dataset);

        // Return most popular category as fallback
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

    /**
     * Get category tree for dataset
     */
    @Cacheable(value = "categoryTrees", key = "#dataset.id")
    public List<DatasetCategory> getCategoryTree(DataSet dataset) {
        return categoryRepository.findByDatasetAndParentIsNullOrderByProductCountDesc(dataset);
    }

    /**
     * Update category from user input
     */
    @Transactional
    public DatasetCategory updateCategory(Long categoryId, String nameUk, String nameRu, String nameEn,
                                          String descriptionUk, String descriptionRu, String descriptionEn) {
        DatasetCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        category.setNameUk(nameUk);
        category.setNameRu(nameRu);
        category.setNameEn(nameEn);
        category.setDescriptionUk(descriptionUk);
        category.setDescriptionRu(descriptionRu);
        category.setDescriptionEn(descriptionEn);
        category.setAiGenerated(false); // Mark as user-edited

        return categoryRepository.save(category);
    }

    /**
     * Move category to different parent
     */
    @Transactional
    public DatasetCategory moveCategory(Long categoryId, Long newParentId) {
        DatasetCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        DatasetCategory newParent = null;
        if (newParentId != null) {
            newParent = categoryRepository.findById(newParentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found: " + newParentId));

            // Validate depth and parent-child limits
            if (newParent.getLevel() >= MAX_CATEGORY_DEPTH - 1) {
                throw new IllegalArgumentException("Cannot move - maximum depth would be exceeded");
            }

            if (newParent.getChildren().size() >= MAX_CHILDREN_PER_PARENT) {
                throw new IllegalArgumentException("Cannot move - parent has maximum children");
            }
        }

        // Remove from old parent
        if (category.getParent() != null) {
            category.getParent().getChildren().remove(category);
        }

        // Set new parent
        category.setParent(newParent);
        category.setLevel(newParent != null ? newParent.getLevel() + 1 : 0);

        if (newParent != null) {
            newParent.getChildren().add(category);
        }

        return categoryRepository.save(category);
    }

    /**
     * Delete category and reassign products
     */
    @Transactional
    public void deleteCategory(Long categoryId, Long reassignToCategoryId) {
        DatasetCategory categoryToDelete = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));

        DatasetCategory reassignCategory = null;
        if (reassignToCategoryId != null) {
            reassignCategory = categoryRepository.findById(reassignToCategoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Reassign category not found: " + reassignToCategoryId));
        }

        // Reassign products to new category or parent
        if (reassignCategory == null && categoryToDelete.getParent() != null) {
            reassignCategory = categoryToDelete.getParent();
        }

        if (reassignCategory != null) {
            categoryToDelete.getProducts().forEach(product -> {
                product.setCategory(reassignCategory);
                reassignCategory.addProduct(product);
            });
        }

        // Move children to parent or reassign category
        DatasetCategory newParentForChildren = reassignCategory != null ? reassignCategory : categoryToDelete.getParent();
        categoryToDelete.getChildren().forEach(child -> {
            child.setParent(newParentForChildren);
            if (newParentForChildren != null) {
                child.setLevel(newParentForChildren.getLevel() + 1);
                newParentForChildren.getChildren().add(child);
            } else {
                child.setLevel(0);
            }
        });

        // Remove from parent
        if (categoryToDelete.getParent() != null) {
            categoryToDelete.getParent().getChildren().remove(categoryToDelete);
        }

        categoryRepository.delete(categoryToDelete);
        log.info("Deleted category: {} and reassigned {} products",
                categoryToDelete.getNameEn(), categoryToDelete.getProducts().size());
    }

    /**
     * Optimize category structure for dataset
     */
    @Transactional
    public void optimizeCategoryStructure(DataSet dataset) {
        List<DatasetCategory> allCategories = categoryRepository.findByDataset(dataset);

        // Remove empty categories
        allCategories.stream()
                .filter(cat -> cat.getProducts().isEmpty() && cat.getChildren().isEmpty())
                .forEach(cat -> {
                    log.info("Removing empty category: {}", cat.getNameEn());
                    categoryRepository.delete(cat);
                });

        // Merge categories with single child
        allCategories.stream()
                .filter(cat -> cat.getChildren().size() == 1 && cat.getProducts().isEmpty())
                .forEach(this::mergeSingleChildCategory);
    }

    private void mergeSingleChildCategory(DatasetCategory parent) {
        DatasetCategory child = parent.getChildren().iterator().next();

        // Move child's products to parent
        child.getProducts().forEach(product -> {
            product.setCategory(parent);
            parent.addProduct(product);
        });

        // Move child's children to parent
        child.getChildren().forEach(grandchild -> {
            grandchild.setParent(parent);
            grandchild.setLevel(parent.getLevel() + 1);
            parent.getChildren().add(grandchild);
        });

        // Update parent name to be more descriptive
        parent.setNameEn(parent.getNameEn() + " - " + child.getNameEn());
        parent.setNameUk(parent.getNameUk() + " - " + child.getNameUk());
        parent.setNameRu(parent.getNameRu() + " - " + child.getNameRu());

        parent.getChildren().remove(child);
        categoryRepository.delete(child);

        log.info("Merged single child category: {} into parent: {}", child.getNameEn(), parent.getNameEn());
    }
}
