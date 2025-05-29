package com.dropiq.engine.integration.horoshop.dto.response;

import lombok.Data;

@Data
public class HoroshopAuthorizationResponse {
    private String status;
    private HoroshopTokenResponse response;
}
