package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.ProductFilterCriteria;
import com.dropiq.admin.model.ProductStatus;
import com.dropiq.admin.model.SourceType;
import com.dropiq.admin.service.DataSetService;
import com.dropiq.admin.view.main.MainView;
import com.dropiq.admin.view.product.ProductDetailView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "datasets/:id", layout = MainView.class)
@ViewController("DataSet.detail")
@ViewDescriptor(path = "data-set-detail-view.xml")
@EditedEntityContainer("dataSetDc")
public class DataSetDetailView extends StandardDetailView<DataSet> {

    @ViewComponent
    private DataGrid<Product> productsDataGrid;

    @ViewComponent
    private JmixButton archiveSelectedButton;

    @ViewComponent
    private JmixButton editProductButton;

    @ViewComponent
    private JmixButton syncDatasetButton;

    @ViewComponent
    private JmixButton toggleFiltersButton;

    @ViewComponent
    private JmixButton bulkPriceUpdateButton;

    @ViewComponent
    private JmixButton exportSelectedButton;

    @ViewComponent
    private Div filtersContainer;

    @ViewComponent
    private Span productCountLabel;

    // Basic filters
    @ViewComponent
    private JmixSelect<SourceType> sourceTypeFilter;

    @ViewComponent
    private TypedTextField<String> nameFilter;

    @ViewComponent
    private TypedTextField<String> categoryFilter;

    @ViewComponent
    private TypedTextField<String> descriptionFilter;

    @ViewComponent
    private TypedTextField<String> minPriceFilter;

    @ViewComponent
    private TypedTextField<String> maxPriceFilter;

    @ViewComponent
    private TypedTextField<String> minStockFilter;

    @ViewComponent
    private TypedTextField<String> maxStockFilter;


    @ViewComponent
    private JmixCheckbox availableOnlyFilter;

    @ViewComponent
    private JmixSelect<ProductStatus> statusFilter;

    @ViewComponent
    private JmixCheckbox aiOptimizedOnlyFilter;

    @ViewComponent
    private JmixCheckbox hasImagesFilter;

    @ViewComponent
    private JmixCheckbox hasGroupIdFilter;

    @ViewComponent
    private JmixButton applyFiltersButton;

    @ViewComponent
    private JmixButton clearFiltersButton;

    @ViewComponent
    private CollectionContainer<Product> productsDc;

    @ViewComponent
    private CollectionLoader<Product> productsDl;

    @Autowired
    private DataSetService dataSetService;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private Notifications notifications;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private DialogWindows dialogWindows;

    private List<Product> allProducts = new ArrayList<>();
    private boolean filtersVisible = false;

    @Subscribe
    public void onReady(ReadyEvent event) {
        setupDataGrid();
        setupFilters();
        updateButtonStates();
        loadProducts();
    }

    private void setupDataGrid() {
        // Set up image column renderer
        productsDataGrid.getColumnByKey("image").setRenderer(new ComponentRenderer<>(this::createImageComponent));

        // Set up grouping by source and category
        productsDataGrid.setDetailsVisibleOnClick(false);

        // Selection listener
        productsDataGrid.addSelectionListener(e -> updateButtonStates());

        // Double-click listener
        productsDataGrid.addItemDoubleClickListener(e -> {
            Product product = e.getItem();
            if (product != null) {
                openProductEditor(product);
            }
        });
    }

    private void setupFilters() {
        // Initialize filter options
        sourceTypeFilter.setItems(SourceType.values());
        sourceTypeFilter.setItemLabelGenerator(SourceType::name);

        statusFilter.setItems(ProductStatus.values());
        statusFilter.setItemLabelGenerator(ProductStatus::name);

        // Initially hide filters
        filtersContainer.setVisible(false);

        // Setup toggle button
        toggleFiltersButton.setIcon(VaadinIcon.FILTER.create());
        toggleFiltersButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    }

    private Component createImageComponent(Product product) {
        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            Image image = new Image(product.getImageUrls().get(0), "Product");
            image.setWidth("50px");
            image.setHeight("50px");
            image.addClassName("product-image");
            image.getStyle().set("object-fit", "cover");
            image.getStyle().set("border-radius", "4px");
            return image;
        }
        return new Span("No image");
    }

    @Subscribe("toggleFiltersButton")
    public void onToggleFiltersButtonClick(ClickEvent<JmixButton> event) {
        filtersVisible = !filtersVisible;
        filtersContainer.setVisible(filtersVisible);

        if (filtersVisible) {
            toggleFiltersButton.setText("Hide Filters");
            toggleFiltersButton.setIcon(VaadinIcon.ANGLE_UP.create());
        } else {
            toggleFiltersButton.setText("Show Filters");
            toggleFiltersButton.setIcon(VaadinIcon.ANGLE_DOWN.create());
        }
    }

    @Subscribe("applyFiltersButton")
    public void onApplyFiltersButtonClick(ClickEvent<JmixButton> event) {
        applyFilters();
    }

    @Subscribe("clearFiltersButton")
    public void onClearFiltersButtonClick(ClickEvent<JmixButton> event) {
        clearAllFilters();
    }

    @Subscribe("syncDatasetButton")
    public void onSyncDatasetButtonClick(ClickEvent<JmixButton> event) {
        DataSet dataSet = getEditedEntity();
        if (dataSet != null) {
            try {
                String userId = currentAuthentication.getUser().getUsername();
                dataSetService.synchronizeDataSet(dataSet, userId);

                notifications.create("Dataset synchronization started")
                        .withType(Notifications.Type.SUCCESS)
                        .show();

                reloadEntity();

            } catch (Exception e) {
                notifications.create("Failed to start synchronization: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    @Subscribe("editProductButton")
    public void onEditProductButtonClick(ClickEvent<JmixButton> event) {
        Product selected = productsDataGrid.getSingleSelectedItem();
        if (selected != null) {
            openProductEditor(selected);
        }
    }

    @Subscribe("archiveSelectedButton")
    public void onArchiveSelectedButtonClick(ClickEvent<JmixButton> event) {
        Set<Product> selectedItems = productsDataGrid.getSelectedItems();
        List<Product> productsToArchive = new ArrayList<>(selectedItems);

        if (!productsToArchive.isEmpty()) {
            try {
                dataSetService.archiveProducts(productsToArchive);

                notifications.create("Archived " + productsToArchive.size() + " products")
                        .withType(Notifications.Type.SUCCESS)
                        .show();

                loadProducts();

            } catch (Exception e) {
                notifications.create("Failed to archive products: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    private void openProductEditor(Product product) {
        dialogWindows.detail(this, Product.class)
                .editEntity(product)
                .withViewClass(ProductDetailView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        loadProducts();
                    }
                })
                .open();
    }

    private void loadProducts() {
        DataSet dataSet = getEditedEntity();
        if (dataSet != null && dataSet.getId() != null) {
            productsDl.setParameter("datasetId", dataSet.getId());
            productsDl.load();

            allProducts = new ArrayList<>(productsDc.getItems());
            updateProductCount(allProducts.size());

            System.out.println("Loaded " + allProducts.size() + " products for dataset");
        }
    }

    private void applyFilters() {
        try {
            ProductFilterCriteria criteria = buildFilterCriteria();

            // Validate criteria
            if (criteria.isEmpty()) {
                // Show all products if no filters applied
                productsDc.setItems(allProducts);
                updateProductCount(allProducts.size());
                notifications.create("No filters applied - showing all " + allProducts.size() + " products")
                        .withType(Notifications.Type.DEFAULT)
                        .show();
                return;
            }

            // Debug logging
            System.out.println("Applying filters to " + allProducts.size() + " products");
            System.out.println("Filter criteria: " + criteriaToString(criteria));

            List<Product> filteredProducts = filterProducts(allProducts, criteria);

            productsDc.setItems(filteredProducts);
            updateProductCount(filteredProducts.size());

            String message = "Applied filters: showing " + filteredProducts.size() + " of " + allProducts.size() + " products";
            notifications.create(message)
                    .withType(filteredProducts.isEmpty() ? Notifications.Type.WARNING : Notifications.Type.SUCCESS)
                    .show();

            System.out.println("Filtered down to " + filteredProducts.size() + " products");

        } catch (Exception e) {
            System.err.println("Error applying filters: " + e.getMessage());
            e.printStackTrace();

            notifications.create("Error applying filters: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    private String criteriaToString(ProductFilterCriteria criteria) {
        StringBuilder sb = new StringBuilder();
        sb.append("FilterCriteria{");

        if (criteria.getNameFilter() != null) sb.append("name='").append(criteria.getNameFilter()).append("', ");
        if (criteria.getCategoryFilter() != null) sb.append("category='").append(criteria.getCategoryFilter()).append("', ");
        if (criteria.getSourceTypes() != null) sb.append("sources=").append(criteria.getSourceTypes()).append(", ");
        if (criteria.getMinPrice() != null) sb.append("minPrice=").append(criteria.getMinPrice()).append(", ");
        if (criteria.getMaxPrice() != null) sb.append("maxPrice=").append(criteria.getMaxPrice()).append(", ");
        if (criteria.getMinStock() != null) sb.append("minStock=").append(criteria.getMinStock()).append(", ");
        if (criteria.getMaxStock() != null) sb.append("maxStock=").append(criteria.getMaxStock()).append(", ");
        if (criteria.getAvailableOnly() != null) sb.append("availableOnly=").append(criteria.getAvailableOnly()).append(", ");
        if (criteria.getStatuses() != null) sb.append("statuses=").append(criteria.getStatuses()).append(", ");
        if (criteria.getAiOptimizedOnly() != null) sb.append("aiOnly=").append(criteria.getAiOptimizedOnly()).append(", ");
        if (criteria.getHasImages() != null) sb.append("hasImages=").append(criteria.getHasImages()).append(", ");
        if (criteria.getHasGroupId() != null) sb.append("hasGroupId=").append(criteria.getHasGroupId()).append(", ");

        sb.append("}");
        return sb.toString();
    }

    private ProductFilterCriteria buildFilterCriteria() {
        ProductFilterCriteria criteria = new ProductFilterCriteria();

        // Text filters - only set if not empty
        String nameValue = nameFilter.getValue();
        if (nameValue != null && !nameValue.trim().isEmpty()) {
            criteria.setNameFilter(nameValue.trim());
        }

        String categoryValue = categoryFilter.getValue();
        if (categoryValue != null && !categoryValue.trim().isEmpty()) {
            criteria.setCategoryFilter(categoryValue.trim());
        }

        String descriptionValue = descriptionFilter.getValue();
        if (descriptionValue != null && !descriptionValue.trim().isEmpty()) {
            criteria.setDescriptionFilter(descriptionValue.trim());
        }

        // Source filter
        if (sourceTypeFilter.getValue() != null) {
            criteria.setSourceTypes(Set.of(sourceTypeFilter.getValue()));
        }

        // Price filters - parse strings to BigDecimal
        String minPriceStr = minPriceFilter.getValue();
        if (minPriceStr != null && !minPriceStr.trim().isEmpty()) {
            try {
                BigDecimal minPrice = new BigDecimal(minPriceStr.trim());
                if (minPrice.compareTo(BigDecimal.ZERO) >= 0) {
                    criteria.setMinPrice(minPrice);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid min price format: " + minPriceStr);
            }
        }

        String maxPriceStr = maxPriceFilter.getValue();
        if (maxPriceStr != null && !maxPriceStr.trim().isEmpty()) {
            try {
                BigDecimal maxPrice = new BigDecimal(maxPriceStr.trim());
                if (maxPrice.compareTo(BigDecimal.ZERO) > 0) {
                    criteria.setMaxPrice(maxPrice);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid max price format: " + maxPriceStr);
            }
        }

        // Stock filters - parse strings to Integer
        String minStockStr = minStockFilter.getValue();
        if (minStockStr != null && !minStockStr.trim().isEmpty()) {
            try {
                Integer minStock = Integer.parseInt(minStockStr.trim());
                if (minStock >= 0) {
                    criteria.setMinStock(minStock);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid min stock format: " + minStockStr);
            }
        }

        String maxStockStr = maxStockFilter.getValue();
        if (maxStockStr != null && !maxStockStr.trim().isEmpty()) {
            try {
                Integer maxStock = Integer.parseInt(maxStockStr.trim());
                if (maxStock >= 0) {
                    criteria.setMaxStock(maxStock);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid max stock format: " + maxStockStr);
            }
        }

        // Boolean filters - handle checkbox values
        Boolean availableOnly = availableOnlyFilter.getValue();
        if (Boolean.TRUE.equals(availableOnly)) {
            criteria.setAvailableOnly(true);
        }

        // Status filter
        if (statusFilter.getValue() != null) {
            criteria.setStatuses(Set.of(statusFilter.getValue()));
        }

        // AI/SEO filters - handle checkbox values
        Boolean aiOptimizedOnly = aiOptimizedOnlyFilter.getValue();
        if (Boolean.TRUE.equals(aiOptimizedOnly)) {
            criteria.setAiOptimizedOnly(true);
        }

        Boolean hasImages = hasImagesFilter.getValue();
        if (Boolean.TRUE.equals(hasImages)) {
            criteria.setHasImages(true);
        }

        Boolean hasGroupId = hasGroupIdFilter.getValue();
        if (Boolean.TRUE.equals(hasGroupId)) {
            criteria.setHasGroupId(true);
        }

        return criteria;
    }

    private boolean matchesCriteria(Product product, ProductFilterCriteria criteria) {
        // Safety check
        if (product == null) {
            return false;
        }

        // Name filter
        if (criteria.getNameFilter() != null) {
            String productName = product.getName();
            if (productName == null || !productName.toLowerCase().contains(criteria.getNameFilter().toLowerCase())) {
                return false;
            }
        }

        // Category filter
        if (criteria.getCategoryFilter() != null) {
            String categoryName = product.getExternalCategoryName();
            if (categoryName == null || !categoryName.toLowerCase().contains(criteria.getCategoryFilter().toLowerCase())) {
                return false;
            }
        }

        // Description filter
        if (criteria.getDescriptionFilter() != null) {
            String description = product.getDescription();
            if (description == null || !description.toLowerCase().contains(criteria.getDescriptionFilter().toLowerCase())) {
                return false;
            }
        }

        // Source type filter
        if (criteria.getSourceTypes() != null && !criteria.getSourceTypes().isEmpty()) {
            SourceType productSourceType = product.getSourceType();
            if (productSourceType == null || !criteria.getSourceTypes().contains(productSourceType)) {
                return false;
            }
        }

        // Price filters
        if (criteria.getMinPrice() != null) {
            BigDecimal productPrice = product.getOriginalPrice();
            if (productPrice == null || productPrice.compareTo(criteria.getMinPrice()) < 0) {
                return false;
            }
        }

        if (criteria.getMaxPrice() != null) {
            BigDecimal productPrice = product.getOriginalPrice();
            if (productPrice == null || productPrice.compareTo(criteria.getMaxPrice()) > 0) {
                return false;
            }
        }

        // Stock filters
        if (criteria.getMinStock() != null) {
            Integer stock = product.getStock();
            if (stock == null || stock < criteria.getMinStock()) {
                return false;
            }
        }

        if (criteria.getMaxStock() != null) {
            Integer stock = product.getStock();
            if (stock == null || stock > criteria.getMaxStock()) {
                return false;
            }
        }

        // Available filter
        if (criteria.getAvailableOnly() != null && criteria.getAvailableOnly()) {
            Boolean available = product.getAvailable();
            if (!Boolean.TRUE.equals(available)) {
                return false;
            }
        }

        // Status filter
        if (criteria.getStatuses() != null && !criteria.getStatuses().isEmpty()) {
            ProductStatus productStatus = product.getStatus();
            if (productStatus == null || !criteria.getStatuses().contains(productStatus)) {
                return false;
            }
        }

        // AI optimized filter
        if (criteria.getAiOptimizedOnly() != null && criteria.getAiOptimizedOnly()) {
            Boolean aiOptimized = product.getAiOptimized();
            if (!Boolean.TRUE.equals(aiOptimized)) {
                return false;
            }
        }

        // Has images filter
        if (criteria.getHasImages() != null && criteria.getHasImages()) {
            List<String> imageUrls = product.getImageUrls();
            if (imageUrls == null || imageUrls.isEmpty()) {
                return false;
            }
        }

        // Has group ID filter
        if (criteria.getHasGroupId() != null && criteria.getHasGroupId()) {
            String groupId = product.getGroupId();
            if (groupId == null || groupId.trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private List<Product> filterProducts(List<Product> products, ProductFilterCriteria criteria) {
        return products.stream()
                .filter(product -> matchesCriteria(product, criteria))
                .collect(Collectors.toList());
    }

    private void clearAllFilters() {
        try {
            // Clear all filter fields
            nameFilter.clear();
            categoryFilter.clear();
            descriptionFilter.clear();
            sourceTypeFilter.clear();
            minPriceFilter.clear();
            maxPriceFilter.clear();
            minStockFilter.clear();
            maxStockFilter.clear();
            availableOnlyFilter.setValue(false);
            statusFilter.clear();
            aiOptimizedOnlyFilter.setValue(false);
            hasImagesFilter.setValue(false);
            hasGroupIdFilter.setValue(false);

            // Reset to show all products
            productsDc.setItems(allProducts);
            updateProductCount(allProducts.size());

            notifications.create("Filters cleared - showing all " + allProducts.size() + " products")
                    .withType(Notifications.Type.DEFAULT)
                    .show();

            System.out.println("Filters cleared, showing " + allProducts.size() + " products");

        } catch (Exception e) {
            System.err.println("Error clearing filters: " + e.getMessage());
            e.printStackTrace();

            notifications.create("Error clearing filters: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    private void updateButtonStates() {
        Set<Product> selectedItems = productsDataGrid.getSelectedItems();

        boolean hasSelection = !selectedItems.isEmpty();
        boolean singleSelection = selectedItems.size() == 1;

        archiveSelectedButton.setEnabled(hasSelection);
        editProductButton.setEnabled(singleSelection);
        bulkPriceUpdateButton.setEnabled(hasSelection);
        exportSelectedButton.setEnabled(hasSelection);
    }

    private void updateProductCount(int count) {
        productCountLabel.setText(count + " products");
    }

    private void reloadEntity() {
        DataSet current = getEditedEntity();
        if (current != null && current.getId() != null) {
            DataSet reloaded = dataManager.load(DataSet.class)
                    .id(current.getId())
                    .fetchPlan("_base")
                    .one();
            getEditedEntityContainer().setItem(reloaded);
        }
    }
}