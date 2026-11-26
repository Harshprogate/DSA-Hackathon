package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.dto.BenchmarkRequest;
import com.warehouse.arrivalindex.dto.BenchmarkResponse;
import com.warehouse.arrivalindex.dto.OperationTraceResponse;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.IntervalOperation;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Benchmarks Approach A (Sorting-Based Baseline) vs Approach B (Incremental TreeMap)
 * using identical generated datasets across multiple runs and reporting median timings.
 */
@Service
public class BenchmarkService {

    public BenchmarkResponse runBenchmark(BenchmarkRequest request) {
        int size = request.getSize();
        String pattern = request.getPattern() == null ? "RANDOM" : request.getPattern().toUpperCase(Locale.ROOT);
        int runs = request.getRuns();

        List<Long> dataset = generateDataset(size, pattern);

        // Run Approach A (Sorting-Based Baseline)
        List<BenchmarkResponse.ApproachMetrics> sortRuns = new ArrayList<>(runs);
        for (int r = 0; r < runs; r++) {
            sortRuns.add(runSortingBaseline(dataset));
        }

        // Run Approach B (Incremental TreeMap)
        List<BenchmarkResponse.ApproachMetrics> treeRuns = new ArrayList<>(runs);
        for (int r = 0; r < runs; r++) {
            treeRuns.add(runTreeMapIncremental(dataset));
        }

        BenchmarkResponse.ApproachMetrics medianSort = pickMedianRun(sortRuns);
        BenchmarkResponse.ApproachMetrics medianTree = pickMedianRun(treeRuns);

        double speedup = medianTree.getMedianTotalTimeMs() > 0
                ? Math.round((medianSort.getMedianTotalTimeMs() / medianTree.getMedianTotalTimeMs()) * 100.0) / 100.0
                : 1.0;

        BenchmarkResponse response = new BenchmarkResponse();
        response.setDatasetSize(size);
        response.setPattern(pattern);
        response.setRuns(runs);
        response.setSortingBaseline(medianSort);
        response.setTreeMapIncremental(medianTree);
        response.setSpeedupFactor(speedup);

        int rebuildInterval = Math.max(1, size / 100);
        String rebuildDesc = rebuildInterval == 1
                ? "rebuilt intervals via full sort + linear scan on every insertion"
                : "rebuilt intervals via full sort + linear scan every " + rebuildInterval + " insertions (100 full rebuilds + final rebuild)";
        response.setMethodologyNote(
                "Identical " + size + "-ID " + pattern + " dataset executed " + runs + " times; median run reported. "
                        + "Sorting Baseline " + rebuildDesc + ", whereas Incremental TreeMap updated only adjacent entries in O(log n) per ID. "
                        + "Timings reflect live JVM execution and depend on hardware, JIT warm-up, garbage collection, and dataset distribution."
        );
        return response;
    }

    private BenchmarkResponse.ApproachMetrics runSortingBaseline(List<Long> dataset) {
        int n = dataset.size();
        int rebuildEvery = Math.max(1, n / 100);

        List<Long> rawIds = new ArrayList<>(n);
        List<Interval> builtIntervals = Collections.emptyList();
        long duplicates = 0;
        long maxOpNs = 0;

        long memBefore = usedMemoryBytes();
        long totalStart = System.nanoTime();

        Set<Long> seenForDupCount = new HashSet<>(n);
        for (int i = 0; i < n; i++) {
            long id = dataset.get(i);
            long opStart = System.nanoTime();

            if (!seenForDupCount.add(id)) {
                duplicates++;
            } else {
                rawIds.add(id);
            }

            if ((i + 1) % rebuildEvery == 0 || i == n - 1) {
                Collections.sort(rawIds);
                builtIntervals = buildIntervalsFromSorted(rawIds);
            }

            long opNs = System.nanoTime() - opStart;
            if (opNs > maxOpNs) {
                maxOpNs = opNs;
            }
        }

        long totalNs = System.nanoTime() - totalStart;
        long memAfter = usedMemoryBytes();

        BenchmarkResponse.ApproachMetrics m = new BenchmarkResponse.ApproachMetrics();
        m.setApproachName("Sorting-Based Baseline");
        m.setMedianTotalTimeMs(round2(totalNs / 1_000_000.0));
        m.setAvgInsertionNs(round2((double) totalNs / n));
        m.setMaxInsertionNs((double) maxOpNs);
        m.setIntervalCount(builtIntervals.size());
        m.setBridgeMerges(0);
        m.setExtensions(0);
        m.setDuplicates(duplicates);
        m.setEstimatedMemoryBytes(Math.max(0L, memAfter - memBefore));
        return m;
    }

    private BenchmarkResponse.ApproachMetrics runTreeMapIncremental(List<Long> dataset) {
        int n = dataset.size();
        NavigableMap<Long, Long> map = new TreeMap<>();

        long bridgeMerges = 0;
        long extensions = 0;
        long duplicates = 0;
        long maxOpNs = 0;

        long memBefore = usedMemoryBytes();
        long totalStart = System.nanoTime();

        for (int i = 0; i < n; i++) {
            long id = dataset.get(i);
            long opStart = System.nanoTime();

            OperationTraceResponse trace = TreeMapIntervalEngine.apply(map, id, false);
            IntervalOperation op = trace.getOperation();
            if (op == IntervalOperation.BRIDGE_MERGE) {
                bridgeMerges++;
            } else if (op == IntervalOperation.LEFT_EXTENSION || op == IntervalOperation.RIGHT_EXTENSION) {
                extensions++;
            } else if (op == IntervalOperation.DUPLICATE_DETECTED) {
                duplicates++;
            }

            long opNs = System.nanoTime() - opStart;
            if (opNs > maxOpNs) {
                maxOpNs = opNs;
            }
        }

        long totalNs = System.nanoTime() - totalStart;
        long memAfter = usedMemoryBytes();

        BenchmarkResponse.ApproachMetrics m = new BenchmarkResponse.ApproachMetrics();
        m.setApproachName("Incremental TreeMap");
        m.setMedianTotalTimeMs(round2(totalNs / 1_000_000.0));
        m.setAvgInsertionNs(round2((double) totalNs / n));
        m.setMaxInsertionNs((double) maxOpNs);
        m.setIntervalCount(map.size());
        m.setBridgeMerges(bridgeMerges);
        m.setExtensions(extensions);
        m.setDuplicates(duplicates);
        m.setEstimatedMemoryBytes(Math.max(0L, memAfter - memBefore));
        return m;
    }

    private List<Interval> buildIntervalsFromSorted(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return Collections.emptyList();
        }
        List<Interval> result = new ArrayList<>();
        long start = sorted.get(0);
        long end = start;
        for (int i = 1; i < sorted.size(); i++) {
            long cur = sorted.get(i);
            if (cur == end) {
                continue;
            }
            if (cur == end + 1) {
                end = cur;
            } else {
                result.add(new Interval(start, end));
                start = cur;
                end = cur;
            }
        }
        result.add(new Interval(start, end));
        return result;
    }

    private List<Long> generateDataset(int size, String pattern) {
        List<Long> list = new ArrayList<>(size);
        Random random = new Random(42L);

        switch (pattern) {
            case "SEQUENTIAL":
                for (int i = 0; i < size; i++) {
                    list.add((long) i);
                }
                break;
            case "REVERSE":
                for (int i = size - 1; i >= 0; i--) {
                    list.add((long) i);
                }
                break;
            case "SPARSE":
                for (int i = 0; i < size; i++) {
                    list.add((long) (random.nextDouble() * PackageIndexServiceImpl.MAX_PACKAGE_ID));
                }
                break;
            case "DUPLICATE_HEAVY":
                int uniquePool = Math.max(10, size / 5);
                for (int i = 0; i < size; i++) {
                    list.add((long) random.nextInt(uniquePool));
                }
                break;
            case "RANDOM":
            default:
                for (int i = 0; i < size; i++) {
                    list.add((long) i);
                }
                Collections.shuffle(list, random);
                break;
        }
        return list;
    }

    private BenchmarkResponse.ApproachMetrics pickMedianRun(List<BenchmarkResponse.ApproachMetrics> runs) {
        runs.sort(Comparator.comparingDouble(BenchmarkResponse.ApproachMetrics::getMedianTotalTimeMs));
        return runs.get(runs.size() / 2);
    }

    private long usedMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
