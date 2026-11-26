package com.warehouse.arrivalindex.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalOperation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Structured step-by-step TreeMap algorithm trace for a package insertion or preview")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperationTraceResponse {

    private long packageId;
    private IntervalOperation operation;
    private Interval floorEntry;
    private Interval ceilingEntry;
    private boolean alreadyExists;
    private boolean connectsLeft;
    private boolean connectsRight;
    private List<Interval> removedIntervals;
    private Interval createdOrUpdatedInterval;
    private List<Interval> beforeIntervals;
    private List<Interval> afterIntervals;
    private int intervalsBefore;
    private int intervalsAfter;
    private List<String> steps;
    private String message;
    private long durationNanos;

    public OperationTraceResponse() {
    }

    public long getPackageId() { return packageId; }
    public void setPackageId(long packageId) { this.packageId = packageId; }

    public IntervalOperation getOperation() { return operation; }
    public void setOperation(IntervalOperation operation) { this.operation = operation; }

    public Interval getFloorEntry() { return floorEntry; }
    public void setFloorEntry(Interval floorEntry) { this.floorEntry = floorEntry; }

    public Interval getCeilingEntry() { return ceilingEntry; }
    public void setCeilingEntry(Interval ceilingEntry) { this.ceilingEntry = ceilingEntry; }

    public boolean isAlreadyExists() { return alreadyExists; }
    public void setAlreadyExists(boolean alreadyExists) { this.alreadyExists = alreadyExists; }

    public boolean isConnectsLeft() { return connectsLeft; }
    public void setConnectsLeft(boolean connectsLeft) { this.connectsLeft = connectsLeft; }

    public boolean isConnectsRight() { return connectsRight; }
    public void setConnectsRight(boolean connectsRight) { this.connectsRight = connectsRight; }

    public List<Interval> getRemovedIntervals() { return removedIntervals; }
    public void setRemovedIntervals(List<Interval> removedIntervals) { this.removedIntervals = removedIntervals; }

    public Interval getCreatedOrUpdatedInterval() { return createdOrUpdatedInterval; }
    public void setCreatedOrUpdatedInterval(Interval createdOrUpdatedInterval) { this.createdOrUpdatedInterval = createdOrUpdatedInterval; }

    public List<Interval> getBeforeIntervals() { return beforeIntervals; }
    public void setBeforeIntervals(List<Interval> beforeIntervals) { this.beforeIntervals = beforeIntervals; }

    public List<Interval> getAfterIntervals() { return afterIntervals; }
    public void setAfterIntervals(List<Interval> afterIntervals) { this.afterIntervals = afterIntervals; }

    public int getIntervalsBefore() { return intervalsBefore; }
    public void setIntervalsBefore(int intervalsBefore) { this.intervalsBefore = intervalsBefore; }

    public int getIntervalsAfter() { return intervalsAfter; }
    public void setIntervalsAfter(int intervalsAfter) { this.intervalsAfter = intervalsAfter; }

    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getDurationNanos() { return durationNanos; }
    public void setDurationNanos(long durationNanos) { this.durationNanos = durationNanos; }
}
