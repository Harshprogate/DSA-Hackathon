package com.warehouse.arrivalindex.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEntry {

    private long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    private Long packageId;
    private String operationType;
    private int intervalsBeforeCount;
    private int intervalsAfterCount;
    private String previousSummary;
    private String updatedSummary;
    private long durationNanos;
    private String status;
    private String message;

    public AuditEntry() {
        this.timestamp = LocalDateTime.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public int getIntervalsBeforeCount() { return intervalsBeforeCount; }
    public void setIntervalsBeforeCount(int intervalsBeforeCount) { this.intervalsBeforeCount = intervalsBeforeCount; }

    public int getIntervalsAfterCount() { return intervalsAfterCount; }
    public void setIntervalsAfterCount(int intervalsAfterCount) { this.intervalsAfterCount = intervalsAfterCount; }

    public String getPreviousSummary() { return previousSummary; }
    public void setPreviousSummary(String previousSummary) { this.previousSummary = previousSummary; }

    public String getUpdatedSummary() { return updatedSummary; }
    public void setUpdatedSummary(String updatedSummary) { this.updatedSummary = updatedSummary; }

    public long getDurationNanos() { return durationNanos; }
    public void setDurationNanos(long durationNanos) { this.durationNanos = durationNanos; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
