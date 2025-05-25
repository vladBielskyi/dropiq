package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class HoroshopConfigValidation {
    private HoroshopConfig config;
    private Boolean valid;
    private Boolean connectionSuccess;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private LocalDateTime timestamp;
}
