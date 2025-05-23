package com.dropiq.admin.model;

import lombok.Getter;

@Getter
public enum ProductOptimizationStatus {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    SKIPPED("Skipped");

    private final String displayName;

    ProductOptimizationStatus(String displayName) {
        this.displayName = displayName;
    }

}
