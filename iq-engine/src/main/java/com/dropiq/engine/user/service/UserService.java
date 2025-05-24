package com.dropiq.engine.user.service;

import com.dropiq.engine.user.entity.User;
import com.dropiq.engine.user.entity.UserQuota;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserQuotaRepository quotaRepository;

    /**
     * Create a new user with default quotas
     */
    public User createUser(String username, String email, String tenantId) {
        log.info("Creating new user: {} with tenant: {}", username, tenantId);

        // Check if user already exists
        if (userRepository.existsByUsernameOrEmail(username, email)) {
            throw new IllegalArgumentException("User with this username or email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setTenantId(tenantId != null ? tenantId : UUID.randomUUID().toString());
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        user = userRepository.save(user);

        // Create default quota for user
        createDefaultQuota(user);

        log.info("User created successfully with ID: {}", user.getId());
        return user;
    }

    /**
     * Get user by username with caching
     */
    @Cacheable(value = "users", key = "#username")
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Get user by ID with caching
     */
    @Cacheable(value = "users", key = "#userId")
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Update user last activity
     */
    @CacheEvict(value = "users", key = "#userId")
    public void updateUserActivity(Long userId) {
        userRepository.updateLastActivity(userId, LocalDateTime.now());
    }

    /**
     * Check if user has reached quota limits
     */
    public boolean checkQuotaLimit(Long userId, String quotaType) {
        Optional<UserQuota> quota = quotaRepository.findByUserIdAndQuotaType(userId, quotaType);

        if (quota.isEmpty()) {
            log.warn("No quota found for user {} and type {}", userId, quotaType);
            return false;
        }

        UserQuota userQuota = quota.get();
        return userQuota.getUsedAmount() >= userQuota.getMaxAmount();
    }

    /**
     * Update user quota usage
     */
    @Transactional
    public void updateQuotaUsage(Long userId, String quotaType, int amount) {
        Optional<UserQuota> quotaOpt = quotaRepository.findByUserIdAndQuotaType(userId, quotaType);

        if (quotaOpt.isEmpty()) {
            log.warn("No quota found for user {} and type {}", userId, quotaType);
            return;
        }

        UserQuota quota = quotaOpt.get();
        quota.setUsedAmount(quota.getUsedAmount() + amount);
        quota.setLastUpdated(LocalDateTime.now());

        // Reset if period has expired
        if (quota.isExpired()) {
            quota.resetUsage();
        }

        quotaRepository.save(quota);
    }

    /**
     * Create default quotas for a new user
     */
    private void createDefaultQuota(User user) {
        // Default quotas based on subscription type
        UserQuota datasetQuota = new UserQuota();
        datasetQuota.setUser(user);
        datasetQuota.setQuotaType("DATASETS");
        datasetQuota.setMaxAmount(10); // Free tier
        datasetQuota.setUsedAmount(0);
        datasetQuota.setPeriodType("MONTHLY");
        datasetQuota.setPeriodStart(LocalDateTime.now());
        datasetQuota.setPeriodEnd(LocalDateTime.now().plusMonths(1));

        UserQuota productQuota = new UserQuota();
        productQuota.setUser(user);
        productQuota.setQuotaType("PRODUCTS");
        productQuota.setMaxAmount(1000); // Free tier
        productQuota.setUsedAmount(0);
        productQuota.setPeriodType("MONTHLY");
        productQuota.setPeriodStart(LocalDateTime.now());
        productQuota.setPeriodEnd(LocalDateTime.now().plusMonths(1));

        UserQuota syncQuota = new UserQuota();
        syncQuota.setUser(user);
        syncQuota.setQuotaType("SYNCS");
        syncQuota.setMaxAmount(50); // Free tier
        syncQuota.setUsedAmount(0);
        syncQuota.setPeriodType("DAILY");
        syncQuota.setPeriodStart(LocalDateTime.now());
        syncQuota.setPeriodEnd(LocalDateTime.now().plusDays(1));

        quotaRepository.save(datasetQuota);
        quotaRepository.save(productQuota);
        quotaRepository.save(syncQuota);
    }

    /**
     * Get user statistics
     */
    public UserStatistics getUserStatistics(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserStatistics stats = new UserStatistics();
        stats.setUserId(userId);
        stats.setUsername(user.getUsername());
        stats.setTenantId(user.getTenantId());

        // Get quota usage
        quotaRepository.findByUserId(userId).forEach(quota -> {
            stats.getQuotaUsage().put(quota.getQuotaType(),
                    new QuotaUsage(quota.getUsedAmount(), quota.getMaxAmount()));
        });

        // Get dataset and product counts
        stats.setTotalDatasets(userRepository.countUserDatasets(userId));
        stats.setActiveDatasets(userRepository.countActiveUserDatasets(userId));
        stats.setTotalProducts(userRepository.countUserProducts(userId));

        return stats;
    }

    @lombok.Data
    public static class UserStatistics {
        private Long userId;
        private String username;
        private String tenantId;
        private Integer totalDatasets;
        private Integer activeDatasets;
        private Integer totalProducts;
        private Map<String, QuotaUsage> quotaUsage = new HashMap<>();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class QuotaUsage {
        private Integer used;
        private Integer max;
    }
}
