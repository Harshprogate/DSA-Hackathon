package com.warehouse.arrivalindex.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.warehouse.arrivalindex.model.Interval;

import java.time.LocalDateTime;
import java.util.List;

public class IndexSnapshot {

    private long snapshotId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    private long totalUniquePackages;
    private int intervalCount;
    private String triggerOperation;
    private Long triggerPackageId;
    private List<Interval> intervals;

    public IndexSnapshot() {
    }

    public IndexSnapshot(long snapshotId, LocalDateTime timestamp, long totalUniquePackages, int intervalCount,
                         String triggerOperation, Long triggerPackageId, List<Interval> intervals) {
        this.snapshotId = snapshotId;
        this.timestamp = timestamp;
        this.totalUniquePackages = totalUniquePackages;
        this.intervalCount = intervalCount;
        this.triggerOperation = triggerOperation;
        this.triggerPackageId = triggerPackageId;
        this.intervals = intervals;
    }

    public long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(long snapshotId) { this.snapshotId = snapshotId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public long getTotalUniquePackages() { return totalUniquePackages; }
    public void setTotalUniquePackages(long totalUniquePackages) { this.totalUniquePackages = totalUniquePackages; }

    public int getIntervalCount() { return intervalCount; }
    public void setIntervalCount(int intervalCount) { this.intervalCount = intervalCount; }

    public String getTriggerOperation() { return triggerOperation; }
    public void setTriggerOperation(String triggerOperation) { this.triggerOperation = triggerOperation; }

    public Long getTriggerPackageId() { return triggerPackageId; }
    public void setTriggerPackageId(Long triggerPackageId) { this.triggerPackageId = triggerPackageId; }

    public List<Interval> getIntervals() { return intervals; }
    public void setIntervals(List<Interval> intervals) { this.intervals = intervals; }
}
