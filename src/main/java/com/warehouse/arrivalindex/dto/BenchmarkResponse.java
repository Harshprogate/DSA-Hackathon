package com.warehouse.arrivalindex.dto;

public class BenchmarkResponse {

    public static class ApproachMetrics {
        private String approachName;
        private double medianTotalTimeMs;
        private double avgInsertionNs;
        private double maxInsertionNs;
        private int intervalCount;
        private long bridgeMerges;
        private long extensions;
        private long duplicates;
        private long estimatedMemoryBytes;

        public String getApproachName() { return approachName; }
        public void setApproachName(String approachName) { this.approachName = approachName; }

        public double getMedianTotalTimeMs() { return medianTotalTimeMs; }
        public void setMedianTotalTimeMs(double medianTotalTimeMs) { this.medianTotalTimeMs = medianTotalTimeMs; }

        public double getAvgInsertionNs() { return avgInsertionNs; }
        public void setAvgInsertionNs(double avgInsertionNs) { this.avgInsertionNs = avgInsertionNs; }

        public double getMaxInsertionNs() { return maxInsertionNs; }
        public void setMaxInsertionNs(double maxInsertionNs) { this.maxInsertionNs = maxInsertionNs; }

        public int getIntervalCount() { return intervalCount; }
        public void setIntervalCount(int intervalCount) { this.intervalCount = intervalCount; }

        public long getBridgeMerges() { return bridgeMerges; }
        public void setBridgeMerges(long bridgeMerges) { this.bridgeMerges = bridgeMerges; }

        public long getExtensions() { return extensions; }
        public void setExtensions(long extensions) { this.extensions = extensions; }

        public long getDuplicates() { return duplicates; }
        public void setDuplicates(long duplicates) { this.duplicates = duplicates; }

        public long getEstimatedMemoryBytes() { return estimatedMemoryBytes; }
        public void setEstimatedMemoryBytes(long estimatedMemoryBytes) { this.estimatedMemoryBytes = estimatedMemoryBytes; }
    }

    private int datasetSize;
    private String pattern;
    private int runs;
    private ApproachMetrics sortingBaseline;
    private ApproachMetrics treeMapIncremental;
    private double speedupFactor;
    private String methodologyNote;

    public int getDatasetSize() { return datasetSize; }
    public void setDatasetSize(int datasetSize) { this.datasetSize = datasetSize; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public int getRuns() { return runs; }
    public void setRuns(int runs) { this.runs = runs; }

    public ApproachMetrics getSortingBaseline() { return sortingBaseline; }
    public void setSortingBaseline(ApproachMetrics sortingBaseline) { this.sortingBaseline = sortingBaseline; }

    public ApproachMetrics getTreeMapIncremental() { return treeMapIncremental; }
    public void setTreeMapIncremental(ApproachMetrics treeMapIncremental) { this.treeMapIncremental = treeMapIncremental; }

    public double getSpeedupFactor() { return speedupFactor; }
    public void setSpeedupFactor(double speedupFactor) { this.speedupFactor = speedupFactor; }

    public String getMethodologyNote() { return methodologyNote; }
    public void setMethodologyNote(String methodologyNote) { this.methodologyNote = methodologyNote; }
}
