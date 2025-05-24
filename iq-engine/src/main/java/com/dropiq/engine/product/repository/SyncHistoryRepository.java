package com.dropiq.engine.product.repository;

import com.dropiq.engine.product.entity.SyncHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SyncHistoryRepository extends JpaRepository<SyncHistory, Long> {

    Page<SyncHistory> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

    Page<SyncHistory> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT h FROM SyncHistory h WHERE h.userId = :userId AND h.startedAt >= :startDate")
    List<SyncHistory> findUserHistorySince(@Param("userId") Long userId,
                                           @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(h) FROM SyncHistory h WHERE h.userId = :userId AND h.syncType = :syncType " +
            "AND h.startedAt >= :startDate")
    Long countUserSyncsSince(@Param("userId") Long userId,
                             @Param("syncType") String syncType,
                             @Param("startDate") LocalDateTime startDate);

    @Modifying
    @Query("DELETE FROM SyncHistory h WHERE h.completedAt < :cutoffDate")
    int deleteOldHistory(@Param("cutoffDate") LocalDateTime cutoffDate);
}
