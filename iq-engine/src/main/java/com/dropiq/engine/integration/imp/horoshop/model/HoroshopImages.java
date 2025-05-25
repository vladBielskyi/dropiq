package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HoroshopImages {
    private Boolean override = false; // true = replace all, false = add to existing
    private List<String> links = new ArrayList<>();
}
