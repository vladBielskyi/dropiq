package com.dropiq.admin.view.dashboard;

import com.dropiq.admin.model.DataSetStatus;
import com.dropiq.admin.model.ProductStatus;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "dashboard", layout = MainView.class)
@ViewController("DashboardView")
@ViewDescriptor(path = "dashboard-view.xml")
public class DashboardView extends StandardView {

    @ViewComponent
    private VerticalLayout contentLayout;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Subscribe
    public void onReady(ReadyEvent event) {
        loadDashboardData();
    }

    private void loadDashboardData() {
        String currentUser = currentAuthentication.getUser().getUsername();

        Long totalDatasets = dataManager.loadValue(
                        "select count(d) from DataSet d where d.createdBy = :user", Long.class)
                .parameter("user", currentUser)
                .one();

        Long activeDatasets = dataManager.loadValue(
                        "select count(d) from DataSet d where d.createdBy = :user and d.status = :status", Long.class)
                .parameter("user", currentUser)
                .parameter("status", DataSetStatus.ACTIVE)
                .one();

        Long totalProducts = dataManager.loadValue(
                        "select count(p) from Product p join p.datasets d where d.createdBy = :user", Long.class)
                .parameter("user", currentUser)
                .one();

        Long activeProducts = dataManager.loadValue(
                        "select count(p) from Product p join p.datasets d where d.createdBy = :user and p.status = :status", Long.class)
                .parameter("user", currentUser)
                .parameter("status", ProductStatus.ACTIVE)
                .one();

        // Create statistics cards
        HorizontalLayout statsLayout = new HorizontalLayout();
        statsLayout.setWidthFull();
        statsLayout.setSpacing(true);

        statsLayout.add(
                createStatCard("📊", "Total Datasets", totalDatasets.toString()),
                createStatCard("✅", "Active Datasets", activeDatasets.toString()),
                createStatCard("📦", "Total Products", totalProducts.toString()),
                createStatCard("🟢", "Active Products", activeProducts.toString())
        );

        contentLayout.add(new H2("Dashboard"), statsLayout);
    }

    private VerticalLayout createStatCard(String icon, String title, String value) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("stats-card");
        card.setSpacing(false);
        card.setPadding(true);
        card.setAlignItems(VerticalLayout.Alignment.CENTER);

        Span iconSpan = new Span(icon);
        iconSpan.addClassName("stats-card-icon");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("stats-card-title");

        Span valueSpan = new Span(value);
        valueSpan.addClassName("stats-card-value");

        card.add(iconSpan, valueSpan, titleSpan);
        return card;
    }
}
