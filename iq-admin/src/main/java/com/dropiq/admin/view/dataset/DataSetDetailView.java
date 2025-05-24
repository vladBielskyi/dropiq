package com.dropiq.admin.view.dataset;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "data-sets/:id", layout = MainView.class)
@ViewController(id = "DataSet.detail")
@ViewDescriptor(path = "data-set-detail-view.xml")
@EditedEntityContainer("dataSetDc")
public class DataSetDetailView extends StandardDetailView<DataSet> {
}