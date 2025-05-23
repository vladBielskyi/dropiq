package com.dropiq.admin.model;

import lombok.Getter;

@Getter
public enum DataSourceStatus {
    DRAFT("Draft"),
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    SYNCING("Syncing"),
    ERROR("Error"),
    ARCHIVED("Archived");

    private final String displayName;

    DataSourceStatus(String displayName) {
        this.displayName = displayName;
    }

}
