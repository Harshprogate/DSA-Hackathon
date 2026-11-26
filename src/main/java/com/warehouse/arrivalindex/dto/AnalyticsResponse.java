package com.warehouse.arrivalindex.dto;

import com.warehouse.arrivalindex.model.Interval;
import java.util.List;

public class AnalyticsResponse {

    private long rangeStart;
    private long rangeEnd;
    private long expectedCount;
    private long receivedCount;
    private long missingCount;
    private double completionPercentage;
    private int receivedIntervalCount;
    private int missingIntervalCount;
    private Interval largestMissingRange;
    private Interval smallestMissingRange;
    private List<Interval> receivedIntervals;
    private List<Interval> missingIntervals;

    public AnalyticsResponse() {
    }

    public long getRangeStart() { return rangeStart; }
    public void setRangeStart(long rangeStart) { this.rangeStart = rangeStart; }

    public long getRangeEnd() { return rangeEnd; }
    public void setRangeEnd(long rangeEnd) { this.rangeEnd = rangeEnd; }

    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }

    public long getReceivedCount() { return receivedCount; }
    public void setReceivedCount(long receivedCount) { this.receivedCount = receivedCount; }

    public long getMissingCount() { return missingCount; }
    public void setMissingCount(long missingCount) { this.missingCount = missingCount; }

    public double getCompletionPercentage() { return completionPercentage; }
    public void setCompletionPercentage(double completionPercentage) { this.completionPercentage = completionPercentage; }

    public int getReceivedIntervalCount() { return receivedIntervalCount; }
    public void setReceivedIntervalCount(int receivedIntervalCount) { this.receivedIntervalCount = receivedIntervalCount; }

    public int getMissingIntervalCount() { return missingIntervalCount; }
    public void setMissingIntervalCount(int missingIntervalCount) { this.missingIntervalCount = missingIntervalCount; }

    public Interval getLargestMissingRange() { return largestMissingRange; }
    public void setLargestMissingRange(Interval largestMissingRange) { this.largestMissingRange = largestMissingRange; }

    public Interval getSmallestMissingRange() { return smallestMissingRange; }
    public void setSmallestMissingRange(Interval smallestMissingRange) { this.smallestMissingRange = smallestMissingRange; }

    public List<Interval> getReceivedIntervals() { return receivedIntervals; }
    public void setReceivedIntervals(List<Interval> receivedIntervals) { this.receivedIntervals = receivedIntervals; }

    public List<Interval> getMissingIntervals() { return missingIntervals; }
    public void setMissingIntervals(List<Interval> missingIntervals) { this.missingIntervals = missingIntervals; }
}
