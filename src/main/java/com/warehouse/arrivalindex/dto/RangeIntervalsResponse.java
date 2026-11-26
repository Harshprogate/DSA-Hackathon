package com.warehouse.arrivalindex.dto;

import com.warehouse.arrivalindex.model.Interval;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response containing received package ID intervals clipped to the requested range")
public class RangeIntervalsResponse {

    @Schema(description = "Requested boundary start ID", example = "3")
    private long rangeStart;

    @Schema(description = "Requested boundary end ID", example = "10")
    private long rangeEnd;

    @Schema(description = "List of received intervals overlapping the requested range, clipped to range boundaries")
    private List<Interval> intervals;

    public RangeIntervalsResponse() {
    }

    public RangeIntervalsResponse(long rangeStart, long rangeEnd, List<Interval> intervals) {
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.intervals = intervals;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public void setRangeStart(long rangeStart) {
        this.rangeStart = rangeStart;
    }

    public long getRangeEnd() {
        return rangeEnd;
    }

    public void setRangeEnd(long rangeEnd) {
        this.rangeEnd = rangeEnd;
    }

    public List<Interval> getIntervals() {
        return intervals;
    }

    public void setIntervals(List<Interval> intervals) {
        this.intervals = intervals;
    }
}
