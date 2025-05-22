package com.dropiq.engine.product.repository;

import com.dropiq.engine.integration.exp.model.SourceType;
import com.dropiq.engine.product.entity.DataSet;
import com.dropiq.engine.product.model.DataSetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataSetRepository extends JpaRepository<DataSet, Long> {

    List<DataSet> findByCreatedBy(String createdBy);

    List<DataSet> findByStatus(DataSetStatus status);

    List<DataSet> findByCreatedByAndStatus(String createdBy, DataSetStatus status);

    @Query("SELECT d FROM DataSet d WHERE d.name LIKE %:name%")
    List<DataSet> findByNameContaining(@Param("name") String name);

    @Query("SELECT d FROM DataSet d WHERE d.createdBy = :createdBy AND d.name LIKE %:name%")
    List<DataSet> findByCreatedByAndNameContaining(@Param("createdBy") String createdBy, @Param("name") String name);

    @Query("SELECT d FROM DataSet d WHERE d.createdAt BETWEEN :startDate AND :endDate")
    List<DataSet> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT d FROM DataSet d JOIN d.sourcePlatforms sp WHERE sp = :sourceType")
    List<DataSet> findBySourcePlatformsContaining(@Param("sourceType") SourceType sourceType);

    Optional<DataSet> findByIdAndCreatedBy(Long id, String createdBy);
}