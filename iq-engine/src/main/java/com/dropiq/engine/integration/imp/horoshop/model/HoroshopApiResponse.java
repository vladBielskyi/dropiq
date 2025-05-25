package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class HoroshopApiResponse<T> {
    private String status; // "OK", "WARNING", "ERROR"
    private T response;
    private List<HoroshopError> errors;
    private Map<String, Object> metadata;
}