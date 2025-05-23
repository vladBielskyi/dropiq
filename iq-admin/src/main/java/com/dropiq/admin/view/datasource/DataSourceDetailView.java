package com.dropiq.admin.view.datasource;

import com.dropiq.admin.entity.DataSource;
import com.dropiq.admin.model.DataSourceType;
import com.dropiq.admin.service.DataSourceService;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.*;

@Route(value = "datasources/:id", layout = MainView.class)
@ViewController(id = "DataSource.detail")
@ViewDescriptor(path = "datasource-detail-view.xml")
@EditedEntityContainer("dataSourceDc")
public class DataSourceDetailView extends StandardDetailView<DataSource> {

    @ViewComponent
    private ComboBox<DataSourceType> sourceTypeField;

    @ViewComponent
    private TextField urlField;

    @ViewComponent
    private TextArea configurationField;

    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private Notifications notifications;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private DataSourceService dataSourceService;

    @Subscribe
    public void onInit(final InitEvent event) {
        sourceTypeField.setItems(DataSourceType.values());
        sourceTypeField.setItemLabelGenerator(DataSourceType::getDisplayName);
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<DataSource> event) {
        if (entityStates.isNew(event.getEntity())) {
            event.getEntity().setCreatedBy("admin"); // TODO: Get from UserSession
        }
    }

    @Subscribe("sourceTypeField")
    public void onSourceTypeFieldValueChange(final AbstractField.ComponentValueChangeEvent<ComboBox<DataSourceType>, DataSourceType> event) {
        DataSourceType selectedType = event.getValue();
        if (selectedType != null) {
            updateFieldsForSourceType(selectedType);
        }
    }

    private void updateFieldsForSourceType(DataSourceType sourceType) {
        switch (sourceType) {
            case MYDROP:
                urlField.setLabel("MyDrop API URL");
                urlField.setPlaceholder("https://backend.mydrop.com.ua/vendor/api/export/...");
                break;
            case EASYDROP:
                urlField.setLabel("EasyDrop API URL");
                urlField.setPlaceholder("https://easydrop.one/prom-export?key=...");
                break;
            case CSV_FILE:
            case EXCEL_FILE:
            case XML_FILE:
                urlField.setLabel("File Path");
                urlField.setPlaceholder("/path/to/file or upload URL");
                break;
            case CUSTOM_API:
                urlField.setLabel("Custom API Endpoint");
                urlField.setPlaceholder("https://your-api.com/products");
                break;
        }
    }

    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        DataSource dataSource = getEditedEntity();

        // Validate required fields based on source type
        if (dataSource.getSourceType() != DataSourceType.MANUAL_ENTRY &&
                (dataSource.getUrl() == null || dataSource.getUrl().trim().isEmpty())) {

            notifications.create(messageBundle.getMessage("urlRequired"))
                    .withType(Notifications.Type.WARNING)
                    .withPosition(Notification.Position.TOP_END)
                    .show();
            event.preventSave();
            return;
        }

        // Test connection if it's a new data source
        if (entityStates.isNew(dataSource)) {
            try {
                boolean connectionValid = dataSourceService.testConnection(dataSource);
                if (!connectionValid) {
                    notifications.create(messageBundle.getMessage("connectionTestFailed"))
                            .withType(Notifications.Type.ERROR)
                            .withPosition(Notification.Position.TOP_END)
                            .show();
                    event.preventSave();
                    return;
                }
            } catch (Exception e) {
                notifications.create("Connection test failed: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .withPosition(Notification.Position.TOP_END)
                        .show();
                event.preventSave();
                return;
            }
        }

        notifications.create(messageBundle.getMessage("dataSourceSaved"))
                .withType(Notifications.Type.SUCCESS)
                .withPosition(Notification.Position.TOP_END)
                .show();
    }
}
