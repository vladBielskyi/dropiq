package com.dropiq.admin.model;

import lombok.Getter;

@Getter
public enum DataSourceType {
    MYDROP("MyDrop Platform"),
    EASYDROP("EasyDrop Platform"),
    CSV_FILE("CSV File Upload"),
    EXCEL_FILE("Excel File Upload"),
    XML_FILE("XML File Upload"),
    CUSTOM_API("Custom API Endpoint"),
    MANUAL_ENTRY("Manual Entry");

    private final String displayName;

    DataSourceType(String displayName) {
        this.displayName = displayName;
    }

}
