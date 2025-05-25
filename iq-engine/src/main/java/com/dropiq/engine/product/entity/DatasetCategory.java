package com.dropiq.engine.product.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "dataset_category",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dataset_id", "slug"}))
@Data
@EqualsAndHashCode(exclude = {"children", "products", "dataset", "parent"})
public class DatasetCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name_uk", nullable = false)
    private String nameUk;

    @Column(name = "name_ru", nullable = false)
    private String nameRu;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "description_uk", length = 1000)
    private String descriptionUk;

    @Column(name = "description_ru", length = 1000)
    private String descriptionRu;

    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private DatasetCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<DatasetCategory> children = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DataSet dataset;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    private Set<Product> products = new HashSet<>();

    @Column(name = "product_count")
    private Integer productCount = 0;

    @Column(name = "level")
    private Integer level = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "ai_generated")
    private Boolean aiGenerated = false;

    @Column(name = "ai_confidence")
    private Double aiConfidence = 0.0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "category_keywords", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword")
    private Set<String> keywords = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        generateSlug();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        generateSlug();
    }

    private void generateSlug() {
        if (nameEn != null) {
            this.slug = nameEn.toLowerCase()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
        }
    }

    public void addProduct(Product product) {
        products.add(product);
        product.setCategory(this);
        updateProductCount();
    }

    public void removeProduct(Product product) {
        products.remove(product);
        product.setCategory(null);
        updateProductCount();
    }

    private void updateProductCount() {
        this.productCount = products.size();
    }

    public String getFullPath() {
        if (parent == null) {
            return nameEn;
        }
        return parent.getFullPath() + " > " + nameEn;
    }
}
