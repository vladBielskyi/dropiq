package com.dropiq.admin.model;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum DataSetType implements EnumClass<String> {
    CLOTHING,
    ELECTRONICS,
    HOME_GARDEN,
    BEAUTY_HEALTH,
    SPORTS_OUTDOOR,
    TOYS_GAMES,
    AUTOMOTIVE,
    BOOKS_MEDIA,
    FOOD_BEVERAGES,
    MIXED,
    CUSTOM;

    @Override
    public String getId() {
        return name();
    }
}
