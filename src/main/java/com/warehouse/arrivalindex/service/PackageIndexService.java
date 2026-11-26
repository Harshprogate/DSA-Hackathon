package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.dto.*;
import com.warehouse.arrivalindex.model.IntervalResponse;
import com.warehouse.arrivalindex.model.PackageResponse;
import com.warehouse.arrivalindex.model.StatisticsResponse;

import java.util.List;

/**
 * Service managing package ID arrivals, consecutive interval indexing, tracing, undo, and analytics.
 */
public interface PackageIndexService {

    PackageResponse addPackage(long packageId);

    OperationTraceResponse previewPackage(long packageId);

    BatchResponse addBatch(List<Long> packageIds);

    IntervalResponse getAllIntervals();

    MissingIntervalsResponse getMissingIntervals(long start, long end);

    RangeIntervalsResponse getBoundedIntervals(long start, long end);

    StatisticsResponse getStatistics();

    AnalyticsResponse getAnalytics(long start, long end);

    UndoResponse undoLastArrival();

    List<AuditEntry> getAuditLog(String operationFilter);

    List<IndexSnapshot> getSnapshots();

    void recordInvalidRequest(Long packageId, String message);

    void reset();
}
