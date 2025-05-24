package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.ProductDisplayItem;
import com.dropiq.admin.model.ProductFilterCriteria;
import com.dropiq.admin.model.ProductStatus;
import com.dropiq.admin.model.SourceType;
import com.dropiq.admin.service.DataSetService;
import com.dropiq.admin.view.main.MainView;
import com.dropiq.admin.view.product.ProductDetailView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
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
    private DataGrid<ProductDisplayItem> productsDataGrid;

    @ViewComponent
    private CollectionContainer<ProductDisplayItem> productDisplayDc;

    @ViewComponent
    private CollectionContainer<Product> productsDc;

    @ViewComponent
    private CollectionLoader<Product> productsDl;

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

    // Filter components
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
    private List<ProductDisplayItem> displayItems = new ArrayList<>();
    private boolean filtersVisible = false;

    @Subscribe
    public void onReady(ReadyEvent event) {
        setupDataGrid();
        setupFilters();
        updateButtonStates();
        loadProducts();
    }

    private void setupDataGrid() {
        // Set up expand/collapse toggle column
        productsDataGrid.getColumnByKey("expandToggle").setRenderer(new ComponentRenderer<>(this::createExpandToggleComponent));

        // Set up other custom columns
        productsDataGrid.getColumnByKey("source").setRenderer(new ComponentRenderer<>(this::createSourceComponent));
        productsDataGrid.getColumnByKey("category").setRenderer(new ComponentRenderer<>(this::createCategoryComponent));
        productsDataGrid.getColumnByKey("image").setRenderer(new ComponentRenderer<>(this::createImageComponent));
        productsDataGrid.getColumnByKey("price").setRenderer(new ComponentRenderer<>(this::createPriceComponent));
        productsDataGrid.getColumnByKey("stock").setRenderer(new ComponentRenderer<>(this::createStockComponent));
        productsDataGrid.getColumnByKey("status").setRenderer(new ComponentRenderer<>(this::createStatusComponent));
        productsDataGrid.getColumnByKey("available").setRenderer(new ComponentRenderer<>(this::createAvailableComponent));

        // Selection listener
        productsDataGrid.addSelectionListener(e -> updateButtonStates());

        // Double-click listener
        productsDataGrid.addItemDoubleClickListener(e -> {
            ProductDisplayItem item = e.getItem();
            if (item != null && !item.isGroup()) {
                Product product = item.getActualProduct();
                if (product != null) {
                    openProductEditor(product);
                }
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

    private Component createExpandToggleComponent(ProductDisplayItem item) {
        if (item.isGroup()) {
            Button toggleButton = new Button();
            toggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            if (item.isExpanded()) {
                toggleButton.setIcon(VaadinIcon.MINUS.create());
                toggleButton.getStyle().set("color", "var(--lumo-primary-color)");
            } else {
                toggleButton.setIcon(VaadinIcon.PLUS.create());
                toggleButton.getStyle().set("color", "var(--lumo-secondary-text-color)");
            }

            toggleButton.addClickListener(e -> toggleGroupExpansion(item));
            return toggleButton;
        }
        return new Span("");
    }

    private Component createSourceComponent(ProductDisplayItem item) {
        Product product = item.getActualProduct();
        if (product != null && product.getSourceType() != null) {
            Span sourceSpan = new Span(product.getSourceType().name());
            sourceSpan.addClassName("source-badge");
            return sourceSpan;
        }
        return new Span("");
    }

    private Component createCategoryComponent(ProductDisplayItem item) {
        Product product = item.getActualProduct();
        if (product != null && product.getExternalCategoryName() != null) {
            return new Span(product.getExternalCategoryName());
        }
        return new Span("");
    }

    private Component createImageComponent(ProductDisplayItem item) {
        if (item.isGroup()) {
            // Show first variant's image for group
            Product firstProduct = item.getVariants().get(0);
            if (firstProduct.getImageUrls() != null && !firstProduct.getImageUrls().isEmpty()) {
                Image image = new Image(firstProduct.getImageUrls().get(0), "Product Group");
                image.setWidth("50px");
                image.setHeight("50px");
                image.addClassName("product-image");
                image.getStyle().set("object-fit", "cover");
                image.getStyle().set("border-radius", "4px");
                image.getStyle().set("opacity", "0.8"); // Slightly faded for group
                return image;
            }
        } else {
            Product product = item.getActualProduct();
            if (product != null && product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                Image image = new Image(product.getImageUrls().get(0), "Product");
                image.setWidth("50px");
                image.setHeight("50px");
                image.addClassName("product-image");
                image.getStyle().set("object-fit", "cover");
                image.getStyle().set("border-radius", "4px");
                return image;
            }
        }
        return new Span("No image");
    }

    private Component createPriceComponent(ProductDisplayItem item) {
        if (item.isGroup()) {
            BigDecimal avgPrice = item.getTotalValue().divide(BigDecimal.valueOf(item.getVariantCount()), 2, BigDecimal.ROUND_HALF_UP);
            Span priceSpan = new Span(String.format("%.2f UAH avg", avgPrice));
            priceSpan.getStyle().set("font-weight", "bold");
            return priceSpan;
        } else {
            Product product = item.getActualProduct();
            if (product != null && product.getOriginalPrice() != null) {
                return new Span(String.format("%.2f UAH", product.getOriginalPrice()));
            }
        }
        return new Span("");
    }

    private Component createStockComponent(ProductDisplayItem item) {
        if (item.isGroup()) {
            int totalStock = item.getVariants().stream()
                    .mapToInt(p -> p.getStock() != null ? p.getStock() : 0)
                    .sum();
            Span stockSpan = new Span(String.valueOf(totalStock));
            stockSpan.getStyle().set("font-weight", "bold");
            return stockSpan;
        } else {
            Product product = item.getActualProduct();
            if (product != null) {
                int stock = product.getStock() != null ? product.getStock() : 0;
                Span stockSpan = new Span(String.valueOf(stock));

                if (stock == 0) {
                    stockSpan.getStyle().set("color", "var(--lumo-error-color)");
                } else if (stock < 10) {
                    stockSpan.getStyle().set("color", "var(--lumo-warning-color)");
                }

                return stockSpan;
            }
        }
        return new Span("");
    }

    private Component createStatusComponent(ProductDisplayItem item) {
        if (item.isGroup()) {
            long activeCount = item.getVariants().stream()
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                    .count();
            Span statusSpan = new Span(activeCount + "/" + item.getVariantCount() + " active");
            statusSpan.addClassName("status-badge");
            return statusSpan;
        } else {
            Product product = item.getActualProduct();
            if (product != null && product.getStatus() != null) {
                Span badge = new Span(product.getStatus().name());
                badge.addClassName("status-badge");

                switch (product.getStatus()) {
                    case ACTIVE:
                        badge.getStyle().set("color", "var(--lumo-success-color)");
                        break;
                    case INACTIVE:
                        badge.getStyle().set("color", "var(--lumo-error-color)");
                        break;
                    case DRAFT:
                        badge.getStyle().set("color", "var(--lumo-warning-color)");
                        break;
                    default:
                        badge.getStyle().set("color", "var(--lumo-secondary-text-color)");
                }

                return badge;
            }
        }
        return new Span("");
    }

    private Component createAvailableComponent(ProductDisplayItem item) {
        if (item.isGroup()) {
            long availableCount = item.getVariants().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getAvailable()))
                    .count();
            return new Span(availableCount + "/" + item.getVariantCount());
        } else {
            Product product = item.getActualProduct();
            if (product != null) {
                boolean available = Boolean.TRUE.equals(product.getAvailable());
                Span availableSpan = new Span(available ? "Yes" : "No");
                availableSpan.getStyle().set("color", available ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
                return availableSpan;
            }
        }
        return new Span("");
    }

    private void toggleGroupExpansion(ProductDisplayItem groupItem) {
        groupItem.setExpanded(!groupItem.isExpanded());
        buildDisplayItems(allProducts);

        // Refresh the grid
        productDisplayDc.setItems(displayItems);

        System.out.println("Toggled group " + groupItem.getGroupId() + " to " + (groupItem.isExpanded() ? "expanded" : "collapsed"));
    }

    private void loadProducts() {
        DataSet dataSet = getEditedEntity();
        if (dataSet != null && dataSet.getId() != null) {
            productsDl.setParameter("datasetId", dataSet.getId());
            productsDl.load();

            allProducts = new ArrayList<>(productsDc.getItems());

            // ADD THIS GROUPING LOGIC
            if (productDisplayDc != null) {
                buildDisplayItems(allProducts);
                System.out.println("Using grouped display with " + allProducts.size() + " products");
            } else {
                // Fallback to old approach
                updateProductCount(allProducts.size());
                System.out.println("Using simple display with " + allProducts.size() + " products");
            }
        }
    }

    // ADD THIS METHOD to your existing class
    private void buildDisplayItems(List<Product> products) {
        if (productDisplayDc == null) {
            System.err.println("productDisplayDc is null - grouping not available");
            return;
        }

        List<ProductDisplayItem> displayItems = new ArrayList<>();

        // Group products by groupId
        Map<String, List<Product>> groupedProducts = products.stream()
                .collect(Collectors.groupingBy(p ->
                        p.getGroupId() != null && !p.getGroupId().trim().isEmpty()
                                ? p.getGroupId()
                                : "single_" + p.getId()));

        System.out.println("Found " + groupedProducts.size() + " groups from " + products.size() + " products");

        for (Map.Entry<String, List<Product>> entry : groupedProducts.entrySet()) {
            String groupId = entry.getKey();
            List<Product> groupProducts = entry.getValue();

            if (groupProducts.size() == 1 || groupId.startsWith("single_")) {
                // Single product - no grouping needed
                displayItems.add(ProductDisplayItem.createSingleProduct(groupProducts.get(0)));
                System.out.println("Added single product: " + groupProducts.get(0).getName());
            } else {
                // Multiple products with same groupId - create group
                ProductDisplayItem groupItem = ProductDisplayItem.createProductGroup(groupId, groupProducts);
                displayItems.add(groupItem);
                System.out.println("Added product group: " + groupId + " with " + groupProducts.size() + " variants");

                // Add expanded variants (for testing, let's expand by default)
                groupItem.setExpanded(true);
                for (Product variant : groupProducts) {
                    displayItems.add(ProductDisplayItem.createVariantProduct(variant));
                }
            }
        }

        // Update the grid
        productDisplayDc.setItems(displayItems);
        updateProductCount(products.size());

        System.out.println("Built " + displayItems.size() + " display items");
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
        ProductDisplayItem selected = productsDataGrid.getSingleSelectedItem();
        if (selected != null && !selected.isGroup()) {
            Product product = selected.getActualProduct();
            if (product != null) {
                openProductEditor(product);
            }
        }
    }

    @Subscribe("archiveSelectedButton")
    public void onArchiveSelectedButtonClick(ClickEvent<JmixButton> event) {
        Set<ProductDisplayItem> selectedItems = productsDataGrid.getSelectedItems();
        List<Product> productsToArchive = selectedItems.stream()
                .filter(item -> !item.isGroup())
                .map(ProductDisplayItem::getActualProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

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

    private void applyFilters() {
        try {
            ProductFilterCriteria criteria = buildFilterCriteria();

            if (criteria.isEmpty()) {
                buildDisplayItems(allProducts);
                notifications.create("No filters applied - showing all " + allProducts.size() + " products")
                        .withType(Notifications.Type.DEFAULT)
                        .show();
                return;
            }

            System.out.println("Applying filters to " + allProducts.size() + " products");
            System.out.println("Filter criteria: " + criteriaToString(criteria));

            List<Product> filteredProducts = filterProducts(allProducts, criteria);
            buildDisplayItems(filteredProducts);

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

    // All the existing filter methods remain exactly the same
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
            buildDisplayItems(allProducts);

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
        Set<ProductDisplayItem> selectedItems = productsDataGrid.getSelectedItems();

        // Only enable buttons for non-group items
        boolean hasProductSelected = selectedItems.stream()
                .anyMatch(item -> !item.isGroup());

        ProductDisplayItem singleSelected = productsDataGrid.getSingleSelectedItem();
        boolean singleProductSelected = singleSelected != null && !singleSelected.isGroup();

        archiveSelectedButton.setEnabled(hasProductSelected);
        editProductButton.setEnabled(singleProductSelected);
        bulkPriceUpdateButton.setEnabled(hasProductSelected);
        exportSelectedButton.setEnabled(hasProductSelected);
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