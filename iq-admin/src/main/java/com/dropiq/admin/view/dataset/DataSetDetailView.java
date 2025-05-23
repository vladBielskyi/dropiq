package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.DataSource;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.DatasetStatus;
import com.dropiq.admin.service.DataSetService;
import com.dropiq.admin.service.EngineIntegrationService;
import com.dropiq.admin.service.ProductService;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

@Route(value = "datasets/:id", layout = MainView.class)
@ViewController(id = "Dataset.detail")
@ViewDescriptor(path = "dataset-detail-view.xml")
@EditedEntityContainer("datasetDc")
public class DataSetDetailView extends StandardDetailView<DataSet> {

    @ViewComponent
    private TypedTextField<String> nameField;

    @ViewComponent
    private ComboBox<DataSource> dataSourceField;

    @ViewComponent
    private ComboBox<DatasetStatus> statusField;

    @ViewComponent
    private BigDecimalField defaultMarkupField;

    @ViewComponent
    private BigDecimalField minProfitMarginField;

    @ViewComponent
    private Checkbox aiOptimizationEnabledField;

    @ViewComponent
    private Checkbox seoOptimizationEnabledField;

    @ViewComponent
    private Checkbox trendAnalysisEnabledField;

    @ViewComponent
    private Checkbox imageAnalysisEnabledField;

    @ViewComponent
    private DataGrid<Product> productsDataGrid;

    @ViewComponent
    private CollectionContainer<Product> productsDc;

    @ViewComponent
    private CollectionLoader<Product> productsDl;

    @ViewComponent
    private Button syncButton;

    @ViewComponent
    private Button optimizeButton;

    @ViewComponent
    private Button createFromSourceButton;

    @ViewComponent
    private Button bulkActionsButton;

    @ViewComponent
    private Button refreshProductsButton;

    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private Notifications notifications;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private DataSetService datasetService;

    @Autowired
    private EngineIntegrationService integrationService;

    @Autowired
    private ProductService productService;

    @Subscribe
    public void onInit(final InitEvent event) {
        statusField.setItems(DatasetStatus.values());
        statusField.setItemLabelGenerator(DatasetStatus::getDisplayName);
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<DataSet> event) {
        if (entityStates.isNew(event.getEntity())) {
            event.getEntity().setCreatedBy("admin"); // TODO: Get from UserSession
            event.getEntity().setStatus(DatasetStatus.DRAFT);
        }
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        updateButtonStates();
        loadProducts();
        updateProductCountDisplay();
    }

    @Subscribe("createFromSourceButton")
    public void onCreateFromSourceButtonClick(final ClickEvent<Button> event) {
        DataSet dataset = getEditedEntity();
        if (dataset.getDataSource() == null) {
            notifications.create("Please select a data source first")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            notifications.create("Creating dataset from source...")
                    .withType(Notifications.Type.DEFAULT)
                    .show();

            DataSet createdDataset = integrationService.createDatasetFromSource(
                    dataset.getDataSource(),
                    dataset.getName(),
                    dataset.getDescription()
            );

            // Update current dataset with created data
            dataset.setStatus(createdDataset.getStatus());
            dataset.setTotalProducts(createdDataset.getTotalProducts());
            dataset.setActiveProducts(createdDataset.getActiveProducts());
            dataset.getMetadata().putAll(createdDataset.getMetadata());

            updateButtonStates();
            loadProducts();
            updateProductCountDisplay();

            notifications.create("Dataset created successfully")
                    .withType(Notifications.Type.SUCCESS)
                    .show();

        } catch (Exception e) {
            notifications.create("Failed to create dataset: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("syncButton")
    public void onSyncButtonClick(final ClickEvent<Button> event) {
        DataSet dataset = getEditedEntity();

        try {
            notifications.create("Synchronization started...")
                    .withType(Notifications.Type.DEFAULT)
                    .show();

            integrationService.syncDataset(dataset);

            updateButtonStates();
            loadProducts();
            updateProductCountDisplay();

            notifications.create("Synchronization completed")
                    .withType(Notifications.Type.SUCCESS)
                    .show();

        } catch (Exception e) {
            notifications.create("Sync failed: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("optimizeButton")
    public void onOptimizeButtonClick(final ClickEvent<Button> event) {
        DataSet dataset = getEditedEntity();

        if (!dataset.getAiOptimizationEnabled()) {
            notifications.create("Please enable AI optimization first")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            notifications.create("AI optimization started...")
                    .withType(Notifications.Type.DEFAULT)
                    .show();

            integrationService.optimizeDataset(dataset);

            updateButtonStates();
            loadProducts();
            updateProductCountDisplay();

            notifications.create("AI optimization completed")
                    .withType(Notifications.Type.SUCCESS)
                    .show();

        } catch (Exception e) {
            notifications.create("Optimization failed: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("bulkActionsButton")
    public void onBulkActionsButtonClick(final ClickEvent<Button> event) {
        Set<Product> selectedProducts = productsDataGrid.getSelectedItems();

        if (selectedProducts.isEmpty()) {
            notifications.create("Please select products first")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        // TODO: Open bulk operations dialog
        notifications.create("Bulk operations dialog will be implemented")
                .withType(Notifications.Type.DEFAULT)
                .show();
    }

    @Subscribe("refreshProductsButton")
    public void onRefreshProductsButtonClick(final ClickEvent<Button> event) {
        loadProducts();
        updateProductCountDisplay();
    }

    @Subscribe("dataSourceField")
    public void onDataSourceFieldValueChange(final AbstractField.ComponentValueChangeEvent<ComboBox<DataSource>, DataSource> event) {
        updateButtonStates();
    }

    @Subscribe("productsDataGrid")
    public void onProductsDataGridSelection(final DataGrid.SelectionEvent<Product> event) {
        updateBulkActionButtons();
    }

    private void updateButtonStates() {
        DataSet dataset = getEditedEntity();
        boolean hasDataSource = dataset.getDataSource() != null;
        boolean isNew = entityStates.isNew(dataset);
        boolean hasProducts = dataset.getTotalProducts() != null && dataset.getTotalProducts() > 0;

        createFromSourceButton.setEnabled(hasDataSource && (isNew || dataset.getStatus() == DatasetStatus.DRAFT));
        syncButton.setEnabled(hasDataSource && !isNew && hasProducts);
        optimizeButton.setEnabled(hasProducts && dataset.getAiOptimizationEnabled());
    }

    private void updateBulkActionButtons() {
        Set<Product> selectedProducts = productsDataGrid.getSelectedItems();
        bulkActionsButton.setEnabled(!selectedProducts.isEmpty());
    }

    private void loadProducts() {
        DataSet dataset = getEditedEntity();
        if (!entityStates.isNew(dataset) && dataset.getId() != null) {
            productsDl.setParameter("dataset", dataset);
            productsDl.load();
        }
    }

    private void updateProductCountDisplay() {
        int productCount = productsDc.getItems().size();
        // Update any product count displays in the UI
        // This would be handled by the UI binding in the XML
    }

    // Product grid action handlers
    @Subscribe("productsDataGrid.activateAction")
    public void onProductsDataGridActivateAction(final io.jmix.flowui.kit.action.ActionPerformedEvent event) {
        Set<Product> selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            try {
                productService.bulkActivateProducts(selectedProducts);
                loadProducts();
                updateProductCountDisplay();

                notifications.create(String.format("Activated %d products", selectedProducts.size()))
                        .withType(Notifications.Type.SUCCESS)
                        .show();
            } catch (Exception e) {
                notifications.create("Failed to activate products: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    @Subscribe("productsDataGrid.deactivateAction")
    public void onProductsDataGridDeactivateAction(final io.jmix.flowui.kit.action.ActionPerformedEvent event) {
        Set<Product> selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            try {
                productService.bulkDeactivateProducts(selectedProducts);
                loadProducts();
                updateProductCountDisplay();

                notifications.create(String.format("Deactivated %d products", selectedProducts.size()))
                        .withType(Notifications.Type.SUCCESS)
                        .show();
            } catch (Exception e) {
                notifications.create("Failed to deactivate products: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    @Subscribe("productsDataGrid.optimizeAction")
    public void onProductsDataGridOptimizeAction(final io.jmix.flowui.kit.action.ActionPerformedEvent event) {
        Set<Product> selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            try {
                notifications.create("Starting AI optimization for selected products...")
                        .withType(Notifications.Type.DEFAULT)
                        .show();

                productService.optimizeProducts(selectedProducts);

                loadProducts();
                updateProductCountDisplay();

                notifications.create(String.format("Optimized %d products", selectedProducts.size()))
                        .withType(Notifications.Type.SUCCESS)
                        .show();
            } catch (Exception e) {
                notifications.create("Failed to optimize products: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }
}