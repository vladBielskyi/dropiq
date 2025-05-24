package com.dropiq.engine.user.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_quota")
public class UserQuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "quota_type", nullable = false)
    private String quotaType; // DATASETS, PRODUCTS, SYNCS, AI_OPTIMIZATIONS

    @Column(name = "max_amount")
    private Integer maxAmount;

    @Column(name = "used_amount")
    private Integer usedAmount = 0;

    @Column(name = "period_type")
    private String periodType; // DAILY, MONTHLY, YEARLY

    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "period_end")
    private LocalDateTime periodEnd;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(periodEnd);
    }

    public void resetUsage() {
        usedAmount = 0;

        switch (periodType) {
            case "DAILY":
                periodStart = LocalDateTime.now();
                periodEnd = LocalDateTime.now().plusDays(1);
                break;
            case "MONTHLY":
                periodStart = LocalDateTime.now();
                periodEnd = LocalDateTime.now().plusMonths(1);
                break;
            case "YEARLY":
                periodStart = LocalDateTime.now();
                periodEnd = LocalDateTime.now().plusYears(1);
                break;
        }

        lastUpdated = LocalDateTime.now();
    }
}
