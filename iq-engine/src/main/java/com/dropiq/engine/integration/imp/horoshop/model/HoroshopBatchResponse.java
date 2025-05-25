package com.dropiq.engine.integration.imp.horoshop.model;

import lombok.Data;

@Data
public class HoroshopBatchResponse {
    private String status;
    private HoroshopBatchResponseData response;
}
