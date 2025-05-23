package com.dropiq.admin.view.product;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.ProductOptimizationStatus;
import com.dropiq.admin.service.ProductService;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

@Route(value = "products/:id", layout = MainView.class)
@ViewController(id = "Product.detail")
@ViewDescriptor(path = "product-detail-view.xml")
@EditedEntityContainer("productDc")
public class ProductDetailView extends StandardDetailView<Product> {

    @ViewComponent
    private TypedTextField<String> nameField;

    @ViewComponent
    private TypedTextField<String> descriptionField;

    @ViewComponent
    private BigDecimalField originalPriceField;

    @ViewComponent
    private BigDecimalField sellingPriceField;

    @ViewComponent
    private BigDecimalField markupPercentageField;

    @ViewComponent
    private TextField stockField;

    @ViewComponent
    private ComboBox<DataSet> datasetField;

    @ViewComponent
    private ComboBox<ProductOptimizationStatus> optimizationStatusField;

    @ViewComponent
    private JmixButton optimizeButton;

    @ViewComponent
    private JmixButton calculatePriceButton;

    @ViewComponent
    private MessageBundle messageBundle;

    @Autowired
    private Notifications notifications;

    @Autowired
    private EntityStates entityStates;

    @Autowired
    private ProductService productService;

    @Subscribe
    public void onInit(final InitEvent event) {
        optimizationStatusField.setItems(ProductOptimizationStatus.values());
        optimizationStatusField.setItemLabelGenerator(ProductOptimizationStatus::getDisplayName);
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<Product> event) {
        if (entityStates.isNew(event.getEntity())) {
            event.getEntity().setCreatedBy("admin"); // TODO: Get from UserSession
            event.getEntity().setOptimizationStatus(ProductOptimizationStatus.PENDING);
        }
    }

    @Subscribe("markupPercentageField")
    public void onMarkupPercentageFieldValueChange(final AbstractField.ComponentValueChangeEvent<BigDecimalField, BigDecimal> event) {
        calculateSellingPrice();
    }

    @Subscribe("originalPriceField")
    public void onOriginalPriceFieldValueChange(final AbstractField.ComponentValueChangeEvent<BigDecimalField, BigDecimal> event) {
        calculateSellingPrice();
    }

    @Subscribe("calculatePriceButton")
    public void onCalculatePriceButtonClick(final ClickEvent<Button> event) {
        calculateSellingPrice();
    }

    @Subscribe("optimizeButton")
    public void onOptimizeButtonClick(final ClickEvent<Button> event) {
        Product product = getEditedEntity();

        try {
            notifications.create("Starting AI optimization...")
                    .withType(Notifications.Type.DEFAULT)
                    .show();

            productService.optimizeProduct(product);

            // Refresh the view to show optimized data
            getViewData().loadAll();

            notifications.create(messageBundle.getMessage("notification.productOptimized"))
                    .withType(Notifications.Type.SUCCESS)
                    .show();

        } catch (Exception e) {
            notifications.create("Optimization failed: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    private void calculateSellingPrice() {
        Product product = getEditedEntity();

        if (product.getOriginalPrice() != null && product.getMarkupPercentage() != null) {
            BigDecimal originalPrice = product.getOriginalPrice();
            BigDecimal markupPercentage = product.getMarkupPercentage();

            BigDecimal markup = originalPrice
                    .multiply(markupPercentage)
                    .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);

            BigDecimal sellingPrice = originalPrice.add(markup);
            product.setSellingPrice(sellingPrice);

            // Update the field
            sellingPriceField.setValue(sellingPrice);

            // Calculate profit margin
            if (originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal profitMargin = markup
                        .divide(originalPrice, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                product.setProfitMargin(profitMargin);
            }
        }
    }

    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        Product product = getEditedEntity();

        // Ensure selling price is calculated
        if (product.getSellingPrice() == null &&
                product.getOriginalPrice() != null &&
                product.getMarkupPercentage() != null) {
            calculateSellingPrice();
        }

        // Set primary image if not set
        if (product.getPrimaryImageUrl() == null &&
                product.getImageUrls() != null &&
                !product.getImageUrls().isEmpty()) {
            product.setPrimaryImageUrl(product.getImageUrls().get(0));
        }
    }
}
