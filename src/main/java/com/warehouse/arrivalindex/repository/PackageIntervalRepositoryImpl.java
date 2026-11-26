package com.warehouse.arrivalindex.repository;

import com.warehouse.arrivalindex.dto.AuditEntry;
import com.warehouse.arrivalindex.dto.IndexSnapshot;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalOperation;
import com.warehouse.arrivalindex.model.StatisticsResponse;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory repository implementation using a TreeMap to store
 * non-overlapping consecutive intervals, along with audit logs, snapshots, and undo history.
 */
@Repository
public class PackageIntervalRepositoryImpl implements PackageIntervalRepository {

    public static class UndoRecord {
        private final long packageId;
        private final IntervalOperation operation;
        private final List<Interval> removedIntervals;
        private final Interval createdOrUpdatedInterval;

        public UndoRecord(long packageId, IntervalOperation operation,
                          List<Interval> removedIntervals, Interval createdOrUpdatedInterval) {
            this.packageId = packageId;
            this.operation = operation;
            this.removedIntervals = removedIntervals;
            this.createdOrUpdatedInterval = createdOrUpdatedInterval;
        }

        public long getPackageId() { return packageId; }
        public IntervalOperation getOperation() { return operation; }
        public List<Interval> getRemovedIntervals() { return removedIntervals; }
        public Interval getCreatedOrUpdatedInterval() { return createdOrUpdatedInterval; }
    }

    private static final int MAX_AUDIT_ENTRIES = 500;
    private static final int MAX_SNAPSHOTS = 100;

    private final TreeMap<Long, Long> intervals = new TreeMap<>();
    private long totalUniquePackages = 0;
    private long totalSuccessfulInsertions = 0;
    private long duplicateCount = 0;
    private long bridgeMergeCount = 0;
    private long extensionCount = 0;
    private long invalidRequestCount = 0;
    private final Map<Long, Long> duplicateFrequency = new HashMap<>();

    private final Deque<UndoRecord> undoStack = new ArrayDeque<>();
    private final List<AuditEntry> auditLog = new ArrayList<>();
    private final List<IndexSnapshot> snapshots = new ArrayList<>();
    private long auditSeq = 0;
    private long snapshotSeq = 0;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public ReentrantReadWriteLock.WriteLock getWriteLock() {
        return writeLock;
    }

    public ReentrantReadWriteLock.ReadLock getReadLock() {
        return readLock;
    }

    @Override
    public Map.Entry<Long, Long> floorEntry(long packageId) {
        return intervals.floorEntry(packageId);
    }

    @Override
    public Map.Entry<Long, Long> ceilingEntry(long packageId) {
        return intervals.ceilingEntry(packageId);
    }

    @Override
    public void put(long start, long end) {
        intervals.put(start, end);
    }

    @Override
    public void remove(long start) {
        intervals.remove(start);
    }

    @Override
    public List<Interval> getAllIntervals() {
        readLock.lock();
        try {
            return snapshotIntervalsUnlocked();
        } finally {
            readLock.unlock();
        }
    }

    public List<Interval> snapshotIntervalsUnlocked() {
        List<Interval> result = new ArrayList<>(intervals.size());
        for (Map.Entry<Long, Long> entry : intervals.entrySet()) {
            result.add(new Interval(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Override
    public List<Interval> findOverlappingIntervals(long rangeStart, long rangeEnd) {
        readLock.lock();
        try {
            List<Interval> result = new ArrayList<>();
            if (intervals.isEmpty()) {
                return result;
            }

            Map.Entry<Long, Long> floor = intervals.floorEntry(rangeStart);
            long searchKey = (floor != null && floor.getValue() >= rangeStart)
                    ? floor.getKey()
                    : rangeStart;

            for (Map.Entry<Long, Long> entry : intervals.tailMap(searchKey, true).entrySet()) {
                long start = entry.getKey();
                long end = entry.getValue();

                if (start > rangeEnd) {
                    break;
                }

                long overlapStart = Math.max(start, rangeStart);
                long overlapEnd = Math.min(end, rangeEnd);

                if (overlapStart <= overlapEnd) {
                    result.add(new Interval(overlapStart, overlapEnd));
                }
            }

            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<Interval> findMissingIntervals(long rangeStart, long rangeEnd) {
        readLock.lock();
        try {
            List<Interval> missing = new ArrayList<>();
            long cursor = rangeStart;

            if (intervals.isEmpty()) {
                missing.add(new Interval(rangeStart, rangeEnd));
                return missing;
            }

            Map.Entry<Long, Long> floor = intervals.floorEntry(rangeStart);
            long searchKey = (floor != null && floor.getValue() >= rangeStart)
                    ? floor.getKey()
                    : rangeStart;

            for (Map.Entry<Long, Long> entry : intervals.tailMap(searchKey, true).entrySet()) {
                long start = entry.getKey();
                long end = entry.getValue();

                if (start > rangeEnd) {
                    break;
                }

                long clippedStart = Math.max(start, rangeStart);
                long clippedEnd = Math.min(end, rangeEnd);

                if (cursor < clippedStart) {
                    missing.add(new Interval(cursor, clippedStart - 1));
                }

                cursor = Math.max(cursor, clippedEnd + 1);

                if (cursor > rangeEnd) {
                    break;
                }
            }

            if (cursor <= rangeEnd) {
                missing.add(new Interval(cursor, rangeEnd));
            }

            return missing;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public StatisticsResponse getStatistics() {
        readLock.lock();
        try {
            Long smallest = intervals.isEmpty() ? null : intervals.firstKey();
            Long largest = intervals.isEmpty() ? null : intervals.lastEntry().getValue();

            StatisticsResponse stats = new StatisticsResponse(
                    totalUniquePackages,
                    intervals.size(),
                    smallest,
                    largest
            );
            stats.setTotalSuccessfulInsertions(totalSuccessfulInsertions);
            stats.setDuplicateCount(duplicateCount);
            stats.setBridgeMergeCount(bridgeMergeCount);
            stats.setExtensionCount(extensionCount);
            stats.setInvalidRequestCount(invalidRequestCount);

            Long mostDupId = null;
            long maxDupCount = 0;
            for (Map.Entry<Long, Long> e : duplicateFrequency.entrySet()) {
                if (e.getValue() > maxDupCount) {
                    maxDupCount = e.getValue();
                    mostDupId = e.getKey();
                }
            }
            stats.setMostDuplicatedPackageId(mostDupId);

            long totalAttempts = totalSuccessfulInsertions + duplicateCount;
            double dupPct = totalAttempts == 0 ? 0.0 : Math.round((duplicateCount * 10000.0) / totalAttempts) / 100.0;
            stats.setDuplicatePercentage(dupPct);

            return stats;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void incrementUniquePackageCount() {
        totalUniquePackages++;
    }

    public void recordInsertionStats(IntervalOperation op) {
        totalSuccessfulInsertions++;
        if (op == IntervalOperation.BRIDGE_MERGE) {
            bridgeMergeCount++;
        } else if (op == IntervalOperation.LEFT_EXTENSION || op == IntervalOperation.RIGHT_EXTENSION) {
            extensionCount++;
        }
    }

    public void recordDuplicate(long packageId) {
        duplicateCount++;
        duplicateFrequency.merge(packageId, 1L, Long::sum);
    }

    public void recordInvalidRequest(Long packageId, String message) {
        writeLock.lock();
        try {
            invalidRequestCount++;
            AuditEntry entry = new AuditEntry();
            entry.setId(++auditSeq);
            entry.setPackageId(packageId);
            entry.setOperationType("INVALID_REQUEST");
            entry.setIntervalsBeforeCount(intervals.size());
            entry.setIntervalsAfterCount(intervals.size());
            entry.setStatus("REJECTED");
            entry.setMessage(message);
            addAuditEntryUnlocked(entry);
        } finally {
            writeLock.unlock();
        }
    }

    public void pushUndoRecord(UndoRecord record) {
        undoStack.push(record);
    }

    public UndoRecord popUndoRecord() {
        return undoStack.isEmpty() ? null : undoStack.pop();
    }

    public void decrementUniqueAndOpStats(IntervalOperation op) {
        if (totalUniquePackages > 0) totalUniquePackages--;
        if (totalSuccessfulInsertions > 0) totalSuccessfulInsertions--;
        if (op == IntervalOperation.BRIDGE_MERGE && bridgeMergeCount > 0) {
            bridgeMergeCount--;
        } else if ((op == IntervalOperation.LEFT_EXTENSION || op == IntervalOperation.RIGHT_EXTENSION) && extensionCount > 0) {
            extensionCount--;
        }
    }

    public void addAuditEntryUnlocked(AuditEntry entry) {
        if (entry.getId() == 0) {
            entry.setId(++auditSeq);
        }
        auditLog.add(entry);
        if (auditLog.size() > MAX_AUDIT_ENTRIES) {
            auditLog.remove(0);
        }
    }

    public void captureSnapshotUnlocked(String triggerOperation, Long triggerPackageId) {
        IndexSnapshot snapshot = new IndexSnapshot(
                ++snapshotSeq,
                LocalDateTime.now(),
                totalUniquePackages,
                intervals.size(),
                triggerOperation,
                triggerPackageId,
                snapshotIntervalsUnlocked()
        );
        snapshots.add(snapshot);
        if (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }
    }

    public List<AuditEntry> getAuditLog(String operationFilter) {
        readLock.lock();
        try {
            List<AuditEntry> result = new ArrayList<>();
            for (int i = auditLog.size() - 1; i >= 0; i--) {
                AuditEntry e = auditLog.get(i);
                if (operationFilter == null || operationFilter.isBlank() || "ALL".equalsIgnoreCase(operationFilter)
                        || operationFilter.equalsIgnoreCase(e.getOperationType())) {
                    result.add(e);
                }
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    public List<IndexSnapshot> getSnapshots() {
        readLock.lock();
        try {
            List<IndexSnapshot> copy = new ArrayList<>(snapshots.size());
            for (int i = snapshots.size() - 1; i >= 0; i--) {
                copy.add(snapshots.get(i));
            }
            return copy;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            intervals.clear();
            totalUniquePackages = 0;
            totalSuccessfulInsertions = 0;
            duplicateCount = 0;
            bridgeMergeCount = 0;
            extensionCount = 0;
            invalidRequestCount = 0;
            duplicateFrequency.clear();
            undoStack.clear();
            auditLog.clear();
            snapshots.clear();
            auditSeq = 0;
            snapshotSeq = 0;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long getTotalUniquePackages() {
        readLock.lock();
        try {
            return totalUniquePackages;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int getIntervalCount() {
        readLock.lock();
        try {
            return intervals.size();
        } finally {
            readLock.unlock();
        }
    }
}
