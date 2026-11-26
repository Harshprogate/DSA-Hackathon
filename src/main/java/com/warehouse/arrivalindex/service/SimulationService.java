package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.dto.OperationTraceResponse;
import com.warehouse.arrivalindex.dto.SimulationStateResponse;
import com.warehouse.arrivalindex.exception.InvalidPackageIdException;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalOperation;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages isolated, deterministic arrival replay simulation sessions.
 * Uses TreeMapIntervalEngine on an isolated TreeMap so the production index is never modified.
 */
@Service
public class SimulationService {

    private static final int MAX_SESSIONS = 50;

    private static class SimulationSession {
        final String sessionId;
        final String modeName;
        final List<Long> sequence;
        final List<SimulationStateResponse> stepStates;
        int currentIndex = 0;

        SimulationSession(String sessionId, String modeName, List<Long> sequence, List<SimulationStateResponse> stepStates) {
            this.sessionId = sessionId;
            this.modeName = modeName;
            this.sequence = sequence;
            this.stepStates = stepStates;
        }
    }

    private final Map<String, SimulationSession> sessions = new ConcurrentHashMap<>();

    public SimulationStateResponse startSimulation(List<Long> sequence, String modeName) {
        if (sequence == null || sequence.isEmpty()) {
            throw new InvalidPackageIdException("Simulation sequence cannot be null or empty");
        }
        if (sequence.size() > 500) {
            throw new InvalidPackageIdException("Simulation sequence cannot exceed 500 items");
        }
        for (Long id : sequence) {
            if (id == null || id < 0 || id > PackageIndexServiceImpl.MAX_PACKAGE_ID) {
                throw new InvalidPackageIdException("Invalid package ID in simulation sequence: " + id);
            }
        }

        if (sessions.size() >= MAX_SESSIONS) {
            // Evict oldest key if too many sessions accumulate
            Iterator<String> it = sessions.keySet().iterator();
            if (it.hasNext()) {
                sessions.remove(it.next());
            }
        }

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        String resolvedMode = (modeName == null || modeName.isBlank()) ? "Custom Sequence" : modeName;

        // Pre-compute deterministic step snapshots using an isolated TreeMap
        NavigableMap<Long, Long> isolatedMap = new TreeMap<>();
        List<SimulationStateResponse> states = new ArrayList<>(sequence.size() + 1);

        long uniquePackages = 0;
        long dupCount = 0;

        // Step 0: Initial empty state
        states.add(buildState(sessionId, resolvedMode, sequence, 0, null, null,
                Collections.emptyList(), 0, 0, false));

        for (int i = 0; i < sequence.size(); i++) {
            long id = sequence.get(i);
            OperationTraceResponse trace = TreeMapIntervalEngine.apply(isolatedMap, id, true);
            if (trace.getOperation() == IntervalOperation.DUPLICATE_DETECTED) {
                dupCount++;
            } else {
                uniquePackages++;
            }
            List<Interval> currentIntervals = trace.getAfterIntervals();
            states.add(buildState(
                    sessionId,
                    resolvedMode,
                    sequence,
                    i + 1,
                    id,
                    trace,
                    currentIntervals,
                    uniquePackages,
                    dupCount,
                    (i + 1) == sequence.size()
            ));
        }

        SimulationSession session = new SimulationSession(sessionId, resolvedMode, List.copyOf(sequence), states);
        sessions.put(sessionId, session);
        return states.get(0);
    }

    public SimulationStateResponse step(String sessionId, String direction) {
        SimulationSession session = getSessionOrThrow(sessionId);
        synchronized (session) {
            if ("PREV".equalsIgnoreCase(direction) || "PREVIOUS".equalsIgnoreCase(direction)) {
                if (session.currentIndex > 0) {
                    session.currentIndex--;
                }
            } else if ("RESET".equalsIgnoreCase(direction)) {
                session.currentIndex = 0;
            } else {
                if (session.currentIndex < session.stepStates.size() - 1) {
                    session.currentIndex++;
                }
            }
            return session.stepStates.get(session.currentIndex);
        }
    }

    public SimulationStateResponse getState(String sessionId) {
        SimulationSession session = getSessionOrThrow(sessionId);
        synchronized (session) {
            return session.stepStates.get(session.currentIndex);
        }
    }

    private SimulationSession getSessionOrThrow(String sessionId) {
        SimulationSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Simulation session not found: " + sessionId);
        }
        return session;
    }

    private SimulationStateResponse buildState(String sessionId, String modeName, List<Long> sequence,
                                               int stepIndex, Long currentPackageId, OperationTraceResponse lastTrace,
                                               List<Interval> intervals, long uniquePackages, long dupCount,
                                               boolean completed) {
        SimulationStateResponse res = new SimulationStateResponse();
        res.setSessionId(sessionId);
        res.setModeName(modeName);
        res.setSequence(sequence);
        res.setCurrentStepIndex(stepIndex);
        res.setTotalSteps(sequence.size());
        res.setCurrentPackageId(currentPackageId);
        res.setLastTrace(lastTrace);
        res.setCurrentIntervals(intervals);
        res.setTotalUniquePackages(uniquePackages);
        res.setIntervalCount(intervals.size());
        res.setDuplicateCount(dupCount);
        res.setCompleted(completed);
        return res;
    }
}
