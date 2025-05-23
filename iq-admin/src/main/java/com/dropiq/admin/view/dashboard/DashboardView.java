package com.dropiq.admin.view.dashboard;

import com.dropiq.admin.service.DashboardService;
import com.dropiq.admin.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;


@Route(value = "dashboard", layout = MainView.class)
@ViewController(id = "DashboardView")
@ViewDescriptor(path = "dashboard-view.xml")
public class DashboardView extends StandardView {

    @Autowired
    private DashboardService dashboardService;

    @ViewComponent
    private VerticalLayout mainContent;

    @ViewComponent
    private HorizontalLayout statsLayout;

    @ViewComponent
    private VerticalLayout chartsLayout;

    @ViewComponent
    private VerticalLayout recentActivityLayout;

    @Subscribe
    public void onInit(final InitEvent event) {
        loadDashboardData();
    }

    private void loadDashboardData() {
        try {
            DashboardService.DashboardStats stats = dashboardService.getDashboardStats();
            createStatsCards(stats);
            createChartsSection(stats);
            createRecentActivitySection(stats);
        } catch (Exception e) {
            // Handle error
            Div errorDiv = new Div();
            errorDiv.setText("Error loading dashboard data: " + e.getMessage());
            errorDiv.addClassName("error-message");
            mainContent.add(errorDiv);
        }
    }

    private void createStatsCards(DashboardService.DashboardStats stats) {
        statsLayout.removeAll();

        // Data Sources Card
        Div dataSourceCard = createStatsCard("Data Sources",
                String.valueOf(stats.getTotalDataSources()),
                stats.getActiveDataSources() + " active",
                "vaadin-icon-database");
        statsLayout.add(dataSourceCard);

        // Datasets Card
        Div datasetsCard = createStatsCard("Datasets",
                String.valueOf(stats.getTotalDatasets()),
                stats.getActiveDatasets() + " active",
                "vaadin-icon-folder");
        statsLayout.add(datasetsCard);

        // Products Card
        Div productsCard = createStatsCard("Products",
                String.valueOf(stats.getTotalProducts()),
                stats.getActiveProducts() + " active",
                "vaadin-icon-package");
        statsLayout.add(productsCard);

        // Optimization Card
        Div optimizationCard = createStatsCard("AI Optimized",
                String.valueOf(stats.getOptimizedProducts()),
                String.format("%.1f%% rate", stats.getOptimizationRate()),
                "vaadin-icon-magic");
        statsLayout.add(optimizationCard);
    }

    private Div createStatsCard(String title, String mainValue, String subValue, String iconClass) {
        Div card = new Div();
        card.addClassName("stats-card");

        Div iconDiv = new Div();
        iconDiv.addClassName("stats-card-icon");
        iconDiv.addClassName(iconClass);

        Div contentDiv = new Div();
        contentDiv.addClassName("stats-card-content");

        H3 titleElement = new H3(title);
        titleElement.addClassName("stats-card-title");

        Span valueElement = new Span(mainValue);
        valueElement.addClassName("stats-card-value");

        Span subValueElement = new Span(subValue);
        subValueElement.addClassName("stats-card-sub-value");

        contentDiv.add(titleElement, valueElement, subValueElement);
        card.add(iconDiv, contentDiv);

        return card;
    }

    private void createChartsSection(DashboardService.DashboardStats stats) {
        chartsLayout.removeAll();

        // Platform Distribution Chart
        H3 chartTitle = new H3("Platform Distribution");
        chartsLayout.add(chartTitle);

        if (stats.getPlatformDistribution() != null && !stats.getPlatformDistribution().isEmpty()) {
            Div chartDiv = new Div();
            chartDiv.addClassName("chart-container");

            // Create simple bar chart representation
            stats.getPlatformDistribution().forEach((platform, count) -> {
                Div barContainer = new Div();
                barContainer.addClassName("chart-bar-container");

                Span label = new Span(platform);
                label.addClassName("chart-bar-label");

                Div bar = new Div();
                bar.addClassName("chart-bar");

                Span value = new Span(String.valueOf(count));
                value.addClassName("chart-bar-value");

                HorizontalLayout barLayout = new HorizontalLayout(label, bar, value);
                barLayout.setAlignItems(FlexComponent.Alignment.CENTER);
                barContainer.add(barLayout);

                chartDiv.add(barContainer);
            });

            chartsLayout.add(chartDiv);
        }
    }

    private void createRecentActivitySection(DashboardService.DashboardStats stats) {
        recentActivityLayout.removeAll();

        H3 activityTitle = new H3("Recent Activity");
        recentActivityLayout.add(activityTitle);

        // Recent Datasets
        if (stats.getRecentDatasets() != null && !stats.getRecentDatasets().isEmpty()) {
            H3 recentDatasetsTitle = new H3("Recent Datasets");
            recentDatasetsTitle.addClassName("activity-section-title");
            recentActivityLayout.add(recentDatasetsTitle);

            stats.getRecentDatasets().forEach(activity -> {
                Div activityItem = createActivityItem(activity);
                recentActivityLayout.add(activityItem);
            });
        }

        // Recent Syncs
        if (stats.getRecentSyncs() != null && !stats.getRecentSyncs().isEmpty()) {
            H3 recentSyncsTitle = new H3("Recent Syncs");
            recentSyncsTitle.addClassName("activity-section-title");
            recentActivityLayout.add(recentSyncsTitle);

            stats.getRecentSyncs().forEach(activity -> {
                Div activityItem = createActivityItem(activity);
                recentActivityLayout.add(activityItem);
            });
        }
    }

    private Div createActivityItem(DashboardService.RecentActivity activity) {
        Div item = new Div();
        item.addClassName("activity-item");

        Div statusIndicator = new Div();
        statusIndicator.addClassName("activity-status");
        statusIndicator.addClassName("status-" + activity.getStatus().toLowerCase());

        Div content = new Div();
        content.addClassName("activity-content");

        Span type = new Span(activity.getType());
        type.addClassName("activity-type");

        Span description = new Span(activity.getDescription());
        description.addClassName("activity-description");

        Span timestamp = new Span(formatTimestamp(activity.getTimestamp()));
        timestamp.addClassName("activity-timestamp");

        content.add(type, description, timestamp);
        item.add(statusIndicator, content);

        return item;
    }

    private String formatTimestamp(java.time.LocalDateTime timestamp) {
        if (timestamp == null) return "";

        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm");
        return timestamp.format(formatter);
    }

    @Subscribe
    public void onRefreshButtonClick(final ClickEvent<Button> event) {
        loadDashboardData();
    }
}