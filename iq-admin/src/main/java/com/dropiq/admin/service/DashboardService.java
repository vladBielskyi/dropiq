package com.dropiq.admin.service;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.DataSource;
import com.dropiq.admin.model.DataSourceStatus;
import com.dropiq.admin.model.DatasetStatus;
import io.jmix.core.DataManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardService {

    @Autowired
    private DataManager dataManager;

    /**
     * Get overall dashboard statistics
     */
    public DashboardStats getDashboardStats() {
        DashboardStats stats = new DashboardStats();

        // Data sources statistics
        List<DataSource> dataSources = dataManager.load(DataSource.class).all().list();
        stats.setTotalDataSources(dataSources.size());
        stats.setActiveDataSources((int) dataSources.stream()
                .filter(ds -> ds.getStatus() == DataSourceStatus.ACTIVE)
                .count());

        // Datasets statistics
        List<DataSet> datasets = dataManager.load(DataSet.class).all().list();
        stats.setTotalDatasets(datasets.size());
        stats.setActiveDatasets((int) datasets.stream()
                .filter(ds -> ds.getStatus() == DatasetStatus.ACTIVE)
                .count());
        stats.setProcessingDatasets((int) datasets.stream()
                .filter(ds -> ds.getStatus() == DatasetStatus.PROCESSING ||
                        ds.getStatus() == DatasetStatus.OPTIMIZING)
                .count());

        // Products statistics
        int totalProducts = datasets.stream()
                .mapToInt(DataSet::getTotalProducts)
                .sum();
        int activeProducts = datasets.stream()
                .mapToInt(DataSet::getActiveProducts)
                .sum();
        int optimizedProducts = datasets.stream()
                .mapToInt(DataSet::getOptimizedProducts)
                .sum();

        stats.setTotalProducts(totalProducts);
        stats.setActiveProducts(activeProducts);
        stats.setOptimizedProducts(optimizedProducts);
        stats.setOptimizationRate(totalProducts > 0 ?
                (optimizedProducts * 100.0 / totalProducts) : 0.0);

        // Recent activity
        stats.setRecentDatasets(getRecentDatasets(5));
        stats.setRecentSyncs(getRecentSyncs(10));

        // Platform distribution
        stats.setPlatformDistribution(getPlatformDistribution());

        return stats;
    }

    /**
     * Get performance metrics over time
     */
    public List<PerformanceMetric> getPerformanceMetrics(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        List<DataSet> datasets = dataManager.load(DataSet.class)
                .query("select d from Dataset d where d.createdAt >= :startDate order by d.createdAt")
                .parameter("startDate", startDate)
                .list();

        Map<LocalDate, PerformanceMetric> metricsMap = new LinkedHashMap<>();

        // Initialize metrics for each day
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            PerformanceMetric metric = new PerformanceMetric();
            metric.setDate(date);
            metric.setNewDatasets(0);
            metric.setNewProducts(0);
            metric.setOptimizedProducts(0);
            metric.setSyncOperations(0);
            metricsMap.put(date, metric);
        }

        // Fill with actual data
        datasets.forEach(dataset -> {
            LocalDate date = dataset.getCreatedAt().toLocalDate();
            if (metricsMap.containsKey(date)) {
                PerformanceMetric metric = metricsMap.get(date);
                metric.setNewDatasets(metric.getNewDatasets() + 1);
                metric.setNewProducts(metric.getNewProducts() + dataset.getTotalProducts());
                metric.setOptimizedProducts(metric.getOptimizedProducts() + dataset.getOptimizedProducts());

                if (dataset.getLastSync() != null &&
                        dataset.getLastSync().toLocalDate().equals(date)) {
                    metric.setSyncOperations(metric.getSyncOperations() + 1);
                }
            }
        });

        return new ArrayList<>(metricsMap.values());
    }

    /**
     * Get top performing datasets
     */
    public List<DatasetPerformance> getTopPerformingDatasets(int limit) {
        List<DataSet> datasets = dataManager.load(DataSet.class)
                .query("select d from Dataset d where d.status = :status order by d.optimizedProducts desc, d.activeProducts desc")
                .parameter("status", DatasetStatus.ACTIVE)
                .maxResults(limit)
                .list();

        return datasets.stream()
                .map(dataset -> {
                    DatasetPerformance performance = new DatasetPerformance();
                    performance.setDatasetName(dataset.getName());
                    performance.setTotalProducts(dataset.getTotalProducts());
                    performance.setActiveProducts(dataset.getActiveProducts());
                    performance.setOptimizedProducts(dataset.getOptimizedProducts());
                    performance.setOptimizationRate(dataset.getTotalProducts() > 0 ?
                            (dataset.getOptimizedProducts() * 100.0 / dataset.getTotalProducts()) : 0.0);
                    performance.setLastSync(dataset.getLastSync());
                    return performance;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get recent datasets
     */
    private List<RecentActivity> getRecentDatasets(int limit) {
        List<DataSet> datasets = dataManager.load(DataSet.class)
                .query("select d from Dataset d order by d.createdAt desc")
                .maxResults(limit)
                .list();

        return datasets.stream()
                .map(dataset -> {
                    RecentActivity activity = new RecentActivity();
                    activity.setType("Dataset Created");
                    activity.setDescription("Dataset '" + dataset.getName() + "' was created");
                    activity.setTimestamp(dataset.getCreatedAt());
                    activity.setStatus(dataset.getStatus().getDisplayName());
                    return activity;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get recent sync operations
     */
    private List<RecentActivity> getRecentSyncs(int limit) {
        List<DataSet> datasets = dataManager.load(DataSet.class)
                .query("select d from Dataset d where d.lastSync is not null order by d.lastSync desc")
                .maxResults(limit)
                .list();

        return datasets.stream()
                .map(dataset -> {
                    RecentActivity activity = new RecentActivity();
                    activity.setType("Sync Completed");
                    activity.setDescription("Dataset '" + dataset.getName() + "' was synchronized");
                    activity.setTimestamp(dataset.getLastSync());
                    activity.setStatus(dataset.getErrorCount() > 0 ? "Warning" : "Success");
                    return activity;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get platform distribution
     */
    private Map<String, Integer> getPlatformDistribution() {
        List<DataSource> dataSources = dataManager.load(DataSource.class).all().list();

        return dataSources.stream()
                .collect(Collectors.groupingBy(
                        ds -> ds.getSourceType().getDisplayName(),
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }

    /**
     * Get AI optimization insights
     */
    public AIOptimizationInsights getAIOptimizationInsights() {
        AIOptimizationInsights insights = new AIOptimizationInsights();

        List<DataSet> datasets = dataManager.load(DataSet.class)
                .query("select d from Dataset d where d.aiOptimizationEnabled = true")
                .list();

        insights.setTotalAIEnabledDatasets(datasets.size());

        int totalOptimized = datasets.stream()
                .mapToInt(DataSet::getOptimizedProducts)
                .sum();

        int totalProducts = datasets.stream()
                .mapToInt(DataSet::getTotalProducts)
                .sum();

        insights.setTotalOptimizedProducts(totalOptimized);
        insights.setOverallOptimizationRate(totalProducts > 0 ?
                (totalOptimized * 100.0 / totalProducts) : 0.0);

        // Get optimization breakdown by feature
        long seoOptimizedCount = datasets.stream()
                .filter(DataSet::getSeoOptimizationEnabled)
                .count();

        long trendAnalysisCount = datasets.stream()
                .filter(DataSet::getTrendAnalysisEnabled)
                .count();

        long imageAnalysisCount = datasets.stream()
                .filter(DataSet::getImageAnalysisEnabled)
                .count();

        Map<String, Integer> optimizationFeatures = new HashMap<>();
        optimizationFeatures.put("SEO Optimization", (int) seoOptimizedCount);
        optimizationFeatures.put("Trend Analysis", (int) trendAnalysisCount);
        optimizationFeatures.put("Image Analysis", (int) imageAnalysisCount);

        insights.setOptimizationFeatures(optimizationFeatures);

        return insights;
    }

    // Data classes for response objects
    @Data
    public static class DashboardStats {
        private int totalDataSources;
        private int activeDataSources;
        private int totalDatasets;
        private int activeDatasets;
        private int processingDatasets;
        private int totalProducts;
        private int activeProducts;
        private int optimizedProducts;
        private double optimizationRate;
        private List<RecentActivity> recentDatasets;
        private List<RecentActivity> recentSyncs;
        private Map<String, Integer> platformDistribution;
    }

    @Data
    public static class PerformanceMetric {
        private LocalDate date;
        private int newDatasets;
        private int newProducts;
        private int optimizedProducts;
        private int syncOperations;
    }

    @Data
    public static class DatasetPerformance {
        private String datasetName;
        private int totalProducts;
        private int activeProducts;
        private int optimizedProducts;
        private double optimizationRate;
        private LocalDateTime lastSync;
    }

    @Data
    public static class RecentActivity {
        private String type;
        private String description;
        private LocalDateTime timestamp;
        private String status;
    }

    @Data
    public static class AIOptimizationInsights {
        private int totalAIEnabledDatasets;
        private int totalOptimizedProducts;
        private double overallOptimizationRate;
        private Map<String, Integer> optimizationFeatures;
    }
}