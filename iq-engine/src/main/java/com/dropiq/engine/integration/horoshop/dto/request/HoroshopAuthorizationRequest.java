package com.dropiq.engine.integration.horoshop.dto.request;

import lombok.Data;

@Data
public class HoroshopAuthorizationRequest {
    private String login;
    private String password;
}
