package com.dropiq.admin.model;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum DataSetStatus implements EnumClass<String> {
    DRAFT,
    ACTIVE,
    INACTIVE,
    PROCESSING,
    ERROR,
    ARCHIVED;

    @Override
    public String getId() {
        return name();
    }
}