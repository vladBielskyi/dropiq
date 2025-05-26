package com.dropiq.engine.product.repository;

import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.product.entity.Product;
import com.dropiq.engine.product.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find product by external ID and source type
     */
    Optional<Product> findByExternalIdAndSourceType(String externalId, SourceType sourceType);

    /**
     * Find products by source type
     */
    List<Product> findBySourceType(SourceType sourceType);

    /**
     * Find products by status
     */
    List<Product> findByStatus(ProductStatus status);

    /**
     * Find products by category
     */
    List<Product> findByExternalCategoryId(String categoryId);

    /**
     * Find products with price range
     */
    @Query("SELECT p FROM Product p WHERE p.sellingPrice BETWEEN :minPrice AND :maxPrice")
    List<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                   @Param("maxPrice") BigDecimal maxPrice);

    /**
     * Find available products
     */
    List<Product> findByAvailableTrue();

    List<Product> findByExternalGroupIdAndSourceTypeAndAiAnalysisDateIsNotNull(String groupId, SourceType sourceType);

    List<Product> findByExternalGroupIdAndSourceTypeAndAiAnalysisDateIsNull(String egroupId, SourceType sourceType);

    /**
     * Find products that need sync (haven't been synced recently)
     */
    @Query("SELECT p FROM Product p WHERE p.lastSync IS NULL OR p.lastSync < :cutoffTime")
    List<Product> findProductsNeedingSync(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find products by trend score range
     */
    @Query("SELECT p FROM Product p WHERE p.trendScore BETWEEN :minScore AND :maxScore ORDER BY p.trendScore DESC")
    List<Product> findByTrendScoreRange(@Param("minScore") BigDecimal minScore,
                                        @Param("maxScore") BigDecimal maxScore);

    /**
     * Find top trending products
     */
    @Query("SELECT p FROM Product p WHERE p.trendScore IS NOT NULL ORDER BY p.trendScore DESC")
    Page<Product> findTopTrendingProducts(Pageable pageable);

    /**
     * Count products by source type
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.sourceType = :sourceType")
    Long countBySourceType(@Param("sourceType") SourceType sourceType);

    /**
     * Find products with low stock
     */
    @Query("SELECT p FROM Product p WHERE p.stock <= :threshold AND p.available = true")
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold);

    /**
     * Find products with high profit margin
     */
    @Query("SELECT p FROM Product p WHERE p.markupPercentage >= :minMargin ORDER BY p.markupPercentage DESC")
    List<Product> findHighProfitProducts(@Param("markupPercentage") BigDecimal markupPercentage);

    /**
     * Find recent products
     */
    @Query("SELECT p FROM Product p WHERE p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<Product> findRecentProducts(@Param("since") LocalDateTime since);

    /**
     * Update product prices in bulk
     */
    @Query("UPDATE Product p SET p.markupPercentage = :markupPercentage WHERE p.id IN :productIds")
    int updateMarkupForProducts(@Param("productIds") List<Long> productIds,
                                @Param("markupPercentage") BigDecimal markupPercentage);

    /**
     * Update product status in bulk
     */
    @Query("UPDATE Product p SET p.status = :status WHERE p.id IN :productIds")
    int updateStatusForProducts(@Param("productIds") List<Long> productIds,
                                @Param("status") ProductStatus status);

}
