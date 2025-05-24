package com.dropiq.admin.view.product;

import com.dropiq.admin.entity.Product;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

import com.dropiq.admin.entity.Product;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;

@Route(value = "products/:id", layout = MainView.class)
@ViewController("Product.detail")
@ViewDescriptor(path = "product-detail-view.xml")
@EditedEntityContainer("productDc")
public class ProductDetailView extends StandardDetailView<Product> {

    @ViewComponent
    private HorizontalLayout imageContainer;

    @ViewComponent
    private TypedTextField<String> calculatedSellingPriceField;

    @Subscribe
    public void onReady(ReadyEvent event) {
        loadProductImages();
        calculateSellingPrice();
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        // Recalculate selling price before saving
        calculateSellingPrice();
    }

    private void loadProductImages() {
        Product product = getEditedEntity();
        if (product != null && product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
            imageContainer.removeAll();

            // Show first 3 images
            product.getImageUrls().stream()
                    .limit(3)
                    .forEach(url -> {
                        Image img = new Image(url, "Product Image");
                        img.setWidth("80px");
                        img.setHeight("80px");
                        img.getStyle().set("object-fit", "cover");
                        img.getStyle().set("border-radius", "8px");
                        img.getStyle().set("margin-right", "10px");
                        imageContainer.add(img);
                    });
        }
    }

    private void calculateSellingPrice() {
        Product product = getEditedEntity();
        if (product != null && product.getOriginalPrice() != null && product.getMarkupPercentage() != null) {
            java.math.BigDecimal originalPrice = product.getOriginalPrice();
            java.math.BigDecimal markup = product.getMarkupPercentage();

            java.math.BigDecimal markupAmount = originalPrice.multiply(markup)
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal sellingPrice = originalPrice.add(markupAmount);

            calculatedSellingPriceField.setValue(sellingPrice + " UAH");
            product.setSellingPrice(sellingPrice);
        }
    }

    @Subscribe("originalPriceField")
    public void onOriginalPriceFieldValueChange(com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent<?, ?> event) {
        calculateSellingPrice();
    }

    @Subscribe("markupPercentageField")
    public void onMarkupPercentageFieldValueChange(com.vaadin.flow.component.AbstractField.ComponentValueChangeEvent<?, ?> event) {
        calculateSellingPrice();
    }
}