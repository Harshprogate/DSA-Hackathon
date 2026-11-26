package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.dto.OperationTraceResponse;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalOperation;

import java.util.*;

/**
 * Pure, shared TreeMap interval decision engine.
 * Used identically by production insertions, What-If previews, isolated replay simulations, and benchmarks.
 */
public final class TreeMapIntervalEngine {

    private TreeMapIntervalEngine() {
    }

    /**
     * Evaluates what an insertion of packageId would do on the given TreeMap without mutating it.
     */
    public static OperationTraceResponse evaluate(NavigableMap<Long, Long> map, long packageId, boolean includeSnapshots) {
        long startNano = System.nanoTime();

        OperationTraceResponse trace = new OperationTraceResponse();
        trace.setPackageId(packageId);
        trace.setIntervalsBefore(map.size());

        List<Interval> before = includeSnapshots ? snapshot(map) : null;
        trace.setBeforeIntervals(before);

        List<String> steps = includeSnapshots ? new ArrayList<>(6) : null;
        if (steps != null) {
            steps.add("Step 1: Receive package ID " + packageId + ".");
        }

        // Step 2: floorEntry(packageId)
        Map.Entry<Long, Long> left = map.floorEntry(packageId);
        Interval floorInterval = (left != null) ? new Interval(left.getKey(), left.getValue()) : null;
        trace.setFloorEntry(floorInterval);
        if (steps != null) {
            steps.add("Step 2: Call floorEntry(" + packageId + ") -> " + (floorInterval != null ? floorInterval : "null") + ".");
        }

        // Duplicate check
        if (left != null && packageId <= left.getValue()) {
            trace.setAlreadyExists(true);
            trace.setConnectsLeft(false);
            trace.setConnectsRight(false);
            trace.setOperation(IntervalOperation.DUPLICATE_DETECTED);
            trace.setRemovedIntervals(Collections.emptyList());
            trace.setCreatedOrUpdatedInterval(floorInterval);
            trace.setIntervalsAfter(map.size());
            trace.setAfterIntervals(before);
            trace.setMessage("Package ID " + packageId + " already exists inside interval " + floorInterval + ".");
            if (steps != null) {
                steps.add("Step 3: Check containment (" + packageId + " <= " + left.getValue() + ") -> Duplicate detected.");
                steps.add("Step 4: TreeMap remains unmodified.");
                trace.setSteps(steps);
            }
            trace.setDurationNanos(System.nanoTime() - startNano);
            return trace;
        }

        // Step 3: ceilingEntry(packageId + 1)
        Map.Entry<Long, Long> right = map.ceilingEntry(packageId + 1);
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
            trace.setIntervalsAfter(map.size() - 1);
            trace.setMessage("Package " + packageId + " bridged " + floorInterval + " and " + ceilingInterval + " into " + merged + ".");
            if (steps != null) {
                steps.add("Step 4: Both left (" + left.getValue() + " + 1 == " + packageId + ") and right (" + (packageId + 1) + " == " + right.getKey() + ") connect!");
                steps.add("Step 5: Remove TreeMap entries " + floorInterval + " and " + ceilingInterval + ".");
                steps.add("Step 6: Insert merged interval " + merged + ".");
            }
        } else if (connectsLeft) {
            trace.setOperation(IntervalOperation.LEFT_EXTENSION);
            Interval extended = new Interval(left.getKey(), packageId);
            trace.setRemovedIntervals(List.of(floorInterval));
            trace.setCreatedOrUpdatedInterval(extended);
            trace.setIntervalsAfter(map.size());
            trace.setMessage("Package " + packageId + " extended left interval " + floorInterval + " to " + extended + ".");
            if (steps != null) {
                steps.add("Step 4: Left interval connects (" + left.getValue() + " + 1 == " + packageId + "); right does not.");
                steps.add("Step 5: Update key " + left.getKey() + " end boundary from " + left.getValue() + " to " + packageId + ".");
            }
        } else if (connectsRight) {
            trace.setOperation(IntervalOperation.RIGHT_EXTENSION);
            Interval extended = new Interval(packageId, right.getValue());
            trace.setRemovedIntervals(List.of(ceilingInterval));
            trace.setCreatedOrUpdatedInterval(extended);
            trace.setIntervalsAfter(map.size());
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
            trace.setIntervalsAfter(map.size() + 1);
            trace.setMessage("Package " + packageId + " created standalone interval " + created + ".");
            if (steps != null) {
                steps.add("Step 4: Neither neighbor connects.");
                steps.add("Step 5: Insert new standalone interval " + created + ".");
            }
        }

        if (includeSnapshots) {
            // Compute predicted afterIntervals without mutating original map
            List<Interval> after = computePredictedAfter(before, trace);
            trace.setAfterIntervals(after);
            trace.setSteps(steps);
        }

        trace.setDurationNanos(System.nanoTime() - startNano);
        return trace;
    }

    /**
     * Evaluates and applies the insertion of packageId onto the given TreeMap.
     */
    public static OperationTraceResponse apply(NavigableMap<Long, Long> map, long packageId, boolean includeSnapshots) {
        long startNano = System.nanoTime();
        OperationTraceResponse trace = evaluate(map, packageId, includeSnapshots);

        switch (trace.getOperation()) {
            case DUPLICATE_DETECTED:
                break;
            case BRIDGE_MERGE:
                Interval left = trace.getFloorEntry();
                Interval right = trace.getCeilingEntry();
                map.remove(left.getStart());
                map.remove(right.getStart());
                map.put(left.getStart(), right.getEnd());
                break;
            case LEFT_EXTENSION:
                map.put(trace.getFloorEntry().getStart(), packageId);
                break;
            case RIGHT_EXTENSION:
                Interval r = trace.getCeilingEntry();
                map.remove(r.getStart());
                map.put(packageId, r.getEnd());
                break;
            case NEW_INTERVAL:
                map.put(packageId, packageId);
                break;
        }

        trace.setIntervalsAfter(map.size());
        if (includeSnapshots) {
            trace.setAfterIntervals(snapshot(map));
        }
        trace.setDurationNanos(System.nanoTime() - startNano);
        return trace;
    }

    public static List<Interval> snapshot(NavigableMap<Long, Long> map) {
        List<Interval> list = new ArrayList<>(map.size());
        for (Map.Entry<Long, Long> entry : map.entrySet()) {
            list.add(new Interval(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    private static List<Interval> computePredictedAfter(List<Interval> before, OperationTraceResponse trace) {
        if (trace.getOperation() == IntervalOperation.DUPLICATE_DETECTED) {
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
}
