package com.dropiq.engine.product.entity;

import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.product.model.DataSetStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;


import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "dataset")
@Data
@EqualsAndHashCode(exclude = {"products"})
public class DataSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_by")
    private String createdBy; // User who created this dataset

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private DataSetStatus status;

    // Many-to-many relationship with products
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "dataset_products",
            joinColumns = @JoinColumn(name = "dataset_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> products = new HashSet<>();

    // Track which data sources were used to create this dataset
    @ElementCollection
    @CollectionTable(name = "dataset_sources")
    @Enumerated(EnumType.STRING)
    private Set<SourceType> sourcePlatforms = new HashSet<>();

    // Metadata about the dataset
    @ElementCollection
    @CollectionTable(name = "dataset_metadata")
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata = new HashMap<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = DataSetStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public void addProduct(Product product) {
        products.add(product);
        sourcePlatforms.add(product.getSourceType());
    }

    public void removeProduct(Product product) {
        products.remove(product);
    }

    public void addProducts(List<Product> productList) {
        for (Product product : productList) {
            addProduct(product);
        }
    }

    public int getProductCount() {
        return products.size();
    }

    public boolean containsProduct(String externalId, SourceType sourceType) {
        return products.stream()
                .anyMatch(p -> p.getExternalId().equals(externalId) &&
                        p.getSourceType().equals(sourceType));
    }
}
