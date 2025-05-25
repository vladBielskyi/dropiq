package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class HoroshopSyncSettings {
    private boolean exportToHoroshop = true;
    private boolean importFromHoroshop = true;
    private LocalDateTime lastSyncTime;
    private boolean syncPricesOnly = false;
    private boolean syncStockOnly = false;
    private List<String> categoriesToSync = new ArrayList<>();
}