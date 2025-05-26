package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HoroshopCharacteristic {
    private String name;
    private String value;
    private String unit;

    public HoroshopCharacteristic(String name, String value) {
        this.name = name;
        this.value = value;
    }
}
