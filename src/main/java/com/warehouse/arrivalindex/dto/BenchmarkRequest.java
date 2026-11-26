package com.warehouse.arrivalindex.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class BenchmarkRequest {

    @Min(value = 100, message = "Benchmark size must be at least 100")
    @Max(value = 50_000, message = "Benchmark size cannot exceed 50,000")
    private int size = 5_000;

    private String pattern = "RANDOM";

    @Min(value = 1, message = "Runs must be at least 1")
    @Max(value = 5, message = "Runs cannot exceed 5")
    private int runs = 3;

    public BenchmarkRequest() {
    }

    public BenchmarkRequest(int size, String pattern, int runs) {
        this.size = size;
        this.pattern = pattern;
        this.runs = runs;
    }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public int getRuns() { return runs; }
    public void setRuns(int runs) { this.runs = runs; }
}
