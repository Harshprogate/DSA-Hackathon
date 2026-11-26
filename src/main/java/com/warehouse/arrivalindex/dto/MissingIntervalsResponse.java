package com.warehouse.arrivalindex.dto;

import com.warehouse.arrivalindex.model.Interval;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response containing missing package ID intervals within a requested bounded range")
public class MissingIntervalsResponse {

    @Schema(description = "Requested boundary start ID", example = "1")
    private long rangeStart;

    @Schema(description = "Requested boundary end ID", example = "7")
    private long rangeEnd;

    @Schema(description = "List of missing intervals inside the requested boundaries")
    private List<Interval> missingIntervals;

    public MissingIntervalsResponse() {
    }

    public MissingIntervalsResponse(long rangeStart, long rangeEnd, List<Interval> missingIntervals) {
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.missingIntervals = missingIntervals;
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

    public List<Interval> getMissingIntervals() {
        return missingIntervals;
    }

    public void setMissingIntervals(List<Interval> missingIntervals) {
        this.missingIntervals = missingIntervals;
    }
}
