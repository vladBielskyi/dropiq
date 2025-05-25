package com.dropiq.engine.product.repository;

import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.entity.DatasetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetCategoryRepository extends JpaRepository<DatasetCategory, Long> {

    List<DatasetCategory> findByDataset(DataSet dataset);

    List<DatasetCategory> findByDatasetAndParentIsNullOrderByProductCountDesc(DataSet dataset);

    List<DatasetCategory> findByDatasetOrderByProductCountDesc(DataSet dataset);

    Optional<DatasetCategory> findByDatasetAndNameEnIgnoreCaseAndParentIsNull(DataSet dataset, String nameEn);

    Optional<DatasetCategory> findByParentAndNameEnIgnoreCase(DatasetCategory parent, String nameEn);

    @Query("SELECT COUNT(c) FROM DatasetCategory c WHERE c.dataset = :dataset")
    long countByDataset(@Param("dataset") DataSet dataset);

    @Query("SELECT c FROM DatasetCategory c WHERE c.dataset = :dataset AND c.parent = :parent AND " +
            "c.level < :maxDepth ORDER BY c.productCount DESC")
    List<DatasetCategory> findByDatasetAndParentAndLevelLessThan(@Param("dataset") DataSet dataset,
                                                                 @Param("parent") DatasetCategory parent,
                                                                 @Param("maxDepth") int maxDepth);

    @Query("SELECT c FROM DatasetCategory c WHERE c.dataset = :dataset AND " +
            "UPPER(c.nameEn) LIKE UPPER(CONCAT('%', :searchTerm, '%')) AND " +
            "(:parent IS NULL OR c.parent = :parent)")
    List<DatasetCategory> findSimilarCategories(@Param("dataset") DataSet dataset,
                                                @Param("searchTerm") String searchTerm,
                                                @Param("parent") DatasetCategory parent,
                                                @Param("threshold") double threshold);

    @Query("SELECT c FROM DatasetCategory c WHERE c.dataset = :dataset AND c.aiGenerated = true")
    List<DatasetCategory> findAIGeneratedCategories(@Param("dataset") DataSet dataset);

    @Query("SELECT c FROM DatasetCategory c WHERE c.dataset = :dataset AND c.productCount = 0")
    List<DatasetCategory> findEmptyCategories(@Param("dataset") DataSet dataset);
}
