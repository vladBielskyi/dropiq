package com.dropiq.admin.service;

import com.dropiq.admin.entity.DataSet;
import com.dropiq.admin.entity.Product;
import com.dropiq.admin.model.DataSetStatus;
import com.dropiq.admin.model.ProductStatus;
import io.jmix.core.DataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class DataSetService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${iq.engine.base-url:http://localhost:8081}")
    private String engineBaseUrl;

    /**
     * Trigger synchronization via HTTP request to iq-engine
     */
    public void synchronizeDataSet(DataSet dataSet, String userId) {
        try {
            log.info("Triggering sync for dataset: {} by user: {}", dataSet.getName(), userId);

            String url = engineBaseUrl + "/api/datasets/" + dataSet.getId() + "/sync";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-ID", userId);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // Update local dataset status
                dataSet.setStatus(DataSetStatus.PROCESSING);
                dataSet.setUpdatedAt(LocalDateTime.now());
                dataManager.save(dataSet);
                log.info("Sync triggered successfully for dataset: {}", dataSet.getName());
            } else {
                log.error("Failed to trigger sync. Status: {}", response.getStatusCode());
                throw new RuntimeException("Sync request failed");
            }

        } catch (Exception e) {
            log.error("Error triggering sync for dataset {}: {}", dataSet.getName(), e.getMessage());
            dataSet.setStatus(DataSetStatus.ERROR);
            dataSet.setLastErrorMessage(e.getMessage());
            dataSet.setUpdatedAt(LocalDateTime.now());
            dataManager.save(dataSet);
            throw new RuntimeException("Failed to trigger synchronization: " + e.getMessage());
        }
    }

    /**
     * Bulk synchronization for multiple datasets
     */
    public void bulkSynchronize(List<DataSet> dataSets, String userId) {
        for (DataSet dataSet : dataSets) {
            try {
                synchronizeDataSet(dataSet, userId);
            } catch (Exception e) {
                log.error("Failed to sync dataset {}: {}", dataSet.getName(), e.getMessage());
                // Continue with next dataset
            }
        }
    }

    /**
     * Update dataset status
     */
    public DataSet updateStatus(DataSet dataSet, DataSetStatus status) {
        dataSet.setStatus(status);
        dataSet.setUpdatedAt(LocalDateTime.now());
        return dataManager.save(dataSet);
    }

    /**
     * Archive multiple products
     */
    public void archiveProducts(List<Product> products) {
        for (Product product : products) {
            product.setStatus(ProductStatus.INACTIVE);
            product.setUpdatedAt(LocalDateTime.now());
            dataManager.save(product);
        }
    }

    /**
     * Get dataset with products
     */
    public DataSet getDataSetWithProducts(Long id) {
        return dataManager.load(DataSet.class)
                .id(id)
                .fetchPlan("dataSet-with-products")
                .one();
    }
}