package com.warehouse.arrivalindex.dto;

import jakarta.validation.constraints.NotBlank;

public class SimulationStepRequest {

    @NotBlank(message = "sessionId is required")
    private String sessionId;

    private String direction = "NEXT";

    public SimulationStepRequest() {
    }

    public SimulationStepRequest(String sessionId, String direction) {
        this.sessionId = sessionId;
        this.direction = direction;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
}
