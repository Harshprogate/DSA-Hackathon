package com.warehouse.arrivalindex;

import com.warehouse.arrivalindex.dto.*;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalOperation;
import com.warehouse.arrivalindex.model.PackageResponse;
import com.warehouse.arrivalindex.model.StatisticsResponse;
import com.warehouse.arrivalindex.repository.PackageIntervalRepositoryImpl;
import com.warehouse.arrivalindex.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Hackathon Features & Concurrency Integration Tests")
class HackathonFeaturesTest {

    private PackageIntervalRepositoryImpl repository;
    private PackageIndexServiceImpl indexService;
    private SimulationService simulationService;
    private BenchmarkService benchmarkService;
    private ExportService exportService;

    @BeforeEach
    void setUp() {
        repository = new PackageIntervalRepositoryImpl();
        indexService = new PackageIndexServiceImpl(repository);
        simulationService = new SimulationService();
        benchmarkService = new BenchmarkService();
        exportService = new ExportService(indexService);
    }

    @Test
    @DisplayName("What-If Preview predicts operations without modifying production TreeMap")
    void testPreviewDoesNotMutateIndex() {
        indexService.addBatch(List.of(1L, 2L, 3L, 5L, 6L, 7L, 8L));
        List<Interval> before = indexService.getAllIntervals().getIntervals();

        // Preview Bridge Merge (4)
        OperationTraceResponse preview = indexService.previewPackage(4L);
        assertEquals(IntervalOperation.BRIDGE_MERGE, preview.getOperation());
        assertEquals(new Interval(1, 3), preview.getFloorEntry());
        assertEquals(new Interval(5, 8), preview.getCeilingEntry());
        assertEquals(new Interval(1, 8), preview.getCreatedOrUpdatedInterval());
        assertEquals(2, preview.getIntervalsBefore());
        assertEquals(1, preview.getIntervalsAfter());

        // Verify production index was NOT modified
        assertEquals(before, indexService.getAllIntervals().getIntervals());
        assertEquals(7, indexService.getStatistics().getTotalUniquePackages());

        // Preview Duplicate (2)
        OperationTraceResponse dupPreview = indexService.previewPackage(2L);
        assertEquals(IntervalOperation.DUPLICATE_DETECTED, dupPreview.getOperation());
        assertTrue(dupPreview.isAlreadyExists());
    }

    @Test
    @DisplayName("Undo reverts bridge merge by splitting interval back into left and right")
    void testUndoBridgeMergeSplitsInterval() {
        indexService.addBatch(List.of(1L, 2L, 3L, 5L, 6L, 7L, 8L));
        PackageResponse mergeRes = indexService.addPackage(4L);
        assertEquals(List.of(new Interval(1, 8)), mergeRes.getCurrentIntervals());
        assertEquals(1, indexService.getStatistics().getBridgeMergeCount());

        // Undo package 4
        UndoResponse undo = indexService.undoLastArrival();
        assertTrue(undo.isUndone());
        assertEquals(4L, undo.getRevertedPackageId());
        assertEquals("BRIDGE_MERGE", undo.getRevertedOperation());
        assertEquals(List.of(new Interval(1, 3), new Interval(5, 8)), undo.getCurrentIntervals());
        assertEquals(7, undo.getTotalUniquePackages());
        assertEquals(0, indexService.getStatistics().getBridgeMergeCount());
    }

    @Test
    @DisplayName("Isolated Hackathon Demo Simulation progresses deterministically without touching production index")
    void testSimulationReplayIsolation() {
        // Put 100 in production index
        indexService.addPackage(100L);

        List<Long> demoSeq = List.of(1L, 3L, 7L, 2L, 6L, 4L, 5L, 3L);
        SimulationStateResponse state0 = simulationService.startSimulation(demoSeq, "Hackathon Demo");
        assertEquals(0, state0.getCurrentStepIndex());
        assertEquals(8, state0.getTotalSteps());

        // Step through all 8 items
        SimulationStateResponse s = state0;
        for (int i = 0; i < demoSeq.size(); i++) {
            s = simulationService.step(state0.getSessionId(), "NEXT");
        }
        assertTrue(s.isCompleted());
        assertEquals(7, s.getTotalUniquePackages());
        assertEquals(1, s.getIntervalCount());
        assertEquals(1, s.getDuplicateCount());
        assertEquals(List.of(new Interval(1, 7)), s.getCurrentIntervals());
        assertEquals(IntervalOperation.DUPLICATE_DETECTED, s.getLastTrace().getOperation());

        // Step backwards (Previous Step)
        SimulationStateResponse prev = simulationService.step(state0.getSessionId(), "PREV");
        assertEquals(7, prev.getCurrentStepIndex());
        assertEquals(IntervalOperation.BRIDGE_MERGE, prev.getLastTrace().getOperation());

        // Verify production index still only has [100, 100]
        assertEquals(List.of(new Interval(100, 100)), indexService.getAllIntervals().getIntervals());
    }

    @Test
    @DisplayName("Benchmark produces matching interval counts for Sorting Baseline and TreeMap")
    void testBenchmarkConsistency() {
        BenchmarkRequest req = new BenchmarkRequest(1000, "RANDOM", 3);
        BenchmarkResponse res = benchmarkService.runBenchmark(req);

        assertEquals(1000, res.getDatasetSize());
        assertEquals(res.getSortingBaseline().getIntervalCount(), res.getTreeMapIncremental().getIntervalCount());
        assertEquals(1, res.getTreeMapIncremental().getIntervalCount());
        assertTrue(res.getTreeMapIncremental().getMedianTotalTimeMs() >= 0);
        assertTrue(res.getSortingBaseline().getMedianTotalTimeMs() >= 0);
    }

    @Test
    @DisplayName("Warehouse Analytics computes exact coverage, completion %, and missing ranges")
    void testWarehouseAnalytics() {
        for (long i = 1; i <= 30; i++) indexService.addPackage(i);
        for (long i = 35; i <= 60; i++) indexService.addPackage(i);
        for (long i = 70; i <= 100; i++) indexService.addPackage(i);

        AnalyticsResponse analytics = indexService.getAnalytics(1, 100);
        assertEquals(100, analytics.getExpectedCount());
        assertEquals(87, analytics.getReceivedCount());
        assertEquals(13, analytics.getMissingCount());
        assertEquals(87.0, analytics.getCompletionPercentage());
        assertEquals(3, analytics.getReceivedIntervalCount());
        assertEquals(2, analytics.getMissingIntervalCount());
        assertEquals(new Interval(61, 69), analytics.getLargestMissingRange());
        assertEquals(new Interval(31, 34), analytics.getSmallestMissingRange());
    }

    @Test
    @DisplayName("Export generates valid CSV, JSON, and PDF outputs")
    void testExports() {
        indexService.addBatch(List.of(1L, 2L, 3L, 6L, 7L));

        String intervalsCsv = exportService.exportIntervalsCsv();
        assertTrue(intervalsCsv.contains("1,3,3"));
        assertTrue(intervalsCsv.contains("6,7,2"));

        String missingCsv = exportService.exportMissingCsv(1, 7);
        assertTrue(missingCsv.contains("4,5,2"));

        String activityCsv = exportService.exportActivityCsv();
        assertTrue(activityCsv.contains("BATCH"));

        Map<String, Object> json = exportService.exportCompleteJson();
        assertTrue(json.containsKey("statistics"));
        assertTrue(json.containsKey("intervals"));

        byte[] pdf = exportService.exportCompletenessPdf(1, 7);
        String pdfHeader = new String(pdf, 0, Math.min(8, pdf.length));
        assertTrue(pdfHeader.startsWith("%PDF-1.4"));
    }

    @Test
    @DisplayName("Concurrent package insertions maintain exact counts and valid disjoint intervals")
    void testConcurrentInsertions() throws InterruptedException, ExecutionException {
        int threads = 8;
        int totalIds = 4000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<Long> ids = new ArrayList<>(totalIds);
        for (long i = 0; i < totalIds; i++) {
            ids.add(i);
        }
        Collections.shuffle(ids, new Random(99));

        List<Callable<Void>> tasks = new ArrayList<>();
        int chunkSize = totalIds / threads;
        for (int t = 0; t < threads; t++) {
            int start = t * chunkSize;
            int end = (t == threads - 1) ? totalIds : start + chunkSize;
            List<Long> slice = ids.subList(start, end);
            tasks.add(() -> {
                for (Long id : slice) {
                    indexService.addPackage(id);
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        StatisticsResponse stats = indexService.getStatistics();
        assertEquals(totalIds, stats.getTotalUniquePackages());
        assertEquals(1, stats.getIntervalCount());
        assertEquals(List.of(new Interval(0, totalIds - 1)), indexService.getAllIntervals().getIntervals());
    }
}
