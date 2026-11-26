package com.warehouse.arrivalindex.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.warehouse.arrivalindex.model.Interval;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response returned after processing a batch of package registrations")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchResponse {

    @Schema(description = "Total number of package IDs processed in the batch", example = "5")
    private int totalProcessed;

    @Schema(description = "Number of newly added package IDs", example = "4")
    private int newlyAdded;

    @Schema(description = "Number of duplicate package IDs encountered", example = "1")
    private int duplicates;

    @Schema(description = "Current snapshot of all sorted, non-overlapping intervals after batch processing")
    private List<Interval> currentIntervals;

    public BatchResponse() {
    }

    public BatchResponse(int totalProcessed, int newlyAdded, int duplicates, List<Interval> currentIntervals) {
        this.totalProcessed = totalProcessed;
        this.newlyAdded = newlyAdded;
        this.duplicates = duplicates;
        this.currentIntervals = currentIntervals;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public int getNewlyAdded() {
        return newlyAdded;
    }

    public void setNewlyAdded(int newlyAdded) {
        this.newlyAdded = newlyAdded;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }

    public List<Interval> getCurrentIntervals() {
        return currentIntervals;
    }

    public void setCurrentIntervals(List<Interval> currentIntervals) {
        this.currentIntervals = currentIntervals;
    }
}
