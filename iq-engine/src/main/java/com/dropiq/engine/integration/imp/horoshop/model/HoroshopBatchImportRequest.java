package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HoroshopBatchImportRequest {
    private String token;
    private List<HoroshopProduct> products = new ArrayList<>();
    private HoroshopImportSettings settings = new HoroshopImportSettings();
}
