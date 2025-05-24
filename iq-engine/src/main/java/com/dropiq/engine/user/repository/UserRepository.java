package com.dropiq.engine.user.repository;

import com.dropiq.engine.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsernameOrEmail(String username, String email);

    @Modifying
    @Query("UPDATE User u SET u.lastActivity = :lastActivity WHERE u.id = :userId")
    void updateLastActivity(@Param("userId") Long userId, @Param("lastActivity") LocalDateTime lastActivity);

    @Query("SELECT COUNT(d) FROM DataSet d WHERE d.createdBy = :userId")
    Integer countUserDatasets(@Param("userId") String userId);

    @Query("SELECT COUNT(d) FROM DataSet d WHERE d.createdBy = :userId AND d.status = 'ACTIVE'")
    Integer countActiveUserDatasets(@Param("userId") String userId);

    @Query("SELECT COUNT(p) FROM Product p JOIN p.datasets d WHERE d.createdBy = :userId")
    Integer countUserProducts(@Param("userId") String userId);
}
