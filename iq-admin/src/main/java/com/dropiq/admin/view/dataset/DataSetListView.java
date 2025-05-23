package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.service.DataSetService;
import com.dropiq.admin.service.EngineIntegrationService;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "datasets", layout = MainView.class)
@ViewController(id = "DataSet.list")
@ViewDescriptor(path = "dataset-list-view.xml")
@LookupComponent("datasetsDataGrid")
@DialogMode(width = "80em", height = "70em")
public class DataSetListView extends StandardListView<DataSet> {

    @ViewComponent
    private DataGrid<DataSet> datasetsDataGrid;

    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private Notifications notifications;

    @Autowired
    private DataSetService datasetService;

    @Autowired
    private EngineIntegrationService integrationService;

    @Subscribe("datasetsDataGrid.syncAction")
    public void onDatasetsDataGridSyncAction(final ActionPerformedEvent event) {
        DataSet selectedDataset = datasetsDataGrid.getSingleSelectedItem();
        if (selectedDataset != null) {
            try {
                notifications.create("Synchronization started")
                        .withType(Notifications.Type.DEFAULT)
                        .show();

                integrationService.syncDataset(selectedDataset);

                getViewData().loadAll();

                notifications.create("Synchronization completed")
                        .withType(Notifications.Type.SUCCESS)
                        .show();

            } catch (Exception e) {
                notifications.create("Sync failed: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    @Subscribe("datasetsDataGrid.optimizeAction")
    public void onDatasetsDataGridOptimizeAction(final ActionPerformedEvent event) {
        DataSet selectedDataset = datasetsDataGrid.getSingleSelectedItem();
        if (selectedDataset != null) {
            try {
                notifications.create("AI optimization started")
                        .withType(Notifications.Type.DEFAULT)
                        .show();

                integrationService.optimizeDataset(selectedDataset);

                getViewData().loadAll();

                notifications.create("AI optimization completed")
                        .withType(Notifications.Type.SUCCESS)
                        .show();

            } catch (Exception e) {
                notifications.create("Optimization failed: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        }
    }

    @Subscribe("datasetsDataGrid.exportAction")
    public void onDatasetsDataGridExportAction(final ActionPerformedEvent event) {
        DataSet selectedDataset = datasetsDataGrid.getSingleSelectedItem();
        if (selectedDataset != null) {
            // TODO: Open export dialog
            notifications.create("Export functionality will be implemented")
                    .withType(Notifications.Type.DEFAULT)
                    .show();
        }
    }

    @Subscribe("datasetsDataGrid.viewProductsAction")
    public void onDatasetsDataGridViewProductsAction(final ActionPerformedEvent event) {
        DataSet selectedDataset = datasetsDataGrid.getSingleSelectedItem();
        if (selectedDataset != null) {
            // Navigate to products view filtered by dataset
            // Using proper Jmix navigation
//            getViewNavigators().detailView(selectedDataset)
//                    .withViewClass(DataSetDetailView.class)
//                    .navigate();
        }
    }
}