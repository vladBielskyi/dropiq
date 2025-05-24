package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.ProductFilterCriteria;
import com.dropiq.admin.model.ProductStatus;
import com.dropiq.admin.model.ProductTreeItem;
import com.dropiq.admin.model.SourceType;
import com.dropiq.admin.service.DataSetService;
import com.dropiq.admin.view.main.MainView;
import com.dropiq.admin.view.product.ProductDetailView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
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
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
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
    private TreeDataGrid<ProductTreeItem> productsTreeGrid;

    @ViewComponent
    private JmixButton archiveSelectedButton;

    @ViewComponent
    private JmixButton editProductButton;

    @ViewComponent
    private JmixButton syncDatasetButton;

    @ViewComponent
    private JmixButton toggleFiltersButton;

    @ViewComponent
    private Div filtersContainer;

    // Basic filters
    @ViewComponent
    private TypedTextField<String> nameFilter;

    @ViewComponent
    private TypedTextField<String> categoryFilter;

    @ViewComponent
    private TypedTextField<String> descriptionFilter;

    // Advanced filters - використовуємо JmixSelect замість JmixMultiSelect
    @ViewComponent
    private JmixSelect<SourceType> sourceTypeFilter;

    @ViewComponent
    private TypedTextField<BigDecimal> minPriceFilter;

    @ViewComponent
    private TypedTextField<BigDecimal> maxPriceFilter;

    @ViewComponent
    private TypedTextField<Integer> minStockFilter;

    @ViewComponent
    private TypedTextField<Integer> maxStockFilter;

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
    private JmixButton expandAllButton;

    @ViewComponent
    private JmixButton collapseAllButton;

    @ViewComponent
    private CollectionContainer<ProductTreeItem> treeItemsDc;

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
    private List<ProductTreeItem> treeItems = new ArrayList<>();
    private boolean filtersVisible = false;

    @Subscribe
    public void onReady(ReadyEvent event) {
        setupTreeGrid();
        setupFilters();
        updateButtonStates();
        loadProducts();
    }

    private void setupTreeGrid() {
        // Правильний спосіб для Jmix 2.5 - додаємо колонки без ComponentRenderer для hierarchy
        productsTreeGrid.addHierarchyColumn(ProductTreeItem::getDisplayName)
                .setHeader("Name / Group")
                .setWidth("300px")
                .setFlexGrow(1);

        // Додаємо інші колонки з ComponentRenderer
        productsTreeGrid.addColumn(new ComponentRenderer<>(this::createImageComponent))
                .setHeader("Image")
                .setWidth("80px")
                .setFlexGrow(0);

        productsTreeGrid.addColumn(new ComponentRenderer<>(this::createPriceComponent))
                .setHeader("Price")
                .setWidth("100px")
                .setFlexGrow(0);

        productsTreeGrid.addColumn(new ComponentRenderer<>(this::createStockComponent))
                .setHeader("Stock")
                .setWidth("80px")
                .setFlexGrow(0);

        productsTreeGrid.addColumn(new ComponentRenderer<>(this::createStatusComponent))
                .setHeader("Status")
                .setWidth("100px")
                .setFlexGrow(0);

        productsTreeGrid.addColumn(new ComponentRenderer<>(this::createSourceComponent))
                .setHeader("Source")
                .setWidth("100px")
                .setFlexGrow(0);

        // Selection listener
        productsTreeGrid.addSelectionListener(e -> updateButtonStates());

        // Double-click listener
        productsTreeGrid.addItemDoubleClickListener(e -> {
            ProductTreeItem item = e.getItem();
            if (item != null && item.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT) {
                openProductEditor(item.getProduct());
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

    private Component createImageComponent(ProductTreeItem item) {
        if (item.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT) {
            Product product = item.getProduct();
            if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                Image image = new Image(product.getImageUrls().get(0), "Product");
                image.setWidth("50px");
                image.setHeight("50px");
                image.addClassName("product-image");
                return image;
            }
        }
        return new Span("");
    }

    private Component createPriceComponent(ProductTreeItem item) {
        if (item.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT) {
            Product product = item.getProduct();
            if (product.getOriginalPrice() != null) {
                return new Span(String.format("%.2f UAH", product.getOriginalPrice()));
            }
        } else if (item.getTotalValue() != null) {
            return new Span(String.format("%.2f UAH", item.getTotalValue()));
        }
        return new Span("");
    }

    private Component createStockComponent(ProductTreeItem item) {
        if (item.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT) {
            Product product = item.getProduct();
            Span stockSpan = new Span(String.valueOf(product.getStock()));
            if (product.getStock() == 0) {
                stockSpan.addClassName("stock-zero");
            } else if (product.getStock() < 10) {
                stockSpan.addClassName("stock-low");
            }
            return stockSpan;
        } else if (item.getProductCount() > 0) {
            return new Span(item.getProductCount() + " items");
        }
        return new Span("");
    }

    private Component createStatusComponent(ProductTreeItem item) {
        if (item.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT) {
            Product product = item.getProduct();
            Span badge = new Span(product.getStatus().name());
            badge.addClassName("status-badge");

            switch (product.getStatus()) {
                case ACTIVE:
                    badge.addClassName("status-active");
                    break;
                case INACTIVE:
                    badge.addClassName("status-inactive");
                    break;
                case DRAFT:
                    badge.addClassName("status-draft");
                    break;
                default:
                    badge.addClassName("status-other");
            }
            return badge;
        }
        return new Span("");
    }

    private Component createSourceComponent(ProductTreeItem item) {
        if (item.getSourceType() != null) {
            Span sourceSpan = new Span(item.getSourceType().name());
            sourceSpan.addClassName("source-badge");
            return sourceSpan;
        }
        return new Span("");
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

    @Subscribe("expandAllButton")
    public void onExpandAllButtonClick(ClickEvent<JmixButton> event) {
        expandAll(treeItems);
    }

    @Subscribe("collapseAllButton")
    public void onCollapseAllButtonClick(ClickEvent<JmixButton> event) {
        collapseAll(treeItems);
    }

    private void expandAll(List<ProductTreeItem> items) {
        for (ProductTreeItem item : items) {
            if (item.hasChildren()) {
                productsTreeGrid.expand(item);
                expandAll(item.getChildren());
            }
        }
    }

    private void collapseAll(List<ProductTreeItem> items) {
        for (ProductTreeItem item : items) {
            if (item.hasChildren()) {
                productsTreeGrid.collapse(item);
                collapseAll(item.getChildren());
            }
        }
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
        ProductTreeItem selected = productsTreeGrid.getSingleSelectedItem();
        if (selected != null && selected.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT) {
            openProductEditor(selected.getProduct());
        }
    }

    @Subscribe("archiveSelectedButton")
    public void onArchiveSelectedButtonClick(ClickEvent<JmixButton> event) {
        Set<ProductTreeItem> selectedItems = productsTreeGrid.getSelectedItems();
        List<Product> productsToArchive = selectedItems.stream()
                .filter(item -> item.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT)
                .map(ProductTreeItem::getProduct)
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

    private void loadProducts() {
        DataSet dataSet = getEditedEntity();
        if (dataSet != null && dataSet.getId() != null) {
            String query = "select p from Product p join p.datasets d where d.id = :datasetId order by p.sourceType, p.groupId, p.name";

            allProducts = dataManager.load(Product.class)
                    .query(query)
                    .parameter("datasetId", dataSet.getId())
                    .list();

            buildTreeStructure(allProducts);
        }
    }

    private void applyFilters() {
        ProductFilterCriteria criteria = buildFilterCriteria();
        List<Product> filteredProducts = filterProducts(allProducts, criteria);
        buildTreeStructure(filteredProducts);

        notifications.create("Applied filters, showing " + filteredProducts.size() + " products")
                .withType(Notifications.Type.DEFAULT)
                .show();
    }

    private ProductFilterCriteria buildFilterCriteria() {
        ProductFilterCriteria criteria = new ProductFilterCriteria();

        criteria.setNameFilter(nameFilter.getValue());
        criteria.setCategoryFilter(categoryFilter.getValue());
        criteria.setDescriptionFilter(descriptionFilter.getValue());

        // Для single select замість multi select
        if (sourceTypeFilter.getValue() != null) {
            criteria.setSourceTypes(Set.of(sourceTypeFilter.getValue()));
        }

        criteria.setMinPrice(BigDecimal.valueOf(Double.parseDouble(minPriceFilter.getValue())));
        criteria.setMaxPrice(BigDecimal.valueOf(Double.parseDouble(maxPriceFilter.getValue())));
        criteria.setMinStock(Integer.parseInt(minStockFilter.getValue()));
        criteria.setMaxStock(Integer.parseInt(maxStockFilter.getValue()));
        criteria.setAvailableOnly(availableOnlyFilter.getValue());

        if (statusFilter.getValue() != null) {
            criteria.setStatuses(Set.of(statusFilter.getValue()));
        }

        criteria.setAiOptimizedOnly(aiOptimizedOnlyFilter.getValue());
        criteria.setHasImages(hasImagesFilter.getValue());
        criteria.setHasGroupId(hasGroupIdFilter.getValue());

        return criteria;
    }

    private List<Product> filterProducts(List<Product> products, ProductFilterCriteria criteria) {
        return products.stream()
                .filter(product -> matchesCriteria(product, criteria))
                .collect(Collectors.toList());
    }

    private boolean matchesCriteria(Product product, ProductFilterCriteria criteria) {
        // Name filter
        if (criteria.getNameFilter() != null && !criteria.getNameFilter().trim().isEmpty()) {
            if (!product.getName().toLowerCase().contains(criteria.getNameFilter().toLowerCase())) {
                return false;
            }
        }

        // Category filter
        if (criteria.getCategoryFilter() != null && !criteria.getCategoryFilter().trim().isEmpty()) {
            if (product.getExternalCategoryName() == null ||
                    !product.getExternalCategoryName().toLowerCase().contains(criteria.getCategoryFilter().toLowerCase())) {
                return false;
            }
        }

        // Description filter
        if (criteria.getDescriptionFilter() != null && !criteria.getDescriptionFilter().trim().isEmpty()) {
            if (product.getDescription() == null ||
                    !product.getDescription().toLowerCase().contains(criteria.getDescriptionFilter().toLowerCase())) {
                return false;
            }
        }

        // Source type filter
        if (criteria.getSourceTypes() != null && !criteria.getSourceTypes().isEmpty()) {
            if (!criteria.getSourceTypes().contains(product.getSourceType())) {
                return false;
            }
        }

        // Price filters
        if (criteria.getMinPrice() != null &&
                (product.getOriginalPrice() == null || product.getOriginalPrice().compareTo(criteria.getMinPrice()) < 0)) {
            return false;
        }
        if (criteria.getMaxPrice() != null &&
                (product.getOriginalPrice() == null || product.getOriginalPrice().compareTo(criteria.getMaxPrice()) > 0)) {
            return false;
        }

        // Stock filters
        if (criteria.getMinStock() != null && product.getStock() < criteria.getMinStock()) {
            return false;
        }
        if (criteria.getMaxStock() != null && product.getStock() > criteria.getMaxStock()) {
            return false;
        }

        // Available filter
        if (criteria.getAvailableOnly() != null && criteria.getAvailableOnly() && !product.getAvailable()) {
            return false;
        }

        // Status filter
        if (criteria.getStatuses() != null && !criteria.getStatuses().isEmpty()) {
            if (!criteria.getStatuses().contains(product.getStatus())) {
                return false;
            }
        }

        // AI optimized filter
        if (criteria.getAiOptimizedOnly() != null && criteria.getAiOptimizedOnly() && !product.getAiOptimized()) {
            return false;
        }

        // Has images filter
        if (criteria.getHasImages() != null && criteria.getHasImages()) {
            if (product.getImageUrls() == null || product.getImageUrls().isEmpty()) {
                return false;
            }
        }

        // Has group ID filter
        if (criteria.getHasGroupId() != null && criteria.getHasGroupId()) {
            if (product.getGroupId() == null || product.getGroupId().trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private void buildTreeStructure(List<Product> products) {
        treeItems.clear();

        // Group by source type
        Map<SourceType, List<Product>> sourceGroups = products.stream()
                .collect(Collectors.groupingBy(Product::getSourceType));

        for (Map.Entry<SourceType, List<Product>> sourceEntry : sourceGroups.entrySet()) {
            SourceType sourceType = sourceEntry.getKey();
            List<Product> sourceProducts = sourceEntry.getValue();

            // Calculate totals for source group
            BigDecimal sourceTotalValue = sourceProducts.stream()
                    .filter(p -> p.getOriginalPrice() != null)
                    .map(Product::getOriginalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ProductTreeItem sourceGroup = ProductTreeItem.createSourceGroup(
                    sourceType, sourceProducts.size(), sourceTotalValue);

            // Group by category within source
            Map<String, List<Product>> categoryGroups = sourceProducts.stream()
                    .collect(Collectors.groupingBy(p ->
                            p.getExternalCategoryName() != null ? p.getExternalCategoryName() : "Uncategorized"));

            for (Map.Entry<String, List<Product>> categoryEntry : categoryGroups.entrySet()) {
                String category = categoryEntry.getKey();
                List<Product> categoryProducts = categoryEntry.getValue();

                BigDecimal categoryTotalValue = categoryProducts.stream()
                        .filter(p -> p.getOriginalPrice() != null)
                        .map(Product::getOriginalPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                ProductTreeItem categoryGroup = ProductTreeItem.createCategoryGroup(
                        category, sourceType, categoryProducts.size(), categoryTotalValue);

                // Group by groupId within category
                Map<String, List<Product>> variantGroups = categoryProducts.stream()
                        .collect(Collectors.groupingBy(p ->
                                p.getGroupId() != null ? p.getGroupId() : "single_" + p.getId()));

                for (Map.Entry<String, List<Product>> variantEntry : variantGroups.entrySet()) {
                    String groupId = variantEntry.getKey();
                    List<Product> variantProducts = variantEntry.getValue();

                    if (variantProducts.size() > 1 && !groupId.startsWith("single_")) {
                        // Create variant group for multiple products
                        BigDecimal variantTotalValue = variantProducts.stream()
                                .filter(p -> p.getOriginalPrice() != null)
                                .map(Product::getOriginalPrice)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                        ProductTreeItem variantGroup = ProductTreeItem.createVariantGroup(
                                groupId, sourceType, category, variantProducts.size(), variantTotalValue);

                        for (Product product : variantProducts) {
                            ProductTreeItem productItem = ProductTreeItem.createProduct(product);
                            variantGroup.addChild(productItem);
                        }

                        categoryGroup.addChild(variantGroup);
                    } else {
                        // Add single products directly
                        for (Product product : variantProducts) {
                            ProductTreeItem productItem = ProductTreeItem.createProduct(product);
                            categoryGroup.addChild(productItem);
                        }
                    }
                }

                sourceGroup.addChild(categoryGroup);
            }

            treeItems.add(sourceGroup);
        }

        // Update tree grid - правильний спосіб для Jmix 2.5
        treeItemsDc.setItems(treeItems);

        // Expand first level by default
        for (ProductTreeItem item : treeItems) {
            productsTreeGrid.expand(item);
        }
    }

    private void clearAllFilters() {
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

        buildTreeStructure(allProducts);

        notifications.create("Filters cleared")
                .withType(Notifications.Type.DEFAULT)
                .show();
    }

    private void updateButtonStates() {
        Set<ProductTreeItem> selectedItems = productsTreeGrid.getSelectedItems();

        boolean hasProductSelected = selectedItems.stream()
                .anyMatch(item -> item.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT);

        ProductTreeItem singleSelected = productsTreeGrid.getSingleSelectedItem();
        boolean singleProductSelected = singleSelected != null &&
                singleSelected.getType() == ProductTreeItem.ProductTreeItemType.PRODUCT;

        archiveSelectedButton.setEnabled(hasProductSelected);
        editProductButton.setEnabled(singleProductSelected);
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