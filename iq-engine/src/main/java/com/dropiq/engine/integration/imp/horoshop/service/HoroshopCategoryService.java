package com.dropiq.engine.integration.imp.horoshop.service;

import com.dropiq.engine.integration.imp.horoshop.model.HoroshopCategory;
import com.dropiq.engine.product.entity.DatasetCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing category synchronization with Horoshop
 */
@Slf4j
@Service
public class HoroshopCategoryService {

    /**
     * Convert internal DatasetCategory to Horoshop format
     */
    public HoroshopCategory convertToHoroshopCategory(DatasetCategory category) {
        log.debug("Converting category {} to Horoshop format", category.getNameEn());

        HoroshopCategory horoshopCategory = new HoroshopCategory();

        horoshopCategory.setName(category.getNameEn());
        horoshopCategory.setSlug(category.getSlug());

        // Build category path
        horoshopCategory.setPath(buildCategoryPath(category));

        // Set parent ID if exists
        if (category.getParent() != null) {
            // In real implementation, you'd need to map to Horoshop parent ID
            horoshopCategory.setParentId(category.getParent().getId());
        }

        // Multilingual titles
        Map<String, String> titles = new HashMap<>();
        titles.put("ua", category.getNameUk());
        titles.put("ru", category.getNameRu());
        titles.put("en", category.getNameEn());
        horoshopCategory.setTitle(titles);

        // Multilingual descriptions
        Map<String, String> descriptions = new HashMap<>();
        if (category.getDescriptionUk() != null) {
            descriptions.put("ua", category.getDescriptionUk());
        }
        if (category.getDescriptionRu() != null) {
            descriptions.put("ru", category.getDescriptionRu());
        }
        if (category.getDescriptionEn() != null) {
            descriptions.put("en", category.getDescriptionEn());
        }
        horoshopCategory.setDescription(descriptions);

        horoshopCategory.setActive(category.getIsActive());
        horoshopCategory.setSortOrder(category.getLevel());

        return horoshopCategory;
    }

    /**
     * Convert Horoshop category to internal format
     */
    public DatasetCategory convertFromHoroshopCategory(HoroshopCategory horoshopCategory) {
        log.debug("Converting Horoshop category {} to internal format", horoshopCategory.getName());

        DatasetCategory category = new DatasetCategory();

        // Set names from multilingual titles or fallback to main name
        if (horoshopCategory.getTitle() != null) {
            category.setNameUk(horoshopCategory.getTitle().getOrDefault("ua", horoshopCategory.getName()));
            category.setNameRu(horoshopCategory.getTitle().getOrDefault("ru", horoshopCategory.getName()));
            category.setNameEn(horoshopCategory.getTitle().getOrDefault("en", horoshopCategory.getName()));
        } else {
            category.setNameEn(horoshopCategory.getName());
            category.setNameUk(horoshopCategory.getName());
            category.setNameRu(horoshopCategory.getName());
        }

        // Set descriptions
        if (horoshopCategory.getDescription() != null) {
            category.setDescriptionUk(horoshopCategory.getDescription().get("ua"));
            category.setDescriptionRu(horoshopCategory.getDescription().get("ru"));
            category.setDescriptionEn(horoshopCategory.getDescription().get("en"));
        }

        category.setIsActive(horoshopCategory.getActive());
        category.setLevel(horoshopCategory.getSortOrder());

        return category;
    }

    /**
     * Build hierarchical category tree for Horoshop
     */
    public List<HoroshopCategory> buildCategoryTree(List<DatasetCategory> categories) {
        log.info("Building category tree from {} categories", categories.size());

        Map<Long, HoroshopCategory> categoryMap = new HashMap<>();
        List<HoroshopCategory> rootCategories = new ArrayList<>();

        // First pass: convert all categories
        for (DatasetCategory category : categories) {
            HoroshopCategory horoshopCategory = convertToHoroshopCategory(category);
            horoshopCategory.setId(category.getId());
            categoryMap.put(category.getId(), horoshopCategory);
        }

        // Second pass: build hierarchy
        for (DatasetCategory category : categories) {
            HoroshopCategory horoshopCategory = categoryMap.get(category.getId());

            if (category.getParent() != null) {
                HoroshopCategory parent = categoryMap.get(category.getParent().getId());
                if (parent != null) {
                    horoshopCategory.setParentId(parent.getId());
                    parent.getChildren().add(horoshopCategory);
                } else {
                    // Parent not found, treat as root
                    rootCategories.add(horoshopCategory);
                }
            } else {
                rootCategories.add(horoshopCategory);
            }
        }

        return rootCategories;
    }

    /**
     * Flatten category tree for batch operations
     */
    public List<HoroshopCategory> flattenCategoryTree(List<HoroshopCategory> rootCategories) {
        List<HoroshopCategory> flatList = new ArrayList<>();

        for (HoroshopCategory category : rootCategories) {
            addCategoryAndChildren(category, flatList);
        }

        return flatList;
    }

    /**
     * Find category by path in Horoshop format
     */
    public Optional<HoroshopCategory> findCategoryByPath(List<HoroshopCategory> categories, String path) {
        return categories.stream()
                .filter(category -> path.equals(category.getPath()))
                .findFirst();
    }

    /**
     * Merge category hierarchies (local vs remote)
     */
    public List<HoroshopCategory> mergeCategoryHierarchies(List<HoroshopCategory> localCategories,
                                                           List<HoroshopCategory> remoteCategories) {
        log.info("Merging category hierarchies: {} local, {} remote",
                localCategories.size(), remoteCategories.size());

        Map<String, HoroshopCategory> remoteMap = remoteCategories.stream()
                .collect(Collectors.toMap(
                        cat -> cat.getName().toLowerCase(),
                        cat -> cat,
                        (existing, replacement) -> existing
                ));

        List<HoroshopCategory> mergedCategories = new ArrayList<>();

        for (HoroshopCategory localCategory : localCategories) {
            String key = localCategory.getName().toLowerCase();

            if (remoteMap.containsKey(key)) {
                // Category exists remotely, merge data
                HoroshopCategory remoteCategory = remoteMap.get(key);
                HoroshopCategory merged = mergeCategoryData(localCategory, remoteCategory);
                mergedCategories.add(merged);
                remoteMap.remove(key); // Remove from remote map
            } else {
                // New local category
                mergedCategories.add(localCategory);
            }
        }

        // Add remaining remote categories
        mergedCategories.addAll(remoteMap.values());

        return mergedCategories;
    }

    /**
     * Validate category structure for Horoshop
     */
    public List<String> validateCategoryStructure(List<HoroshopCategory> categories) {
        List<String> errors = new ArrayList<>();

        for (HoroshopCategory category : categories) {
            // Name validation
            if (category.getName() == null || category.getName().trim().isEmpty()) {
                errors.add("Category name is required");
            }

            // Path validation for nested categories
            if (category.getParentId() != null) {
                boolean parentExists = categories.stream()
                        .anyMatch(cat -> cat.getId().equals(category.getParentId()));
                if (!parentExists) {
                    errors.add("Parent category not found for: " + category.getName());
                }
            }

            // Circular reference check
            if (hasCircularReference(category, categories)) {
                errors.add("Circular reference detected in category: " + category.getName());
            }

            // Depth validation (Horoshop typically supports 3-4 levels)
            int depth = calculateCategoryDepth(category, categories);
            if (depth > 4) {
                errors.add("Category depth exceeds maximum (4 levels): " + category.getName());
            }
        }

        return errors;
    }

    /**
     * Generate category mapping for product assignment
     */
    public Map<String, String> generateCategoryMapping(List<DatasetCategory> internalCategories,
                                                       List<HoroshopCategory> horoshopCategories) {
        Map<String, String> mapping = new HashMap<>();

        Map<String, HoroshopCategory> horoshopMap = horoshopCategories.stream()
                .collect(Collectors.toMap(
                        cat -> cat.getName().toLowerCase(),
                        cat -> cat
                ));

        for (DatasetCategory internal : internalCategories) {
            String key = internal.getNameEn().toLowerCase();

            if (horoshopMap.containsKey(key)) {
                HoroshopCategory horoshop = horoshopMap.get(key);
                mapping.put(internal.getId().toString(), horoshop.getPath());
            } else {
                // Use path for new categories
                mapping.put(internal.getId().toString(), buildCategoryPath(internal));
            }
        }

        return mapping;
    }

    // Helper methods

    private String buildCategoryPath(DatasetCategory category) {
        if (category.getParent() != null) {
            return buildCategoryPath(category.getParent()) + " / " + category.getNameEn();
        }
        return category.getNameEn();
    }

    private void addCategoryAndChildren(HoroshopCategory category, List<HoroshopCategory> flatList) {
        flatList.add(category);
        for (HoroshopCategory child : category.getChildren()) {
            addCategoryAndChildren(child, flatList);
        }
    }

    private HoroshopCategory mergeCategoryData(HoroshopCategory local, HoroshopCategory remote) {
        // Create merged category with local data taking precedence
        HoroshopCategory merged = new HoroshopCategory();

        merged.setId(remote.getId()); // Use remote ID
        merged.setName(local.getName());
        merged.setSlug(local.getSlug());
        merged.setPath(local.getPath());
        merged.setParentId(local.getParentId());

        // Merge multilingual data
        Map<String, String> mergedTitles = new HashMap<>();
        if (remote.getTitle() != null) {
            mergedTitles.putAll(remote.getTitle());
        }
        if (local.getTitle() != null) {
            mergedTitles.putAll(local.getTitle()); // Local overrides remote
        }
        merged.setTitle(mergedTitles);

        Map<String, String> mergedDescriptions = new HashMap<>();
        if (remote.getDescription() != null) {
            mergedDescriptions.putAll(remote.getDescription());
        }
        if (local.getDescription() != null) {
            mergedDescriptions.putAll(local.getDescription()); // Local overrides remote
        }
        merged.setDescription(mergedDescriptions);

        merged.setActive(local.getActive());
        merged.setSortOrder(local.getSortOrder());

        return merged;
    }

    private boolean hasCircularReference(HoroshopCategory category, List<HoroshopCategory> allCategories) {
        Set<Long> visited = new HashSet<>();
        return hasCircularReferenceRecursive(category, allCategories, visited);
    }

    private boolean hasCircularReferenceRecursive(HoroshopCategory category,
                                                  List<HoroshopCategory> allCategories,
                                                  Set<Long> visited) {
        if (category.getId() != null && visited.contains(category.getId())) {
            return true; // Circular reference found
        }

        if (category.getId() != null) {
            visited.add(category.getId());
        }

        if (category.getParentId() != null) {
            Optional<HoroshopCategory> parent = allCategories.stream()
                    .filter(cat -> cat.getId().equals(category.getParentId()))
                    .findFirst();

            if (parent.isPresent()) {
                return hasCircularReferenceRecursive(parent.get(), allCategories, visited);
            }
        }

        return false;
    }

    private int calculateCategoryDepth(HoroshopCategory category, List<HoroshopCategory> allCategories) {
        int depth = 1;
        HoroshopCategory current = category;

        while (current.getParentId() != null) {
            HoroshopCategory finalCurrent = current;
            Optional<HoroshopCategory> parent = allCategories.stream()
                    .filter(cat -> cat.getId().equals(finalCurrent.getParentId()))
                    .findFirst();

            if (parent.isPresent()) {
                current = parent.get();
                depth++;

                if (depth > 10) { // Prevent infinite loops
                    break;
                }
            } else {
                break;
            }
        }

        return depth;
    }

    /**
     * Create sample category structure for testing
     */
    public List<HoroshopCategory> createSampleCategoryStructure() {
        List<HoroshopCategory> categories = new ArrayList<>();

        // Root category
        HoroshopCategory electronics = new HoroshopCategory();
        electronics.setId(1L);
        electronics.setName("Electronics");
        electronics.setPath("Electronics");
        electronics.setActive(true);
        electronics.setSortOrder(1);

        Map<String, String> electronicsTitles = new HashMap<>();
        electronicsTitles.put("ua", "Електроніка");
        electronicsTitles.put("ru", "Электроника");
        electronicsTitles.put("en", "Electronics");
        electronics.setTitle(electronicsTitles);

        // Child category
        HoroshopCategory smartphones = new HoroshopCategory();
        smartphones.setId(2L);
        smartphones.setName("Smartphones");
        smartphones.setPath("Electronics / Smartphones");
        smartphones.setParentId(1L);
        smartphones.setActive(true);
        smartphones.setSortOrder(1);

        Map<String, String> smartphoneTitles = new HashMap<>();
        smartphoneTitles.put("ua", "Смартфони");
        smartphoneTitles.put("ru", "Смартфоны");
        smartphoneTitles.put("en", "Smartphones");
        smartphones.setTitle(smartphoneTitles);

        electronics.getChildren().add(smartphones);
        categories.add(electronics);
        categories.add(smartphones);

        return categories;
    }
}
