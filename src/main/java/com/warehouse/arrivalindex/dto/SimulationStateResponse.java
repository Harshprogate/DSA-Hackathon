package com.warehouse.arrivalindex.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.warehouse.arrivalindex.model.Interval;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimulationStateResponse {

    private String sessionId;
    private String modeName;
    private List<Long> sequence;
    private int currentStepIndex;
    private int totalSteps;
    private Long currentPackageId;
    private OperationTraceResponse lastTrace;
    private List<Interval> currentIntervals;
    private long totalUniquePackages;
    private int intervalCount;
    private long duplicateCount;
    private boolean completed;

    public SimulationStateResponse() {
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getModeName() { return modeName; }
    public void setModeName(String modeName) { this.modeName = modeName; }

    public List<Long> getSequence() { return sequence; }
    public void setSequence(List<Long> sequence) { this.sequence = sequence; }

    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }

    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

    public Long getCurrentPackageId() { return currentPackageId; }
    public void setCurrentPackageId(Long currentPackageId) { this.currentPackageId = currentPackageId; }

    public OperationTraceResponse getLastTrace() { return lastTrace; }
    public void setLastTrace(OperationTraceResponse lastTrace) { this.lastTrace = lastTrace; }

    public List<Interval> getCurrentIntervals() { return currentIntervals; }
    public void setCurrentIntervals(List<Interval> currentIntervals) { this.currentIntervals = currentIntervals; }

    public long getTotalUniquePackages() { return totalUniquePackages; }
    public void setTotalUniquePackages(long totalUniquePackages) { this.totalUniquePackages = totalUniquePackages; }

    public int getIntervalCount() { return intervalCount; }
    public void setIntervalCount(int intervalCount) { this.intervalCount = intervalCount; }

    public long getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(long duplicateCount) { this.duplicateCount = duplicateCount; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
