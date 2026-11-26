package com.warehouse.arrivalindex.controller;

import com.warehouse.arrivalindex.dto.*;
import com.warehouse.arrivalindex.model.IntervalResponse;
import com.warehouse.arrivalindex.model.PackageArrival;
import com.warehouse.arrivalindex.model.PackageBatchRequest;
import com.warehouse.arrivalindex.model.PackageResponse;
import com.warehouse.arrivalindex.model.StatisticsResponse;
import com.warehouse.arrivalindex.service.PackageIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/packages")
@Tag(name = "Package Index Management", description = "Endpoints for registering packages and querying consecutive intervals")
public class PackageController {

    private final PackageIndexService packageIndexService;

    public PackageController(PackageIndexService packageIndexService) {
        this.packageIndexService = packageIndexService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a single package", description = "Registers an incoming package ID and dynamically updates consecutive intervals in O(log n) time.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Package newly registered and intervals updated",
                    content = @Content(schema = @Schema(implementation = PackageResponse.class))),
            @ApiResponse(responseCode = "200", description = "Package ID was a duplicate; intervals unchanged",
                    content = @Content(schema = @Schema(implementation = PackageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid package ID or malformed request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PackageResponse> registerPackage(@Valid @RequestBody PackageArrival arrival) {
        PackageResponse response = packageIndexService.addPackage(arrival.getPackageId());
        if ("ADDED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register multiple packages in batch", description = "Processes a sequence of package IDs incrementally in the provided order without pre-sorting.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Batch successfully processed",
                    content = @Content(schema = @Schema(implementation = BatchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid package IDs, empty batch, or malformed request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BatchResponse> registerBatch(@Valid @RequestBody PackageBatchRequest batchRequest) {
        BatchResponse response = packageIndexService.addBatch(batchRequest.getPackageIds());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/intervals", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all received intervals", description = "Returns all sorted, non-overlapping consecutive intervals and the total package count.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved all intervals",
                    content = @Content(schema = @Schema(implementation = IntervalResponse.class)))
    })
    public ResponseEntity<IntervalResponse> getAllIntervals() {
        return ResponseEntity.ok(packageIndexService.getAllIntervals());
    }

    @GetMapping(value = "/missing", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get missing intervals within a bounded range", description = "Returns only the missing package ID intervals within [start, end] in O(log k + r) time.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved missing intervals",
                    content = @Content(schema = @Schema(implementation = MissingIntervalsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid range boundaries (negative, exceeds max, or start > end)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MissingIntervalsResponse> getMissingIntervals(
            @Parameter(description = "Start of bounded range (inclusive)", example = "1", required = true)
            @RequestParam("start") long start,
            @Parameter(description = "End of bounded range (inclusive)", example = "7", required = true)
            @RequestParam("end") long end) {
        return ResponseEntity.ok(packageIndexService.getMissingIntervals(start, end));
    }

    @GetMapping(value = "/intervals/range", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get received intervals within a bounded range", description = "Returns received intervals overlapping [start, end], clipped to the query boundaries in O(log k + r) time.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved bounded intervals",
                    content = @Content(schema = @Schema(implementation = RangeIntervalsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid range boundaries (negative, exceeds max, or start > end)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RangeIntervalsResponse> getBoundedIntervals(
            @Parameter(description = "Start of bounded range (inclusive)", example = "1", required = true)
            @RequestParam("start") long start,
            @Parameter(description = "End of bounded range (inclusive)", example = "10", required = true)
            @RequestParam("end") long end) {
        return ResponseEntity.ok(packageIndexService.getBoundedIntervals(start, end));
    }

    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get system statistics", description = "Returns summary statistics including total packages received, total intervals, minimum ID, and maximum ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved statistics",
                    content = @Content(schema = @Schema(implementation = StatisticsResponse.class)))
    })
    public ResponseEntity<StatisticsResponse> getStatistics() {
        return ResponseEntity.ok(packageIndexService.getStatistics());
    }

    @DeleteMapping
    @Operation(summary = "Reset the arrival index", description = "Clears all stored intervals and resets statistics to zero.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Index cleared successfully")
    })
    public ResponseEntity<Void> resetIndex() {
        packageIndexService.reset();
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview a package arrival (What-If)", description = "Evaluates the TreeMap decision for an incoming package ID without mutating the index.")
    public ResponseEntity<OperationTraceResponse> previewPackage(@Valid @RequestBody PackageArrival arrival) {
        return ResponseEntity.ok(packageIndexService.previewPackage(arrival.getPackageId()));
    }

    @PostMapping(value = "/undo", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Undo last successful insertion", description = "Reverts the most recent insertion, including splitting bridge-merged intervals.")
    public ResponseEntity<UndoResponse> undoLastArrival() {
        return ResponseEntity.ok(packageIndexService.undoLastArrival());
    }

    @GetMapping(value = "/activity", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get arrival audit history", description = "Returns chronological audit entries with optional operation type filter.")
    public ResponseEntity<java.util.List<AuditEntry>> getActivityLog(
            @RequestParam(value = "operation", required = false, defaultValue = "ALL") String operation) {
        return ResponseEntity.ok(packageIndexService.getAuditLog(operation));
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get historical index snapshots", description = "Returns captured index snapshots for timeline inspection and comparison.")
    public ResponseEntity<java.util.List<IndexSnapshot>> getSnapshots() {
        return ResponseEntity.ok(packageIndexService.getSnapshots());
    }

    @GetMapping(value = "/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get warehouse completeness analytics", description = "Computes coverage, completion %, and largest/smallest missing ranges in [start, end].")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @RequestParam("start") long start,
            @RequestParam("end") long end) {
        return ResponseEntity.ok(packageIndexService.getAnalytics(start, end));
    }
}
