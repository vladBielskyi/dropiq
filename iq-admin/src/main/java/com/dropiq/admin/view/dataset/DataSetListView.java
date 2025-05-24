package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.service.DataSetService;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.Route;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

@Route(value = "datasets", layout = MainView.class)
@ViewController("DataSet.list")
@ViewDescriptor(path = "data-set-list-view.xml")
@LookupComponent("dataSetsDataGrid")
@DialogMode(width = "80em", height = "60em")
public class DataSetListView extends StandardListView<DataSet> {

    @ViewComponent
    private DataGrid<DataSet> dataSetsDataGrid;

    @ViewComponent
    private JmixButton syncButton;

    @ViewComponent
    private JmixButton bulkSyncButton;

    @Autowired
    private DataSetService dataSetService;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private Notifications notifications;

    @Subscribe
    public void onReady(ReadyEvent event) {
        // Configure grid selection
        dataSetsDataGrid.setSelectionMode(Grid.SelectionMode.MULTI);

        // Update button states based on selection
        dataSetsDataGrid.addSelectionListener(selectionEvent -> {
            updateButtonStates();
        });

        updateButtonStates();
    }

    @Subscribe("syncButton")
    public void onSyncButtonClick(ClickEvent<JmixButton> event) {
        DataSet selected = dataSetsDataGrid.getSingleSelectedItem();
        if (selected != null) {
            performSync(selected);
        }
    }

    @Subscribe("bulkSyncButton")
    public void onBulkSyncButtonClick(ClickEvent<JmixButton> event) {
        Set<DataSet> selected = dataSetsDataGrid.getSelectedItems();
        if (!selected.isEmpty()) {
            performBulkSync(selected);
        }
    }

    private void performSync(DataSet dataSet) {
        try {
            String userId = currentAuthentication.getUser().getUsername();
            dataSetService.synchronizeDataSet(dataSet, userId);

            notifications.create("Synchronization started for: " + dataSet.getName())
                    .withType(Notifications.Type.SUCCESS)
                    .show();

            // Refresh the grid
            getViewData().loadAll();

        } catch (Exception e) {
            notifications.create("Failed to start synchronization: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    private void performBulkSync(Set<DataSet> dataSets) {
        try {
            String userId = currentAuthentication.getUser().getUsername();
            dataSetService.bulkSynchronize(dataSets.stream().toList(), userId);

            notifications.create("Bulk synchronization started for " + dataSets.size() + " datasets")
                    .withType(Notifications.Type.SUCCESS)
                    .show();

            // Refresh the grid
            getViewData().loadAll();

        } catch (Exception e) {
            notifications.create("Failed to start bulk synchronization: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    private void updateButtonStates() {
        Set<DataSet> selectedItems = dataSetsDataGrid.getSelectedItems();

        syncButton.setEnabled(selectedItems.size() == 1);
        bulkSyncButton.setEnabled(selectedItems.size() > 1);
    }
}