package com.dropiq.admin.component;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.service.DataSetService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatasetStatsComponent extends VerticalLayout {

    @Autowired
    private DataSetService datasetService;

    public void updateStats(DataSet dataset) {
        removeAll();

        if (dataset == null || dataset.getId() == null) {
            add(new Span("No dataset selected"));
            return;
        }

        try {
            DataSetService.DatasetStatistics stats = datasetService.getDatasetStatistics(
                    dataset.getId(), dataset.getCreatedBy());

            // Create stats cards
            HorizontalLayout statsRow = new HorizontalLayout();
            statsRow.setWidthFull();
            statsRow.setSpacing(true);

            statsRow.add(createStatCard("Total Products", String.valueOf(stats.getTotalProducts()), "📦"));
            statsRow.add(createStatCard("Active", String.valueOf(stats.getActiveProducts()), "✅"));
            statsRow.add(createStatCard("Optimized", String.valueOf(stats.getOptimizedProducts()), "🤖"));

            add(statsRow);

            // Add additional metrics if available
            if (!stats.getAdditionalMetrics().isEmpty()) {
                HorizontalLayout metricsRow = new HorizontalLayout();
                metricsRow.setWidthFull();
                metricsRow.setSpacing(true);

                stats.getAdditionalMetrics().forEach((key, value) -> {
                    metricsRow.add(createStatCard(key, value.toString(), "📊"));
                });

                add(metricsRow);
            }

        } catch (Exception e) {
            add(new Span("Error loading statistics: " + e.getMessage()));
        }
    }

    private Div createStatCard(String title, String value, String icon) {
        Div card = new Div();
        card.addClassName("stats-card");
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "8px")
                .set("padding", "16px")
                .set("text-align", "center")
                .set("background", "var(--lumo-base-color)")
                .set("min-width", "120px");

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "24px");

        H4 titleElement = new H4(title);
        titleElement.getStyle()
                .set("margin", "8px 0 4px 0")
                .set("font-size", "12px")
                .set("color", "var(--lumo-secondary-text-color)");

        Span valueElement = new Span(value);
        valueElement.getStyle()
                .set("font-size", "18px")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-primary-text-color)");

        card.add(iconSpan, titleElement, valueElement);
        return card;
    }
}
