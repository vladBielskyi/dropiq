package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.service.DataSetService;
import com.dropiq.admin.view.main.MainView;
import com.dropiq.admin.view.product.ProductDetailView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

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
    private TypedTextField<String> productNameFilter;

    @ViewComponent
    private TypedTextField<String> categoryFilter;

    @ViewComponent
    private JmixButton applyFilterButton;

    @ViewComponent
    private JmixButton clearFilterButton;

    @ViewComponent
    private CollectionContainer<Product> productsDc;

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

    @Subscribe
    public void onReady(ReadyEvent event) {
        setupProductGrid();
        updateButtonStates();
        loadProducts();
    }

    private void setupProductGrid() {
        // Configure selection
        productsDataGrid.setSelectionMode(Grid.SelectionMode.MULTI);

        // Add custom columns
        productsDataGrid.addColumn(new ComponentRenderer<>(this::createGroupComponent))
                .setHeader("Group")
                .setWidth("120px")
                .setFlexGrow(0);

        productsDataGrid.addColumn(new ComponentRenderer<>(this::createImageComponent))
                .setHeader("Image")
                .setWidth("80px")
                .setFlexGrow(0);

        productsDataGrid.addColumn(new ComponentRenderer<>(this::createStatusComponent))
                .setHeader("Status")
                .setWidth("100px")
                .setFlexGrow(0);

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

    private Span createGroupComponent(Product product) {
        String groupText = "";
        if (product.getGroupId() != null && !product.getGroupId().isEmpty()) {
            groupText = product.getGroupId();
        } else {
            groupText = "Single";
        }

        Span groupSpan = new Span(groupText);
        groupSpan.getElement().getThemeList().add("badge");
        groupSpan.getElement().getThemeList().add("contrast");

        // Add source type as subtitle
        Span sourceSpan = new Span(product.getSourceType().name());
        sourceSpan.getStyle().set("font-size", "0.8em");
        sourceSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        return groupSpan;
    }

    private Image createImageComponent(Product product) {
        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            Image image = new Image(product.getImageUrls().get(0), "Product");
            image.setWidth("50px");
            image.setHeight("50px");
            image.getStyle().set("object-fit", "cover");
            image.getStyle().set("border-radius", "4px");
            return image;
        } else {
            Span placeholder = new Span("📦");
            placeholder.getStyle().set("font-size", "24px");
            return new Image(); // Return empty image as placeholder
        }
    }

    private Span createStatusComponent(Product product) {
        Span badge = new Span(product.getStatus().name());
        badge.getElement().getThemeList().add("badge");

        switch (product.getStatus()) {
            case ACTIVE:
                badge.getElement().getThemeList().add("success");
                break;
            case INACTIVE:
                badge.getElement().getThemeList().add("error");
                break;
            case DRAFT:
                badge.getElement().getThemeList().add("contrast");
                break;
            default:
                badge.getElement().getThemeList().add("primary");
        }

        return badge;
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
        Set<Product> selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            try {
                dataSetService.archiveProducts(selectedProducts.stream().toList());

                notifications.create("Archived " + selectedProducts.size() + " products")
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

    @Subscribe("applyFilterButton")
    public void onApplyFilterButtonClick(ClickEvent<JmixButton> event) {
        applyFilters();
    }

    @Subscribe("clearFilterButton")
    public void onClearFilterButtonClick(ClickEvent<JmixButton> event) {
        clearFilters();
    }

    private void openProductEditor(Product product) {
        dialogWindows.detail(this, Product.class)
                .editEntity(product)
                .withViewClass(ProductDetailView.class)
                .withAfterCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(StandardOutcome.SAVE)) {
                        loadProducts(); // Refresh after save
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

            productsDc.setItems(allProducts);
        }
    }

    private void applyFilters() {
        List<Product> filteredProducts = allProducts;

        if (productNameFilter.getValue() != null && !productNameFilter.getValue().trim().isEmpty()) {
            String nameFilter = productNameFilter.getValue().toLowerCase();
            filteredProducts = filteredProducts.stream()
                    .filter(p -> p.getName().toLowerCase().contains(nameFilter))
                    .collect(Collectors.toList());
        }

        if (categoryFilter.getValue() != null && !categoryFilter.getValue().trim().isEmpty()) {
            String catFilter = categoryFilter.getValue().toLowerCase();
            filteredProducts = filteredProducts.stream()
                    .filter(p -> p.getExternalCategoryName() != null &&
                            p.getExternalCategoryName().toLowerCase().contains(catFilter))
                    .collect(Collectors.toList());
        }

        productsDc.setItems(filteredProducts);
    }

    private void clearFilters() {
        productNameFilter.clear();
        categoryFilter.clear();
        productsDc.setItems(allProducts);
    }

    private void updateButtonStates() {
        Set<Product> selectedItems = productsDataGrid.getSelectedItems();

        archiveSelectedButton.setEnabled(!selectedItems.isEmpty());
        editProductButton.setEnabled(selectedItems.size() == 1);
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