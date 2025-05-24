package com.dropiq.engine.user.repository;

import com.dropiq.engine.user.entity.UserQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuota, Long> {

    @Query("SELECT q FROM UserQuota q WHERE q.user.id = :userId AND q.quotaType = :quotaType")
    Optional<UserQuota> findByUserIdAndQuotaType(@Param("userId") Long userId, @Param("quotaType") String quotaType);

    @Query("SELECT q FROM UserQuota q WHERE q.user.id = :userId")
    List<UserQuota> findByUserId(@Param("userId") Long userId);

    @Query("SELECT q FROM UserQuota q WHERE q.periodEnd < CURRENT_TIMESTAMP")
    List<UserQuota> findExpiredQuotas();
}