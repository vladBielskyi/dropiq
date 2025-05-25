package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

import java.util.List;

@Data
public class HoroshopBatchResponseData {
    private List<HoroshopProductLog> log;
    private Integer totalProcessed;
    private Integer totalSuccess;
    private Integer totalErrors;
}
