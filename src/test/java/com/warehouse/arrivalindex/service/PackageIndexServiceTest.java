package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.dto.BatchResponse;
import com.warehouse.arrivalindex.dto.MissingIntervalsResponse;
import com.warehouse.arrivalindex.dto.RangeIntervalsResponse;
import com.warehouse.arrivalindex.exception.InvalidPackageIdException;
import com.warehouse.arrivalindex.exception.InvalidRangeException;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalResponse;
import com.warehouse.arrivalindex.model.PackageResponse;
import com.warehouse.arrivalindex.model.StatisticsResponse;
import com.warehouse.arrivalindex.repository.PackageIntervalRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PackageIndexService Unit Tests")
class PackageIndexServiceTest {

    private PackageIntervalRepositoryImpl repository;
    private PackageIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new PackageIntervalRepositoryImpl();
        service = new PackageIndexServiceImpl(repository);
    }

    @Test
    @DisplayName("Test 1: Basic insertion - sequential 1, 2, 3")
    void test1_basicInsertion() {
        service.addPackage(1);
        service.addPackage(2);
        PackageResponse response = service.addPackage(3);

        assertEquals("ADDED", response.getStatus());
        assertEquals(List.of(new Interval(1, 3)), response.getCurrentIntervals());
        assertEquals(3, service.getStatistics().getTotalUniquePackages());
        assertEquals(1, service.getStatistics().getIntervalCount());
    }

    @Test
    @DisplayName("Test 2: Arbitrary arrival order - 1, 3, 7, 2, 6")
    void test2_arbitraryArrivalOrder() {
        service.addPackage(1);
        service.addPackage(3);
        service.addPackage(7);
        service.addPackage(2);
        PackageResponse response = service.addPackage(6);

        assertEquals("ADDED", response.getStatus());
        assertEquals(List.of(new Interval(1, 3), new Interval(6, 7)), response.getCurrentIntervals());
        assertEquals(5, service.getStatistics().getTotalUniquePackages());
        assertEquals(2, service.getStatistics().getIntervalCount());
    }

    @Test
    @DisplayName("Test 3: Bridge merge - [[1,3], [5,8]] bridged by 4")
    void test3_bridgeMerge() {
        // Setup initial: [[1,3], [5,8]]
        service.addPackage(1);
        service.addPackage(2);
        service.addPackage(3);
        service.addPackage(5);
        service.addPackage(6);
        service.addPackage(7);
        service.addPackage(8);

        assertEquals(List.of(new Interval(1, 3), new Interval(5, 8)), service.getAllIntervals().getIntervals());

        // Insert bridge package 4
        PackageResponse response = service.addPackage(4);

        assertEquals("ADDED", response.getStatus());
        assertEquals(List.of(new Interval(1, 8)), response.getCurrentIntervals());
        assertEquals(8, service.getStatistics().getTotalUniquePackages());
        assertEquals(1, service.getStatistics().getIntervalCount());
    }

    @Test
    @DisplayName("Test 4: Duplicate insertion - [[1,5]] receiving 3")
    void test4_duplicateInsertion() {
        for (long i = 1; i <= 5; i++) {
            service.addPackage(i);
        }

        // Insert duplicate 3
        PackageResponse response = service.addPackage(3);

        assertEquals("DUPLICATE", response.getStatus());
        assertEquals("Package ID already exists", response.getMessage());
        assertEquals(List.of(new Interval(1, 5)), response.getCurrentIntervals());
        assertEquals(5, service.getStatistics().getTotalUniquePackages());
        assertEquals(1, service.getStatistics().getIntervalCount());
    }

    @Test
    @DisplayName("Test 5: Left extension - [[5,8]] receiving 4")
    void test5_leftExtension() {
        for (long i = 5; i <= 8; i++) {
            service.addPackage(i);
        }

        // Insert 4 (connects to the left of [5,8])
        PackageResponse response = service.addPackage(4);

        assertEquals("ADDED", response.getStatus());
        assertEquals(List.of(new Interval(4, 8)), response.getCurrentIntervals());
        assertEquals(5, service.getStatistics().getTotalUniquePackages());
        assertEquals(1, service.getStatistics().getIntervalCount());
    }

    @Test
    @DisplayName("Test 6: Right extension - [[5,8]] receiving 9")
    void test6_rightExtension() {
        for (long i = 5; i <= 8; i++) {
            service.addPackage(i);
        }

        // Insert 9 (connects to the right of [5,8])
        PackageResponse response = service.addPackage(9);

        assertEquals("ADDED", response.getStatus());
        assertEquals(List.of(new Interval(5, 9)), response.getCurrentIntervals());
        assertEquals(5, service.getStatistics().getTotalUniquePackages());
        assertEquals(1, service.getStatistics().getIntervalCount());
    }

    @Test
    @DisplayName("Test 7: Sparse IDs - 0, 1,000,000, 1,000,000,000")
    void test7_sparseIds() {
        service.addPackage(0);
        service.addPackage(1_000_000);
        PackageResponse response = service.addPackage(1_000_000_000);

        List<Interval> expected = List.of(
                new Interval(0, 0),
                new Interval(1_000_000, 1_000_000),
                new Interval(1_000_000_000, 1_000_000_000)
        );

        assertEquals(expected, response.getCurrentIntervals());
        assertEquals(3, service.getStatistics().getTotalUniquePackages());
        assertEquals(3, service.getStatistics().getIntervalCount());
        assertEquals(0L, service.getStatistics().getSmallestId());
        assertEquals(1_000_000_000L, service.getStatistics().getLargestId());
    }

    @Test
    @DisplayName("Test 8: Missing range - Received [[1,3], [6,7]], Query [1,7]")
    void test8_missingRange() {
        service.addPackage(1);
        service.addPackage(2);
        service.addPackage(3);
        service.addPackage(6);
        service.addPackage(7);

        MissingIntervalsResponse response = service.getMissingIntervals(1, 7);

        assertEquals(1, response.getRangeStart());
        assertEquals(7, response.getRangeEnd());
        assertEquals(List.of(new Interval(4, 5)), response.getMissingIntervals());
    }

    @Test
    @DisplayName("Test 9: Partially overlapping query - Received [[1,5], [8,12]], Query [3,10]")
    void test9_partiallyOverlappingQuery() {
        for (long i = 1; i <= 5; i++) service.addPackage(i);
        for (long i = 8; i <= 12; i++) service.addPackage(i);

        RangeIntervalsResponse response = service.getBoundedIntervals(3, 10);

        assertEquals(3, response.getRangeStart());
        assertEquals(10, response.getRangeEnd());
        List<Interval> expected = List.of(new Interval(3, 5), new Interval(8, 10));
        assertEquals(expected, response.getIntervals());
    }

    @Test
    @DisplayName("Test 10: Empty index - Query [1,10]")
    void test10_emptyIndex() {
        MissingIntervalsResponse missingResponse = service.getMissingIntervals(1, 10);
        assertEquals(List.of(new Interval(1, 10)), missingResponse.getMissingIntervals());

        RangeIntervalsResponse rangeResponse = service.getBoundedIntervals(1, 10);
        assertTrue(rangeResponse.getIntervals().isEmpty());

        StatisticsResponse stats = service.getStatistics();
        assertEquals(0, stats.getTotalUniquePackages());
        assertEquals(0, stats.getIntervalCount());
        assertNull(stats.getSmallestId());
        assertNull(stats.getLargestId());
    }

    @Test
    @DisplayName("Test 11: Complete range - Received [[1,10]], Query [1,10]")
    void test11_completeRange() {
        for (long i = 1; i <= 10; i++) {
            service.addPackage(i);
        }

        MissingIntervalsResponse response = service.getMissingIntervals(1, 10);
        assertTrue(response.getMissingIntervals().isEmpty());
    }

    @Test
    @DisplayName("Test 12: Boundary values - 0 and 1,000,000,000")
    void test12_boundaryValues() {
        service.addPackage(0);
        service.addPackage(1_000_000_000);

        StatisticsResponse stats = service.getStatistics();
        assertEquals(2, stats.getTotalUniquePackages());
        assertEquals(2, stats.getIntervalCount());
        assertEquals(0L, stats.getSmallestId());
        assertEquals(1_000_000_000L, stats.getLargestId());

        // Range query spanning full system limits
        MissingIntervalsResponse missing = service.getMissingIntervals(0, 1_000_000_000);
        assertEquals(List.of(new Interval(1, 999_999_999)), missing.getMissingIntervals());
    }

    @Test
    @DisplayName("Test 13: Invalid input - negative IDs, exceeds max, negative range, start > end")
    void test13_invalidInput() {
        // Negative package ID
        assertThrows(InvalidPackageIdException.class, () -> service.addPackage(-1));

        // Package ID exceeds 1,000,000,000
        assertThrows(InvalidPackageIdException.class, () -> service.addPackage(1_000_000_001L));

        // Negative range boundary
        assertThrows(InvalidRangeException.class, () -> service.getMissingIntervals(-5, 10));
        assertThrows(InvalidRangeException.class, () -> service.getBoundedIntervals(5, -10));

        // Range end exceeds maximum
        assertThrows(InvalidRangeException.class, () -> service.getMissingIntervals(0, 1_000_000_001L));
        assertThrows(InvalidRangeException.class, () -> service.getBoundedIntervals(0, 1_000_000_001L));

        // Start greater than end
        assertThrows(InvalidRangeException.class, () -> service.getMissingIntervals(50, 20));
        assertThrows(InvalidRangeException.class, () -> service.getBoundedIntervals(50, 20));

        // Null or empty batch
        assertThrows(InvalidPackageIdException.class, () -> service.addBatch(null));
        assertThrows(InvalidPackageIdException.class, () -> service.addBatch(List.of()));
        assertThrows(InvalidPackageIdException.class, () -> service.addBatch(java.util.Arrays.asList(1L, null, 3L)));
    }

    @Test
    @DisplayName("Test 14: Batch insertion - [1, 3, 7, 2, 6, 3]")
    void test14_batchInsertion() {
        BatchResponse response = service.addBatch(List.of(1L, 3L, 7L, 2L, 6L, 3L));

        assertEquals(6, response.getTotalProcessed());
        assertEquals(5, response.getNewlyAdded());
        assertEquals(1, response.getDuplicates());

        List<Interval> expectedIntervals = List.of(new Interval(1, 3), new Interval(6, 7));
        assertEquals(expectedIntervals, response.getCurrentIntervals());

        // Verify total number of unique IDs
        IntervalResponse allIntervals = service.getAllIntervals();
        assertEquals(5, allIntervals.getTotalReceived());
        assertEquals(2, allIntervals.getIntervalCount());
        assertEquals(expectedIntervals, allIntervals.getIntervals());

        StatisticsResponse stats = service.getStatistics();
        assertEquals(5, stats.getTotalUniquePackages());
        assertEquals(2, stats.getIntervalCount());
        assertEquals(1L, stats.getSmallestId());
        assertEquals(7L, stats.getLargestId());
    }

    @Test
    @DisplayName("Demonstration test: Step-by-step arrivals and bridge merge")
    void testStepByStepDemonstration() {
        // Arrival 1: Insert 1 -> [[1,1]]
        PackageResponse r1 = service.addPackage(1);
        assertEquals(List.of(new Interval(1, 1)), r1.getCurrentIntervals());

        // Arrival 2: Insert 3 -> [[1,1], [3,3]]
        PackageResponse r2 = service.addPackage(3);
        assertEquals(List.of(new Interval(1, 1), new Interval(3, 3)), r2.getCurrentIntervals());

        // Arrival 3: Insert 7 -> [[1,1], [3,3], [7,7]]
        PackageResponse r3 = service.addPackage(7);
        assertEquals(List.of(new Interval(1, 1), new Interval(3, 3), new Interval(7, 7)), r3.getCurrentIntervals());

        // Arrival 4: Insert 2 -> [[1,3], [7,7]]
        PackageResponse r4 = service.addPackage(2);
        assertEquals(List.of(new Interval(1, 3), new Interval(7, 7)), r4.getCurrentIntervals());

        // Arrival 5: Insert 6 -> [[1,3], [6,7]]
        PackageResponse r5 = service.addPackage(6);
        assertEquals(List.of(new Interval(1, 3), new Interval(6, 7)), r5.getCurrentIntervals());

        // Reset and demonstrate bridge merge explicitly:
        // Before: [[1,3], [5,8]]
        service.reset();
        service.addBatch(List.of(1L, 2L, 3L, 5L, 6L, 7L, 8L));
        assertEquals(List.of(new Interval(1, 3), new Interval(5, 8)), service.getAllIntervals().getIntervals());

        // Insert 4
        PackageResponse bridgeResponse = service.addPackage(4);
        assertEquals("ADDED", bridgeResponse.getStatus());
        assertEquals(List.of(new Interval(1, 8)), bridgeResponse.getCurrentIntervals());
        assertEquals(8, service.getStatistics().getTotalUniquePackages());
    }
}
