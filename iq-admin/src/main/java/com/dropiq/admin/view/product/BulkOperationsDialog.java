package com.dropiq.admin.view.product;

import com.dropiq.admin.entity.Product;
import com.dropiq.admin.service.ProductService;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.view.*;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Collection;

@ViewController(id = "BulkOperationsDialog")
@ViewDescriptor(path = "bulk-operations-dialog.xml")
@DialogMode(width = "40em", height = "30em")
public class BulkOperationsDialog extends StandardView {

    public enum BulkOperationType {
        ACTIVATE_ALL("Activate All", "Activate all selected products"),
        DEACTIVATE_ALL("Deactivate All", "Deactivate all selected products"),
        APPLY_MARKUP("Apply Markup", "Apply markup percentage to all selected products"),
        SET_CATEGORY("Set Category", "Set category for all selected products"),
        DELETE_INACTIVE("Delete Inactive", "Delete all inactive products");

        private final String displayName;
        private final String description;

        BulkOperationType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    @ViewComponent
    private ComboBox<BulkOperationType> operationTypeField;

    @ViewComponent
    private BigDecimalField markupPercentageField;

    @ViewComponent
    private TextField categoryField;

    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private Notifications notifications;

    @Autowired
    private ProductService productService;

    @Setter
    private Collection<Product> selectedProducts;
    @Setter
    private Runnable onCompleteCallback;

    @Subscribe
    public void onInit(final InitEvent event) {
        operationTypeField.setItems(BulkOperationType.values());
        operationTypeField.setItemLabelGenerator(BulkOperationType::getDisplayName);

        // Initially hide all parameter fields
        markupPercentageField.setVisible(false);
        categoryField.setVisible(false);
    }

    @Subscribe("operationTypeField")
    public void onOperationTypeFieldValueChange(final AbstractField.ComponentValueChangeEvent<ComboBox<BulkOperationType>, BulkOperationType> event) {
        BulkOperationType selectedType = event.getValue();

        // Hide all parameter fields first
        markupPercentageField.setVisible(false);
        categoryField.setVisible(false);

        // Show relevant fields based on operation type
        if (selectedType == BulkOperationType.APPLY_MARKUP) {
            markupPercentageField.setVisible(true);
        } else if (selectedType == BulkOperationType.SET_CATEGORY) {
            categoryField.setVisible(true);
        }
    }

    @Subscribe("executeButton")
    public void onExecuteButtonClick(final ClickEvent<Button> event) {
        BulkOperationType operationType = operationTypeField.getValue();

        if (operationType == null) {
            notifications.create("Please select an operation type")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        if (selectedProducts == null || selectedProducts.isEmpty()) {
            notifications.create("No products selected")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            switch (operationType) {
                case ACTIVATE_ALL:
                    productService.bulkActivateProducts(selectedProducts);
                    notifications.create(messageBundle.getMessage("notification.productsActivated"))
                            .withType(Notifications.Type.SUCCESS)
                            .show();
                    break;

                case DEACTIVATE_ALL:
                    productService.bulkDeactivateProducts(selectedProducts);
                    notifications.create(messageBundle.getMessage("notification.productsDeactivated"))
                            .withType(Notifications.Type.SUCCESS)
                            .show();
                    break;

                case APPLY_MARKUP:
                    BigDecimal markup = markupPercentageField.getValue();
                    if (markup == null || markup.compareTo(BigDecimal.ZERO) <= 0) {
                        notifications.create("Please enter a valid markup percentage")
                                .withType(Notifications.Type.WARNING)
                                .show();
                        return;
                    }
                    productService.applyMarkupToProducts(selectedProducts, markup);
                    notifications.create(messageBundle.getMessage("notification.markupApplied"))
                            .withType(Notifications.Type.SUCCESS)
                            .show();
                    break;

                case SET_CATEGORY:
                    String category = categoryField.getValue();
                    if (category == null || category.trim().isEmpty()) {
                        notifications.create("Please enter a category name")
                                .withType(Notifications.Type.WARNING)
                                .show();
                        return;
                    }
                    productService.setCategoryForProducts(selectedProducts, category.trim());
                    notifications.create(messageBundle.getMessage("notification.categorySet"))
                            .withType(Notifications.Type.SUCCESS)
                            .show();
                    break;

                case DELETE_INACTIVE:
                    Collection<Product> inactiveProducts = selectedProducts.stream()
                            .filter(p -> !p.getActive())
                            .toList();
                    if (inactiveProducts.isEmpty()) {
                        notifications.create("No inactive products to delete")
                                .withType(Notifications.Type.DEFAULT)
                                .show();
                        return;
                    }
                    productService.deleteProducts(inactiveProducts);
                    notifications.create(String.format("Deleted %d inactive products", inactiveProducts.size()))
                            .withType(Notifications.Type.SUCCESS)
                            .show();
                    break;
            }

            // Call completion callback
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }

            // Close dialog
            close(StandardOutcome.SAVE);

        } catch (Exception e) {
            notifications.create("Operation failed: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("cancelButton")
    public void onCancelButtonClick(final ClickEvent<Button> event) {
        close(StandardOutcome.DISCARD);
    }

}
