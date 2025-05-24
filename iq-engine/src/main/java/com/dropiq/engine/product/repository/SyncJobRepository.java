package com.dropiq.engine.product.repository;

import com.dropiq.engine.product.entity.SyncJob;
import com.dropiq.engine.product.model.SyncJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    @Query("SELECT j FROM SyncJob j WHERE j.entityType = :entityType AND j.entityId = :entityId " +
            "AND j.status = 'PENDING' ORDER BY j.scheduledAt DESC")
    Optional<SyncJob> findPendingJobForEntity(@Param("entityType") String entityType,
                                              @Param("entityId") Long entityId);

    @Query("SELECT j FROM SyncJob j WHERE j.status = :status AND j.scheduledAt <= :now " +
            "ORDER BY j.priority DESC, j.scheduledAt ASC")
    List<SyncJob> findJobsToProcess(@Param("status") SyncJobStatus status,
                                    @Param("now") LocalDateTime now,
                                    Pageable pageable);

    Page<SyncJob> findByUserId(Long userId, Pageable pageable);

    Page<SyncJob> findByUserIdAndStatus(Long userId, SyncJobStatus status, Pageable pageable);

    @Query("SELECT j FROM SyncJob j WHERE j.status = 'RUNNING' AND j.startedAt < :cutoffTime")
    List<SyncJob> findStaleRunningJobs(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Modifying
    @Query("DELETE FROM SyncJob j WHERE j.status IN ('COMPLETED', 'CANCELLED') AND j.completedAt < :cutoffDate")
    int deleteOldCompletedJobs(@Param("cutoffDate") LocalDateTime cutoffDate);
}
