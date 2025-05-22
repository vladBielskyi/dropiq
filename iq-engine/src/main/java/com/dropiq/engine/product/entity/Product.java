package com.dropiq.engine.product.entity;

import com.dropiq.engine.integration.exp.model.SourceType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "product")
@Data
@EqualsAndHashCode(exclude = {"datasets"})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalId;
    private String groupId;
    private String name;

    @Column(length = 4000)
    private String description;

    private Double price;
    private Integer stock;
    private boolean available;
    private String categoryId;
    private String categoryName;

    @ElementCollection
    @CollectionTable(name = "product_images")
    private List<String> imageUrls = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_attributes")
    @MapKeyColumn(name = "attr_key")
    @Column(name = "attr_value")
    private Map<String, String> attributes = new HashMap<>();

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String sourceUrl;
    private LocalDateTime lastUpdated;

    @ElementCollection
    @CollectionTable(name = "product_platform_data")
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    private Map<String, String> platformSpecificData = new HashMap<>();

    @ManyToMany(mappedBy = "products")
    private Set<DataSet> datasets = new HashSet<>();

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
