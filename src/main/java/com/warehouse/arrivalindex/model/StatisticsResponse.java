package com.warehouse.arrivalindex.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Overall system statistics for received packages and intervals")
public class StatisticsResponse {

    @Schema(description = "Total unique package IDs received", example = "5")
    private long totalUniquePackages;

    @Schema(description = "Total number of disjoint consecutive intervals", example = "2")
    private int intervalCount;

    @Schema(description = "Smallest received package ID (null if no packages received)", example = "1")
    private Long smallestId;

    @Schema(description = "Largest received package ID (null if no packages received)", example = "7")
    private Long largestId;

    @Schema(description = "Total successful package insertions")
    private long totalSuccessfulInsertions;

    @Schema(description = "Total duplicate arrival attempts")
    private long duplicateCount;

    @Schema(description = "Total bridge merge operations performed")
    private long bridgeMergeCount;

    @Schema(description = "Total interval extension operations (left or right)")
    private long extensionCount;

    @Schema(description = "Total invalid requests rejected")
    private long invalidRequestCount;

    @Schema(description = "Most frequently duplicated package ID (null if no duplicates)")
    private Long mostDuplicatedPackageId;

    @Schema(description = "Duplicate percentage of total arrival attempts")
    private double duplicatePercentage;

    public StatisticsResponse() {
    }

    public StatisticsResponse(long totalUniquePackages, int intervalCount, Long smallestId, Long largestId) {
        this.totalUniquePackages = totalUniquePackages;
        this.intervalCount = intervalCount;
        this.smallestId = smallestId;
        this.largestId = largestId;
    }

    public long getTotalUniquePackages() {
        return totalUniquePackages;
    }

    public void setTotalUniquePackages(long totalUniquePackages) {
        this.totalUniquePackages = totalUniquePackages;
    }

    public int getIntervalCount() {
        return intervalCount;
    }

    public void setIntervalCount(int intervalCount) {
        this.intervalCount = intervalCount;
    }

    public Long getSmallestId() {
        return smallestId;
    }

    public void setSmallestId(Long smallestId) {
        this.smallestId = smallestId;
    }

    public Long getLargestId() {
        return largestId;
    }

    public void setLargestId(Long largestId) {
        this.largestId = largestId;
    }

    public long getTotalSuccessfulInsertions() { return totalSuccessfulInsertions; }
    public void setTotalSuccessfulInsertions(long totalSuccessfulInsertions) { this.totalSuccessfulInsertions = totalSuccessfulInsertions; }

    public long getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(long duplicateCount) { this.duplicateCount = duplicateCount; }

    public long getBridgeMergeCount() { return bridgeMergeCount; }
    public void setBridgeMergeCount(long bridgeMergeCount) { this.bridgeMergeCount = bridgeMergeCount; }

    public long getExtensionCount() { return extensionCount; }
    public void setExtensionCount(long extensionCount) { this.extensionCount = extensionCount; }

    public long getInvalidRequestCount() { return invalidRequestCount; }
    public void setInvalidRequestCount(long invalidRequestCount) { this.invalidRequestCount = invalidRequestCount; }

    public Long getMostDuplicatedPackageId() { return mostDuplicatedPackageId; }
    public void setMostDuplicatedPackageId(Long mostDuplicatedPackageId) { this.mostDuplicatedPackageId = mostDuplicatedPackageId; }

    public double getDuplicatePercentage() { return duplicatePercentage; }
    public void setDuplicatePercentage(double duplicatePercentage) { this.duplicatePercentage = duplicatePercentage; }
}
