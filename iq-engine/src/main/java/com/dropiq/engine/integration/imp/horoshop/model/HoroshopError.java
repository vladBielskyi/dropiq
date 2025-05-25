package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

@Data
public class HoroshopError {
    private Integer code;
    private String message;
    private String field;
}
