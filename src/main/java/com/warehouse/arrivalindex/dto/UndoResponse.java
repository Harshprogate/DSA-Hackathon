package com.warehouse.arrivalindex.dto;

import com.warehouse.arrivalindex.model.Interval;
import java.util.List;

public class UndoResponse {

    private boolean undone;
    private Long revertedPackageId;
    private String revertedOperation;
    private String message;
    private long totalUniquePackages;
    private List<Interval> currentIntervals;

    public UndoResponse() {
    }

    public UndoResponse(boolean undone, Long revertedPackageId, String revertedOperation, String message,
                        long totalUniquePackages, List<Interval> currentIntervals) {
        this.undone = undone;
        this.revertedPackageId = revertedPackageId;
        this.revertedOperation = revertedOperation;
        this.message = message;
        this.totalUniquePackages = totalUniquePackages;
        this.currentIntervals = currentIntervals;
    }

    public boolean isUndone() { return undone; }
    public void setUndone(boolean undone) { this.undone = undone; }

    public Long getRevertedPackageId() { return revertedPackageId; }
    public void setRevertedPackageId(Long revertedPackageId) { this.revertedPackageId = revertedPackageId; }

    public String getRevertedOperation() { return revertedOperation; }
    public void setRevertedOperation(String revertedOperation) { this.revertedOperation = revertedOperation; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTotalUniquePackages() { return totalUniquePackages; }
    public void setTotalUniquePackages(long totalUniquePackages) { this.totalUniquePackages = totalUniquePackages; }

    public List<Interval> getCurrentIntervals() { return currentIntervals; }
    public void setCurrentIntervals(List<Interval> currentIntervals) { this.currentIntervals = currentIntervals; }
}
