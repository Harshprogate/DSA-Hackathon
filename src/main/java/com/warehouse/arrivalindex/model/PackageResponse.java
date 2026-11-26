package com.warehouse.arrivalindex.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.warehouse.arrivalindex.dto.OperationTraceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response returned after registering a package ID")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PackageResponse {

    @Schema(description = "The registered package ID", example = "4")
    private long packageId;

    @Schema(description = "Registration status (ADDED or DUPLICATE)", example = "ADDED")
    private String status;

    @Schema(description = "Informative status message", example = "Package successfully registered")
    private String message;

    @Schema(description = "Current snapshot of all sorted, non-overlapping intervals")
    private List<Interval> currentIntervals;

    @Schema(description = "Detailed TreeMap algorithm decision trace")
    private OperationTraceResponse trace;

    public PackageResponse() {
    }

    public PackageResponse(long packageId, String status, String message, List<Interval> currentIntervals) {
        this.packageId = packageId;
        this.status = status;
        this.message = message;
        this.currentIntervals = currentIntervals;
    }

    public PackageResponse(long packageId, String status, String message, List<Interval> currentIntervals, OperationTraceResponse trace) {
        this(packageId, status, message, currentIntervals);
        this.trace = trace;
    }

    public long getPackageId() {
        return packageId;
    }

    public void setPackageId(long packageId) {
        this.packageId = packageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Interval> getCurrentIntervals() {
        return currentIntervals;
    }

    public void setCurrentIntervals(List<Interval> currentIntervals) {
        this.currentIntervals = currentIntervals;
    }

    public OperationTraceResponse getTrace() {
        return trace;
    }

    public void setTrace(OperationTraceResponse trace) {
        this.trace = trace;
    }
}
