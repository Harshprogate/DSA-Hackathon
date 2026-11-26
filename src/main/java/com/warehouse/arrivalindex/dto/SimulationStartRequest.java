package com.warehouse.arrivalindex.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SimulationStartRequest {

    @NotNull(message = "sequence cannot be null")
    @NotEmpty(message = "sequence cannot be empty")
    private List<Long> sequence;

    private String modeName;

    public SimulationStartRequest() {
    }

    public SimulationStartRequest(List<Long> sequence, String modeName) {
        this.sequence = sequence;
        this.modeName = modeName;
    }

    public List<Long> getSequence() { return sequence; }
    public void setSequence(List<Long> sequence) { this.sequence = sequence; }

    public String getModeName() { return modeName; }
    public void setModeName(String modeName) { this.modeName = modeName; }
}
