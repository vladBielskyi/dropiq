package com.dropiq.admin.model;

import lombok.Getter;

@Getter
public enum DatasetStatus {
    DRAFT("Draft"),
    ACTIVE("Active"),
    PROCESSING("Processing"),
    OPTIMIZING("AI Optimizing"),
    READY_FOR_EXPORT("Ready for Export"),
    EXPORTED("Exported"),
    ERROR("Error"),
    ARCHIVED("Archived");

    private final String displayName;

    DatasetStatus(String displayName) {
        this.displayName = displayName;
    }

}
