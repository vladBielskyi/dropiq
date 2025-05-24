package com.dropiq.admin.model;

import io.jmix.core.metamodel.datatype.EnumClass;
import lombok.Getter;

@Getter
public enum SyncJobStatus implements EnumClass<String> {
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

    @Override
    public String getId() {
        return name();
    }
}
