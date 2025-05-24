package com.dropiq.engine.product.model;

public enum SyncJobStatus {
    PENDING("Waiting to be processed"),
    RUNNING("Currently processing"),
    COMPLETED("Successfully completed"),
    FAILED("Failed with errors"),
    CANCELLED("Cancelled by user"),
    TIMEOUT("Timed out");

    private final String description;

    SyncJobStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
