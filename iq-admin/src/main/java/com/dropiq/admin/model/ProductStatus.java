package com.dropiq.admin.model;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum ProductStatus implements EnumClass<String> {
    DRAFT,
    ACTIVE,
    INACTIVE,
    OUT_OF_STOCK,
    PROCESSING,
    ERROR;

    @Override
    public String getId() {
        return name();
    }
}
