package com.dropiq.admin.view.datasource;

import com.dropiq.admin.entity.DataSource;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "datasources", layout = MainView.class)
@ViewController(id = "DataSource.list")
@ViewDescriptor(path = "datasource-list-view.xml")
@LookupComponent("dataSourcesDataGrid")
@DialogMode(width = "64em")
public class DataSourceListView extends StandardListView<DataSource> {
}
