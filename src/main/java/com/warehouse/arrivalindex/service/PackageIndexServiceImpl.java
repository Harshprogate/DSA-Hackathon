package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.dto.*;
import com.warehouse.arrivalindex.exception.InvalidPackageIdException;
import com.warehouse.arrivalindex.exception.InvalidRangeException;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalOperation;
import com.warehouse.arrivalindex.model.IntervalResponse;
import com.warehouse.arrivalindex.model.PackageResponse;
import com.warehouse.arrivalindex.model.StatisticsResponse;
import com.warehouse.arrivalindex.repository.PackageIntervalRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implementation of PackageIndexService utilizing TreeMap navigation operations
 * for O(log n) incremental interval management, tracing, previewing, undo, and analytics.
 */
@Service
public class PackageIndexServiceImpl implements PackageIndexService {

    private static final Logger log = LoggerFactory.getLogger(PackageIndexServiceImpl.class);
    public static final long MIN_PACKAGE_ID = 0L;
    public static final long MAX_PACKAGE_ID = 1_000_000_000L;

    private final PackageIntervalRepositoryImpl repository;

    public PackageIndexServiceImpl(PackageIntervalRepositoryImpl repository) {
        this.repository = repository;
    }

    @Override
    public PackageResponse addPackage(long packageId) {
        validatePackageId(packageId);

        long startNano = System.nanoTime();
        repository.getWriteLock().lock();
        try {
            // Capture full step-by-step trace & snapshots for interactive sizes (<= 200 intervals)
            boolean includeSnapshots = repository.getIntervalCount() <= 200;
            OperationTraceResponse trace = evaluateOnRepository(packageId, includeSnapshots);

            if (trace.getOperation() == IntervalOperation.DUPLICATE_DETECTED) {
                repository.recordDuplicate(packageId);
                trace.setDurationNanos(System.nanoTime() - startNano);
                recordAudit(packageId, "DUPLICATE", trace.getIntervalsBefore(), trace.getIntervalsAfter(),
                        trace.getDurationNanos(), "DUPLICATE", "Package ID already exists");

                return new PackageResponse(
                        packageId,
                        "DUPLICATE",
                        "Package ID already exists",
                        repository.snapshotIntervalsUnlocked(),
                        trace
                );
            }

            applyTraceToRepository(trace);
            repository.incrementUniquePackageCount();
            repository.recordInsertionStats(trace.getOperation());
            repository.pushUndoRecord(new PackageIntervalRepositoryImpl.UndoRecord(
                    packageId,
                    trace.getOperation(),
                    trace.getRemovedIntervals(),
                    trace.getCreatedOrUpdatedInterval()
            ));

            List<Interval> after = repository.snapshotIntervalsUnlocked();
            trace.setIntervalsAfter(after.size());
            if (includeSnapshots) {
                trace.setAfterIntervals(after);
            }
            trace.setDurationNanos(System.nanoTime() - startNano);

            String auditOp = mapToAuditOp(trace.getOperation());
            recordAudit(packageId, auditOp, trace.getIntervalsBefore(), trace.getIntervalsAfter(),
                    trace.getDurationNanos(), "SUCCESS", trace.getMessage());

            if (includeSnapshots) {
                repository.captureSnapshotUnlocked(auditOp, packageId);
            }

            return new PackageResponse(
                    packageId,
                    "ADDED",
                    "Package successfully registered",
                    after,
                    trace
            );
        } finally {
            repository.getWriteLock().unlock();
        }
    }

    @Override
    public OperationTraceResponse previewPackage(long packageId) {
        validatePackageId(packageId);
        repository.getReadLock().lock();
        try {
            return evaluateOnRepository(packageId, true);
        } finally {
            repository.getReadLock().unlock();
        }
    }

    @Override
    public BatchResponse addBatch(List<Long> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) {
            throw new InvalidPackageIdException("Batch package ID list cannot be null or empty");
        }

        for (Long id : packageIds) {
            if (id == null) {
                throw new InvalidPackageIdException("Package ID in batch cannot be null");
            }
            validatePackageId(id);
        }

        int processed = 0;
        int newlyAdded = 0;
        int duplicates = 0;
        long batchStartNano = System.nanoTime();

        repository.getWriteLock().lock();
        try {
            int intervalsBefore = repository.getIntervalCount();

            for (Long id : packageIds) {
                processed++;
                Map.Entry<Long, Long> left = repository.floorEntry(id);

                if (left != null && id <= left.getValue()) {
                    duplicates++;
                    repository.recordDuplicate(id);
                    continue;
                }

                boolean connectsLeft = (left != null && left.getValue() + 1 == id);
                Map.Entry<Long, Long> right = repository.ceilingEntry(id + 1);
                boolean connectsRight = (right != null && right.getKey() == id + 1);

                IntervalOperation op;
                List<Interval> removed = new ArrayList<>(2);
                Interval createdOrUpdated;

                if (connectsLeft && connectsRight) {
                    op = IntervalOperation.BRIDGE_MERGE;
                    long mergedStart = left.getKey();
                    long mergedEnd = right.getValue();
                    removed.add(new Interval(left.getKey(), left.getValue()));
                    removed.add(new Interval(right.getKey(), right.getValue()));
                    repository.remove(left.getKey());
                    repository.remove(right.getKey());
                    repository.put(mergedStart, mergedEnd);
                    createdOrUpdated = new Interval(mergedStart, mergedEnd);
                } else if (connectsLeft) {
                    op = IntervalOperation.LEFT_EXTENSION;
                    removed.add(new Interval(left.getKey(), left.getValue()));
                    repository.put(left.getKey(), id);
                    createdOrUpdated = new Interval(left.getKey(), id);
                } else if (connectsRight) {
                    op = IntervalOperation.RIGHT_EXTENSION;
                    long rightEnd = right.getValue();
                    removed.add(new Interval(right.getKey(), rightEnd));
                    repository.remove(right.getKey());
                    repository.put(id, rightEnd);
                    createdOrUpdated = new Interval(id, rightEnd);
                } else {
                    op = IntervalOperation.NEW_INTERVAL;
                    repository.put(id, id);
                    createdOrUpdated = new Interval(id, id);
                }

                repository.incrementUniquePackageCount();
                repository.recordInsertionStats(op);
                repository.pushUndoRecord(new PackageIntervalRepositoryImpl.UndoRecord(id, op, removed, createdOrUpdated));
                newlyAdded++;
            }

            List<Interval> finalIntervals = repository.snapshotIntervalsUnlocked();
            long duration = System.nanoTime() - batchStartNano;

            recordAudit(null, "BATCH", intervalsBefore, finalIntervals.size(), duration, "SUCCESS",
                    "Batch processed " + processed + " IDs (" + newlyAdded + " added, " + duplicates + " duplicates)");

            if (finalIntervals.size() <= 2000) {
                repository.captureSnapshotUnlocked("BATCH", null);
            }

            return new BatchResponse(processed, newlyAdded, duplicates, finalIntervals);
        } finally {
            repository.getWriteLock().unlock();
        }
    }

    @Override
    public IntervalResponse getAllIntervals() {
        List<Interval> intervals = repository.getAllIntervals();
        long totalReceived = repository.getTotalUniquePackages();
        return new IntervalResponse(totalReceived, intervals.size(), intervals);
    }

    @Override
    public MissingIntervalsResponse getMissingIntervals(long start, long end) {
        validateRange(start, end);
        List<Interval> missing = repository.findMissingIntervals(start, end);
        return new MissingIntervalsResponse(start, end, missing);
    }

    @Override
    public RangeIntervalsResponse getBoundedIntervals(long start, long end) {
        validateRange(start, end);
        List<Interval> overlapping = repository.findOverlappingIntervals(start, end);
        return new RangeIntervalsResponse(start, end, overlapping);
    }

    @Override
    public StatisticsResponse getStatistics() {
        return repository.getStatistics();
    }

    @Override
    public AnalyticsResponse getAnalytics(long start, long end) {
        validateRange(start, end);

        List<Interval> received = repository.findOverlappingIntervals(start, end);
        List<Interval> missing = repository.findMissingIntervals(start, end);

        long expectedCount = end - start + 1;
        long receivedCount = 0;
        for (Interval iv : received) {
            receivedCount += iv.size();
        }
        long missingCount = expectedCount - receivedCount;
        double completionPct = expectedCount == 0 ? 100.0
                : Math.round((receivedCount * 10000.0) / expectedCount) / 100.0;

        Interval largestMissing = null;
        Interval smallestMissing = null;
        for (Interval iv : missing) {
            if (largestMissing == null || iv.size() > largestMissing.size()) {
                largestMissing = iv;
            }
            if (smallestMissing == null || iv.size() < smallestMissing.size()) {
                smallestMissing = iv;
            }
        }

        AnalyticsResponse res = new AnalyticsResponse();
        res.setRangeStart(start);
        res.setRangeEnd(end);
        res.setExpectedCount(expectedCount);
        res.setReceivedCount(receivedCount);
        res.setMissingCount(missingCount);
        res.setCompletionPercentage(completionPct);
        res.setReceivedIntervalCount(received.size());
        res.setMissingIntervalCount(missing.size());
        res.setLargestMissingRange(largestMissing);
        res.setSmallestMissingRange(smallestMissing);
        res.setReceivedIntervals(received);
        res.setMissingIntervals(missing);
        return res;
    }

    @Override
    public UndoResponse undoLastArrival() {
        long startNano = System.nanoTime();
        repository.getWriteLock().lock();
        try {
            PackageIntervalRepositoryImpl.UndoRecord record = repository.popUndoRecord();
            if (record == null) {
                return new UndoResponse(
                        false,
                        null,
                        null,
                        "No insertions available to undo",
                        repository.getTotalUniquePackages(),
                        repository.snapshotIntervalsUnlocked()
                );
            }

            int beforeCount = repository.getIntervalCount();

            switch (record.getOperation()) {
                case NEW_INTERVAL:
                    repository.remove(record.getPackageId());
                    break;
                case LEFT_EXTENSION: {
                    Interval prevLeft = record.getRemovedIntervals().get(0);
                    repository.put(prevLeft.getStart(), prevLeft.getEnd());
                    break;
                }
                case RIGHT_EXTENSION: {
                    Interval prevRight = record.getRemovedIntervals().get(0);
                    repository.remove(record.getPackageId());
                    repository.put(prevRight.getStart(), prevRight.getEnd());
                    break;
                }
                case BRIDGE_MERGE: {
                    Interval prevLeft = record.getRemovedIntervals().get(0);
                    Interval prevRight = record.getRemovedIntervals().get(1);
                    repository.remove(prevLeft.getStart());
                    repository.put(prevLeft.getStart(), prevLeft.getEnd());
                    repository.put(prevRight.getStart(), prevRight.getEnd());
                    break;
                }
                default:
                    break;
            }

            repository.decrementUniqueAndOpStats(record.getOperation());
            List<Interval> current = repository.snapshotIntervalsUnlocked();

            String msg = "Reverted " + record.getOperation() + " for package ID " + record.getPackageId();
            recordAudit(record.getPackageId(), "UNDO", beforeCount, current.size(),
                    System.nanoTime() - startNano, "SUCCESS", msg);

            if (current.size() <= 2000) {
                repository.captureSnapshotUnlocked("UNDO", record.getPackageId());
            }

            return new UndoResponse(
                    true,
                    record.getPackageId(),
                    record.getOperation().name(),
                    msg,
                    repository.getTotalUniquePackages(),
                    current
            );
        } finally {
            repository.getWriteLock().unlock();
        }
    }

    @Override
    public List<AuditEntry> getAuditLog(String operationFilter) {
        return repository.getAuditLog(operationFilter);
    }

    @Override
    public List<IndexSnapshot> getSnapshots() {
        return repository.getSnapshots();
    }

    @Override
    public void recordInvalidRequest(Long packageId, String message) {
        repository.recordInvalidRequest(packageId, message);
    }

    @Override
    public void reset() {
        repository.clear();
    }

    private OperationTraceResponse evaluateOnRepository(long packageId, boolean includeSnapshots) {
        OperationTraceResponse trace = new OperationTraceResponse();
        trace.setPackageId(packageId);
        int count = repository.getIntervalCount();
        trace.setIntervalsBefore(count);

        List<Interval> before = includeSnapshots ? repository.snapshotIntervalsUnlocked() : null;
        trace.setBeforeIntervals(before);

        List<String> steps = includeSnapshots ? new ArrayList<>(6) : null;
        if (steps != null) {
            steps.add("Step 1: Receive package ID " + packageId + ".");
        }

        Map.Entry<Long, Long> left = repository.floorEntry(packageId);
        Interval floorInterval = (left != null) ? new Interval(left.getKey(), left.getValue()) : null;
        trace.setFloorEntry(floorInterval);
        if (steps != null) {
            steps.add("Step 2: Call floorEntry(" + packageId + ") -> " + (floorInterval != null ? floorInterval : "null") + ".");
        }

        if (left != null && packageId <= left.getValue()) {
            trace.setAlreadyExists(true);
            trace.setConnectsLeft(false);
            trace.setConnectsRight(false);
            trace.setOperation(IntervalOperation.DUPLICATE_DETECTED);
            trace.setRemovedIntervals(Collections.emptyList());
            trace.setCreatedOrUpdatedInterval(floorInterval);
            trace.setIntervalsAfter(count);
            trace.setAfterIntervals(before);
            trace.setMessage("Package ID " + packageId + " already exists inside interval " + floorInterval + ".");
            if (steps != null) {
                steps.add("Step 3: Check containment (" + packageId + " <= " + left.getValue() + ") -> Duplicate detected.");
                steps.add("Step 4: TreeMap remains unmodified.");
                trace.setSteps(steps);
            }
            return trace;
        }

        Map.Entry<Long, Long> right = repository.ceilingEntry(packageId + 1);
        Interval ceilingInterval = (right != null) ? new Interval(right.getKey(), right.getValue()) : null;
        trace.setCeilingEntry(ceilingInterval);
        if (steps != null) {
            steps.add("Step 3: Call ceilingEntry(" + (packageId + 1) + ") -> " + (ceilingInterval != null ? ceilingInterval : "null") + ".");
        }

        boolean connectsLeft = (left != null && left.getValue() + 1 == packageId);
        boolean connectsRight = (right != null && right.getKey() == packageId + 1);
        trace.setAlreadyExists(false);
        trace.setConnectsLeft(connectsLeft);
        trace.setConnectsRight(connectsRight);

        if (connectsLeft && connectsRight) {
            trace.setOperation(IntervalOperation.BRIDGE_MERGE);
            Interval merged = new Interval(left.getKey(), right.getValue());
            trace.setRemovedIntervals(List.of(floorInterval, ceilingInterval));
            trace.setCreatedOrUpdatedInterval(merged);
            trace.setIntervalsAfter(count - 1);
            trace.setMessage("Package " + packageId + " connected two existing intervals: " + floorInterval + " and " + ceilingInterval + " -> " + merged + ".");
            if (steps != null) {
                steps.add("Step 4: Both left (" + left.getValue() + " + 1 == " + packageId + ") and right (" + (packageId + 1) + " == " + right.getKey() + ") connect.");
                steps.add("Step 5: Remove TreeMap entries " + floorInterval + " and " + ceilingInterval + ".");
                steps.add("Step 6: Insert merged interval " + merged + ".");
            }
        } else if (connectsLeft) {
            trace.setOperation(IntervalOperation.LEFT_EXTENSION);
            Interval extended = new Interval(left.getKey(), packageId);
            trace.setRemovedIntervals(List.of(floorInterval));
            trace.setCreatedOrUpdatedInterval(extended);
            trace.setIntervalsAfter(count);
            trace.setMessage("Package " + packageId + " extended left interval " + floorInterval + " to " + extended + ".");
            if (steps != null) {
                steps.add("Step 4: Left interval connects (" + left.getValue() + " + 1 == " + packageId + "); right does not.");
                steps.add("Step 5: Update key " + left.getKey() + " end boundary to " + packageId + ".");
            }
        } else if (connectsRight) {
            trace.setOperation(IntervalOperation.RIGHT_EXTENSION);
            Interval extended = new Interval(packageId, right.getValue());
            trace.setRemovedIntervals(List.of(ceilingInterval));
            trace.setCreatedOrUpdatedInterval(extended);
            trace.setIntervalsAfter(count);
            trace.setMessage("Package " + packageId + " extended right interval " + ceilingInterval + " to " + extended + ".");
            if (steps != null) {
                steps.add("Step 4: Right interval connects (" + (packageId + 1) + " == " + right.getKey() + "); left does not.");
                steps.add("Step 5: Remove key " + right.getKey() + " and insert " + extended + ".");
            }
        } else {
            trace.setOperation(IntervalOperation.NEW_INTERVAL);
            Interval created = new Interval(packageId, packageId);
            trace.setRemovedIntervals(Collections.emptyList());
            trace.setCreatedOrUpdatedInterval(created);
            trace.setIntervalsAfter(count + 1);
            trace.setMessage("Package " + packageId + " created standalone interval " + created + ".");
            if (steps != null) {
                steps.add("Step 4: Neither neighbor connects.");
                steps.add("Step 5: Insert new standalone interval " + created + ".");
            }
        }

        if (includeSnapshots) {
            trace.setSteps(steps);
            trace.setAfterIntervals(computePredictedAfter(before, trace));
        }

        return trace;
    }

    private void applyTraceToRepository(OperationTraceResponse trace) {
        switch (trace.getOperation()) {
            case BRIDGE_MERGE:
                repository.remove(trace.getFloorEntry().getStart());
                repository.remove(trace.getCeilingEntry().getStart());
                repository.put(trace.getCreatedOrUpdatedInterval().getStart(), trace.getCreatedOrUpdatedInterval().getEnd());
                break;
            case LEFT_EXTENSION:
                repository.put(trace.getFloorEntry().getStart(), trace.getPackageId());
                break;
            case RIGHT_EXTENSION:
                repository.remove(trace.getCeilingEntry().getStart());
                repository.put(trace.getPackageId(), trace.getCeilingEntry().getEnd());
                break;
            case NEW_INTERVAL:
                repository.put(trace.getPackageId(), trace.getPackageId());
                break;
            case DUPLICATE_DETECTED:
                break;
        }
    }

    private List<Interval> computePredictedAfter(List<Interval> before, OperationTraceResponse trace) {
        if (before == null || trace.getOperation() == IntervalOperation.DUPLICATE_DETECTED) {
            return before;
        }
        List<Interval> after = new ArrayList<>(before.size() + 1);
        Set<Long> removedStarts = new HashSet<>();
        if (trace.getRemovedIntervals() != null) {
            for (Interval r : trace.getRemovedIntervals()) {
                removedStarts.add(r.getStart());
            }
        }
        boolean inserted = false;
        Interval created = trace.getCreatedOrUpdatedInterval();
        for (Interval iv : before) {
            if (removedStarts.contains(iv.getStart())) {
                continue;
            }
            if (!inserted && created.getStart() < iv.getStart()) {
                after.add(created);
                inserted = true;
            }
            after.add(iv);
        }
        if (!inserted) {
            after.add(created);
        }
        return after;
    }

    private String mapToAuditOp(IntervalOperation op) {
        switch (op) {
            case NEW_INTERVAL: return "NEW_PACKAGE";
            case LEFT_EXTENSION:
            case RIGHT_EXTENSION: return "INTERVAL_EXTENSION";
            case BRIDGE_MERGE: return "BRIDGE_MERGE";
            case DUPLICATE_DETECTED: return "DUPLICATE";
            default: return op.name();
        }
    }

    private void recordAudit(Long packageId, String opType, int beforeCount, int afterCount,
                             long durationNanos, String status, String message) {
        AuditEntry entry = new AuditEntry();
        entry.setPackageId(packageId);
        entry.setOperationType(opType);
        entry.setIntervalsBeforeCount(beforeCount);
        entry.setIntervalsAfterCount(afterCount);
        entry.setDurationNanos(durationNanos);
        entry.setStatus(status);
        entry.setMessage(message);
        repository.addAuditEntryUnlocked(entry);
    }

    private void validatePackageId(long packageId) {
        if (packageId < MIN_PACKAGE_ID) {
            throw new InvalidPackageIdException("Package ID cannot be negative. Provided: " + packageId);
        }
        if (packageId > MAX_PACKAGE_ID) {
            throw new InvalidPackageIdException("Package ID cannot exceed " + MAX_PACKAGE_ID + ". Provided: " + packageId);
        }
    }

    private void validateRange(long start, long end) {
        if (start < MIN_PACKAGE_ID) {
            throw new InvalidRangeException("Range start cannot be negative. Provided: " + start);
        }
        if (end > MAX_PACKAGE_ID) {
            throw new InvalidRangeException("Range end cannot exceed " + MAX_PACKAGE_ID + ". Provided: " + end);
        }
        if (start > end) {
            throw new InvalidRangeException("Range start (" + start + ") must be less than or equal to range end (" + end + ")");
        }
    }
}
