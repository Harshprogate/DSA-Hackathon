package com.warehouse.arrivalindex.controller;

import com.warehouse.arrivalindex.dto.*;
import com.warehouse.arrivalindex.service.BenchmarkService;
import com.warehouse.arrivalindex.service.ExportService;
import com.warehouse.arrivalindex.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Algorithm Lab, Simulation, Benchmark & Export", description = "Endpoints for isolated arrival replay, performance benchmarking, and reporting exports")
public class LabController {

    private final SimulationService simulationService;
    private final BenchmarkService benchmarkService;
    private final ExportService exportService;

    public LabController(SimulationService simulationService,
                         BenchmarkService benchmarkService,
                         ExportService exportService) {
        this.simulationService = simulationService;
        this.benchmarkService = benchmarkService;
        this.exportService = exportService;
    }

    @PostMapping(value = "/simulation/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start an isolated arrival replay simulation session")
    public ResponseEntity<SimulationStateResponse> startSimulation(@Valid @RequestBody SimulationStartRequest request) {
        return ResponseEntity.ok(simulationService.startSimulation(request.getSequence(), request.getModeName()));
    }

    @PostMapping(value = "/simulation/step", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Advance, rewind, or reset an isolated simulation session")
    public ResponseEntity<SimulationStateResponse> stepSimulation(@Valid @RequestBody SimulationStepRequest request) {
        return ResponseEntity.ok(simulationService.step(request.getSessionId(), request.getDirection()));
    }

    @GetMapping(value = "/simulation/state", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Retrieve current state of an isolated simulation session")
    public ResponseEntity<SimulationStateResponse> getSimulationState(@RequestParam("sessionId") String sessionId) {
        return ResponseEntity.ok(simulationService.getState(sessionId));
    }

    @PostMapping(value = "/benchmark/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Run Sorting Baseline vs Incremental TreeMap benchmark")
    public ResponseEntity<BenchmarkResponse> runBenchmark(@Valid @RequestBody BenchmarkRequest request) {
        return ResponseEntity.ok(benchmarkService.runBenchmark(request));
    }

    @GetMapping(value = "/packages/export")
    @Operation(summary = "Export intervals, missing ranges, activity logs, or completeness report")
    public ResponseEntity<?> exportReport(
            @RequestParam("type") String type,
            @RequestParam(value = "start", defaultValue = "0") long start,
            @RequestParam(value = "end", defaultValue = "100") long end) {

        switch (type.toLowerCase()) {
            case "intervals": {
                byte[] bytes = exportService.exportIntervalsCsv().getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"intervals.csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(bytes);
            }
            case "missing": {
                byte[] bytes = exportService.exportMissingCsv(start, end).getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"missing-intervals.csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(bytes);
            }
            case "activity": {
                byte[] bytes = exportService.exportActivityCsv().getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"arrival-activity.csv\"")
                        .contentType(MediaType.parseMediaType("text/csv"))
                        .body(bytes);
            }
            case "json": {
                Map<String, Object> json = exportService.exportCompleteJson();
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"arrival-index.json\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json);
            }
            case "pdf": {
                byte[] pdf = exportService.exportCompletenessPdf(start, end);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"warehouse-completeness-report.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdf);
            }
            default:
                throw new IllegalArgumentException("Unsupported export type: " + type + ". Use intervals, missing, activity, json, or pdf.");
        }
    }
}
