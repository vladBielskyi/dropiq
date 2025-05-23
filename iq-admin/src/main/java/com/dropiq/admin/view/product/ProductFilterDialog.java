package com.dropiq.admin.view.product;

import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.ProductOptimizationStatus;
import com.dropiq.admin.service.ProductService;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.function.Consumer;

@ViewController(id = "ProductFilterDialog")
@ViewDescriptor(path = "product-filter-dialog.xml")
@DialogMode(width = "40em", height = "35em")
public class ProductFilterDialog extends StandardView {

    @ViewComponent
    private TextField searchTermField;

    @ViewComponent
    private Checkbox activeOnlyField;

    @ViewComponent
    private Checkbox availableOnlyField;

    @ViewComponent
    private BigDecimalField minPriceField;

    @ViewComponent
    private BigDecimalField maxPriceField;

    @ViewComponent
    private TextField categoryField;

    @ViewComponent
    private ComboBox<ProductOptimizationStatus> optimizationStatusField;

    @Autowired
    private ProductService productService;

    private Consumer<ProductService.ProductFilter> onFilterAppliedCallback;

    @Subscribe
    public void onInit(final InitEvent event) {
        optimizationStatusField.setItems(ProductOptimizationStatus.values());
        optimizationStatusField.setItemLabelGenerator(ProductOptimizationStatus::getDisplayName);
    }

    @Subscribe("applyFilterButton")
    public void onApplyFilterButtonClick(final ClickEvent<Button> event) {
        ProductService.ProductFilter filter = new ProductService.ProductFilter();

        filter.setSearchTerm(searchTermField.getValue());
        filter.setActiveOnly(activeOnlyField.getValue());
        filter.setAvailableOnly(availableOnlyField.getValue());
        filter.setMinPrice(minPriceField.getValue());
        filter.setMaxPrice(maxPriceField.getValue());
        filter.setCategory(categoryField.getValue());
        filter.setOptimizationStatus(optimizationStatusField.getValue());

        if (onFilterAppliedCallback != null) {
            onFilterAppliedCallback.accept(filter);
        }

        close(StandardOutcome.SAVE);
    }

    @Subscribe("clearFilterButton")
    public void onClearFilterButtonClick(final ClickEvent<Button> event) {
        searchTermField.clear();
        activeOnlyField.setValue(false);
        availableOnlyField.setValue(false);
        minPriceField.clear();
        maxPriceField.clear();
        categoryField.clear();
        optimizationStatusField.clear();
    }

    @Subscribe("cancelButton")
    public void onCancelButtonClick(final ClickEvent<Button> event) {
        close(StandardOutcome.DISCARD);
    }

    public void setOnFilterAppliedCallback(Consumer<ProductService.ProductFilter> callback) {
        this.onFilterAppliedCallback = callback;
    }
}