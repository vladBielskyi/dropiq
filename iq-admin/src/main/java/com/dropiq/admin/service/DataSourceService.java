package com.dropiq.admin.service;

import com.dropiq.admin.entity.DataSource;
import com.dropiq.admin.model.DataSourceStatus;
import com.dropiq.admin.model.DataSourceType;
import io.jmix.core.DataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class DataSourceService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private RestTemplate restTemplate;

    public List<DataSource> getAllDataSources() {
        return dataManager.load(DataSource.class)
                .query("select ds from DataSource ds order by ds.createdAt desc")
                .list();
    }

    public List<DataSource> getActiveDataSources() {
        return dataManager.load(DataSource.class)
                .query("select ds from DataSource ds where ds.status = :status order by ds.createdAt desc")
                .parameter("status", DataSourceStatus.ACTIVE)
                .list();
    }

    public boolean testConnection(DataSource dataSource) {
        try {
            if (dataSource.getSourceType() == DataSourceType.MYDROP ||
                    dataSource.getSourceType() == DataSourceType.EASYDROP) {

                // Test HTTP connection
                String response = restTemplate.getForObject(dataSource.getUrl(), String.class);
                return response != null && !response.trim().isEmpty();
            }

            // For file-based sources, check if file exists or URL is accessible
            if (dataSource.getSourceType() == DataSourceType.CSV_FILE ||
                    dataSource.getSourceType() == DataSourceType.EXCEL_FILE ||
                    dataSource.getSourceType() == DataSourceType.XML_FILE) {

                // Basic URL validation for now
                return dataSource.getUrl() != null && !dataSource.getUrl().trim().isEmpty();
            }

            return true; // Manual entry always valid

        } catch (Exception e) {
            log.error("Connection test failed for data source {}: {}", dataSource.getName(), e.getMessage());
            return false;
        }
    }

    public void updateSyncStatistics(DataSource dataSource, int totalProducts, int activeProducts) {
        dataSource.setTotalProducts(totalProducts);
        dataSource.setActiveProducts(activeProducts);
        dataSource.setLastSync(LocalDateTime.now());
        dataSource.setSyncCount(dataSource.getSyncCount() + 1);

        dataManager.save(dataSource);
    }

    public void markSyncError(DataSource dataSource, String errorMessage) {
        dataSource.setStatus(DataSourceStatus.ERROR);
        dataSource.setLastErrorMessage(errorMessage);
        dataSource.setErrorCount(dataSource.getErrorCount() + 1);

        dataManager.save(dataSource);
    }
}
