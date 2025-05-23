package com.dropiq.admin.view.product;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.service.ProductService;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Route(value = "products", layout = MainView.class)
@ViewController(id = "Product.list")
@ViewDescriptor(path = "product-list-view.xml")
@LookupComponent("productsDataGrid")
@DialogMode(width = "90em", height = "80em")
public class ProductListView extends StandardListView<Product> implements HasUrlParameter<String> {

    @ViewComponent
    private DataGrid<Product> productsDataGrid;

    @ViewComponent
    private CollectionLoader<Product> productsDl;

    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private Notifications notifications;

    @Autowired
    private ProductService productService;

    @Autowired
    private DataManager dataManager;

    private DataSet currentDataset;

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        if (parameter != null && parameter.startsWith("datasetId=")) {
            String datasetId = parameter.substring("datasetId=".length());
            try {
                UUID datasetUuid = UUID.fromString(datasetId);
                currentDataset = dataManager.load(DataSet.class).id(datasetUuid).one();
                filterByDataset(currentDataset);
            } catch (Exception e) {
                notifications.create("Invalid dataset ID: " + datasetId)
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        setupProductsGrid();
    }

    @Subscribe("productsDataGrid.activateAction")
    public void onProductsDataGridActivateAction(final ActionPerformedEvent event) {
        var selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            try {
                productService.bulkActivateProducts(selectedProducts);
                getViewData().loadAll();

                notifications.create(messageBundle.getMessage("notification.productsActivated"))
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
    public void onProductsDataGridDeactivateAction(final ActionPerformedEvent event) {
        var selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            try {
                productService.bulkDeactivateProducts(selectedProducts);
                getViewData().loadAll();

                notifications.create(messageBundle.getMessage("notification.productsDeactivated"))
                        .withType(Notifications.Type.SUCCESS)
                        .show();
            } catch (Exception e) {
                notifications.create("Failed to deactivate products: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    @Subscribe("productsDataGrid.applyMarkupAction")
    public void onProductsDataGridApplyMarkupAction(final ActionPerformedEvent event) {
        var selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            // TODO: Open markup dialog
            notifications.create("Markup dialog will be implemented")
                    .withType(Notifications.Type.DEFAULT)
                    .show();
        }
    }

    @Subscribe("productsDataGrid.optimizeAction")
    public void onProductsDataGridOptimizeAction(final ActionPerformedEvent event) {
        var selectedProducts = productsDataGrid.getSelectedItems();
        if (!selectedProducts.isEmpty()) {
            try {
                productService.optimizeProducts(selectedProducts);
                getViewData().loadAll();

                notifications.create(messageBundle.getMessage("notification.productsOptimized"))
                        .withType(Notifications.Type.SUCCESS)
                        .show();
            } catch (Exception e) {
                notifications.create("Failed to optimize products: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    private void setupProductsGrid() {
        // Add custom renderers and actions
        productsDataGrid.addComponentColumn(product -> {
            if (product.getPrimaryImageUrl() != null && !product.getPrimaryImageUrl().isEmpty()) {
                com.vaadin.flow.component.html.Image img = new com.vaadin.flow.component.html.Image(
                        product.getPrimaryImageUrl(), "Product Image");
                img.setWidth("40px");
                img.setHeight("40px");
                img.getStyle().set("object-fit", "cover");
                img.getStyle().set("border-radius", "4px");
                return img;
            }
            return new com.vaadin.flow.component.html.Span("No Image");
        }).setHeader("Image").setWidth("60px");
    }

    private void filterByDataset(DataSet dataset) {
        if (dataset != null) {
            productsDl.setQuery("select p from Product p where p.dataset = :dataset order by p.name");
            productsDl.setParameter("dataset", dataset);
            productsDl.load();
        }
    }
}
