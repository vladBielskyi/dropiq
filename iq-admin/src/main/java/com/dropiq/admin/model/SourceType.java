package com.dropiq.admin.model;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum SourceType implements EnumClass<String> {

    MYDROP("MYDROP"),
    EASYDROP("EASYDROP"),
    CSV_FILE("CSV_FILE"),
    XML_FILE("XML_FILE"),
    CUSTOM_API("CUSTOM_API"),
    MANUAL_ENTRY("MANUAL_ENTRY");

    private final String id;

    SourceType(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return name();
    }
}
