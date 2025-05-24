package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "data-sets", layout = MainView.class)
@ViewController(id = "DataSet.list")
@ViewDescriptor(path = "data-set-list-view.xml")
@LookupComponent("dataSetsDataGrid")
@DialogMode(width = "64em")
public class DataSetListView extends StandardListView<DataSet> {
}