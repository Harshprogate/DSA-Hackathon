package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.StatisticsResponse;
import com.warehouse.arrivalindex.repository.PackageIntervalRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PackageIndexService 50,000 Packages Performance & Scalability Test")
class PackageIndexPerformanceTest {

    private PackageIntervalRepositoryImpl repository;
    private PackageIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new PackageIntervalRepositoryImpl();
        service = new PackageIndexServiceImpl(repository);
    }

    @Test
    @DisplayName("Verify 50,000 unique package IDs arriving in shuffled order")
    void testFiftyThousandPackagesPerformance() {
        int count = 50_000;
        List<Long> ids = new ArrayList<>(count);
        for (long i = 0; i < count; i++) {
            ids.add(i);
        }

        // Shuffle to simulate completely arbitrary arrival order
        Collections.shuffle(ids, new Random(42));

        long startTime = System.currentTimeMillis();

        for (Long id : ids) {
            service.addPackage(id);
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Time taken to insert 50,000 package IDs incrementally: " + duration + " ms");

        // After all numbers 0..49999 are inserted, they must merge into exactly 1 interval: [0, 49999]
        List<Interval> intervals = service.getAllIntervals().getIntervals();
        assertEquals(1, intervals.size(), "All 50,000 consecutive IDs should merge into a single interval");
        assertEquals(new Interval(0, 49_999), intervals.get(0));

        StatisticsResponse stats = service.getStatistics();
        assertEquals(count, stats.getTotalUniquePackages());
        assertEquals(1, stats.getIntervalCount());
        assertEquals(0L, stats.getSmallestId());
        assertEquals(49_999L, stats.getLargestId());

        // Performance expectation: 50,000 individual addPackage calls (each returning full snapshot) should complete under 15s
        assertTrue(duration < 15_000, "50,000 insertions should complete under 15 seconds (was " + duration + " ms)");

        // Also verify batch insertion of 50,000 shuffled IDs completes in under 1 second
        service.reset();
        long batchStart = System.currentTimeMillis();
        service.addBatch(ids);
        long batchDuration = System.currentTimeMillis() - batchStart;
        System.out.println("Time taken to insert 50,000 package IDs via addBatch: " + batchDuration + " ms");
        assertTrue(batchDuration < 2000, "50,000 batch insertions should complete under 2 seconds (was " + batchDuration + " ms)");

        // Test bounded range query on the 50,000 items
        long qStart = System.nanoTime();
        var bounded = service.getBoundedIntervals(10_000, 20_000);
        long qDuration = System.nanoTime() - qStart;
        assertEquals(1, bounded.getIntervals().size());
        assertEquals(new Interval(10_000, 20_000), bounded.getIntervals().get(0));
        System.out.println("Range query on 50,000 items took: " + (qDuration / 1_000_000.0) + " ms");

        // Test missing query on complete range
        var missing = service.getMissingIntervals(10_000, 20_000);
        assertTrue(missing.getMissingIntervals().isEmpty());
    }

    @Test
    @DisplayName("Verify sparse large package IDs up to 1,000,000,000 without allocating huge memory")
    void testSparseLargeIdsMemoryEfficiency() {
        System.gc();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Insert 10,000 sparse random numbers scattered between 0 and 1,000,000,000
        Random random = new Random(1337);
        Set<Long> uniqueGenerated = new HashSet<>();
        while (uniqueGenerated.size() < 10_000) {
            uniqueGenerated.add((long) (random.nextDouble() * 1_000_000_000L));
        }

        for (Long id : uniqueGenerated) {
            service.addPackage(id);
        }

        System.gc();
        long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsedMb = Math.max(0, (memoryAfter - memoryBefore) / (1024 * 1024));
        System.out.println("Retained memory for 10,000 sparse intervals in [0, 1B]: ~" + memoryUsedMb + " MB");

        StatisticsResponse stats = service.getStatistics();
        assertEquals(10_000, stats.getTotalUniquePackages());
        assertTrue(stats.getIntervalCount() <= 10_000);
        assertNotNull(stats.getSmallestId());
        assertNotNull(stats.getLargestId());

        // An array of 1 billion elements would require 8 GB.
        // The sparse TreeMap only stores 10,000 entries (< 250 MB heap fluctuation).
        assertTrue(memoryUsedMb < 250, "Memory used must be modest and not allocate huge arrays (was " + memoryUsedMb + " MB)");
    }
}
