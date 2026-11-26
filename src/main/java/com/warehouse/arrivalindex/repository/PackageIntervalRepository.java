package com.warehouse.arrivalindex.repository;

import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.StatisticsResponse;

import java.util.List;
import java.util.Map;

/**
 * In-memory repository interface managing consecutive package ID intervals using a TreeMap index.
 */
public interface PackageIntervalRepository {

    /**
     * Finds the interval whose starting ID is the greatest start ID less than or equal to the given package ID.
     */
    Map.Entry<Long, Long> floorEntry(long packageId);

    /**
     * Finds the interval whose starting ID is the least start ID greater than or equal to the given package ID.
     */
    Map.Entry<Long, Long> ceilingEntry(long packageId);

    /**
     * Inserts or updates an interval [start, end].
     */
    void put(long start, long end);

    /**
     * Removes an interval by its start ID.
     */
    void remove(long start);

    /**
     * Returns an unmodifiable snapshot of all currently stored intervals sorted by start ID.
     */
    List<Interval> getAllIntervals();

    /**
     * Returns received intervals overlapping [rangeStart, rangeEnd], clipped to the query range.
     * Operates in O(log k + r) time.
     */
    List<Interval> findOverlappingIntervals(long rangeStart, long rangeEnd);

    /**
     * Returns missing intervals within [rangeStart, rangeEnd].
     * Operates in O(log k + r) time without creating individual ID arrays.
     */
    List<Interval> findMissingIntervals(long rangeStart, long rangeEnd);

    /**
     * Returns current statistics (total unique packages, interval count, min ID, max ID).
     */
    StatisticsResponse getStatistics();

    /**
     * Increments the total unique package count.
     */
    void incrementUniquePackageCount();

    /**
     * Clears all stored intervals and resets statistics.
     */
    void clear();

    /**
     * Returns total unique package count.
     */
    long getTotalUniquePackages();

    /**
     * Returns number of disjoint intervals.
     */
    int getIntervalCount();
}
