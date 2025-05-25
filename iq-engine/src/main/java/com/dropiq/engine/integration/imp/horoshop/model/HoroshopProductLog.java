package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.util.List;

@Data
public class HoroshopProductLog {
    private String article;
    private List<HoroshopLogEntry> info;
}
