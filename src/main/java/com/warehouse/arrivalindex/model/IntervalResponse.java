package com.warehouse.arrivalindex.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response containing all received intervals and total counts")
public class IntervalResponse {

    @Schema(description = "Total number of unique package IDs received", example = "5")
    private long totalReceived;

    @Schema(description = "Total number of disjoint consecutive intervals", example = "2")
    private int intervalCount;

    @Schema(description = "Sorted list of non-overlapping consecutive intervals")
    private List<Interval> intervals;

    public IntervalResponse() {
    }

    public IntervalResponse(long totalReceived, int intervalCount, List<Interval> intervals) {
        this.totalReceived = totalReceived;
        this.intervalCount = intervalCount;
        this.intervals = intervals;
    }

    public long getTotalReceived() {
        return totalReceived;
    }

    public void setTotalReceived(long totalReceived) {
        this.totalReceived = totalReceived;
    }

    public int getIntervalCount() {
        return intervalCount;
    }

    public void setIntervalCount(int intervalCount) {
        this.intervalCount = intervalCount;
    }

    public List<Interval> getIntervals() {
        return intervals;
    }

    public void setIntervals(List<Interval> intervals) {
        this.intervals = intervals;
    }
}
