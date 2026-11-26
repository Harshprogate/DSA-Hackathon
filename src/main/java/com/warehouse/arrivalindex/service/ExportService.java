package com.warehouse.arrivalindex.service;

import com.warehouse.arrivalindex.dto.AnalyticsResponse;
import com.warehouse.arrivalindex.dto.AuditEntry;
import com.warehouse.arrivalindex.model.Interval;
import com.warehouse.arrivalindex.model.StatisticsResponse;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates CSV, JSON, and PDF reports directly from live backend state.
 */
@Service
public class ExportService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PackageIndexService packageIndexService;

    public ExportService(PackageIndexService packageIndexService) {
        this.packageIndexService = packageIndexService;
    }

    public String exportIntervalsCsv() {
        List<Interval> intervals = packageIndexService.getAllIntervals().getIntervals();
        StringBuilder sb = new StringBuilder();
        sb.append("# Live Arrival Index - Active Intervals Export\n");
        sb.append("# Generated: ").append(LocalDateTime.now().format(TS_FMT)).append("\n");
        sb.append("start,end,package_count\n");
        for (Interval iv : intervals) {
            sb.append(iv.getStart()).append(',').append(iv.getEnd()).append(',').append(iv.size()).append('\n');
        }
        return sb.toString();
    }

    public String exportMissingCsv(long start, long end) {
        List<Interval> missing = packageIndexService.getMissingIntervals(start, end).getMissingIntervals();
        StringBuilder sb = new StringBuilder();
        sb.append("# Live Arrival Index - Missing Intervals Export\n");
        sb.append("# Range: [").append(start).append(", ").append(end).append("]\n");
        sb.append("# Generated: ").append(LocalDateTime.now().format(TS_FMT)).append("\n");
        sb.append("missing_start,missing_end,missing_count\n");
        for (Interval iv : missing) {
            sb.append(iv.getStart()).append(',').append(iv.getEnd()).append(',').append(iv.size()).append('\n');
        }
        return sb.toString();
    }

    public String exportActivityCsv() {
        List<AuditEntry> logs = packageIndexService.getAuditLog("ALL");
        StringBuilder sb = new StringBuilder();
        sb.append("# Live Arrival Index - Arrival Activity Audit Export\n");
        sb.append("# Generated: ").append(LocalDateTime.now().format(TS_FMT)).append("\n");
        sb.append("id,timestamp,package_id,operation_type,status,intervals_before,intervals_after,duration_ns,message\n");
        for (AuditEntry e : logs) {
            sb.append(e.getId()).append(',')
              .append(e.getTimestamp()).append(',')
              .append(e.getPackageId() != null ? e.getPackageId() : "").append(',')
              .append(e.getOperationType()).append(',')
              .append(e.getStatus()).append(',')
              .append(e.getIntervalsBeforeCount()).append(',')
              .append(e.getIntervalsAfterCount()).append(',')
              .append(e.getDurationNanos()).append(',')
              .append('"').append(escapeCsv(e.getMessage())).append('"').append('\n');
        }
        return sb.toString();
    }

    public Map<String, Object> exportCompleteJson() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("generatedAt", LocalDateTime.now().format(TS_FMT));
        doc.put("statistics", packageIndexService.getStatistics());
        doc.put("intervals", packageIndexService.getAllIntervals().getIntervals());
        return doc;
    }

    public byte[] exportCompletenessPdf(long start, long end) {
        AnalyticsResponse analytics = packageIndexService.getAnalytics(start, end);
        StatisticsResponse stats = packageIndexService.getStatistics();

        List<String> lines = new ArrayList<>();
        lines.add("LIVE ARRIVAL INDEX - WAREHOUSE COMPLETENESS REPORT");
        lines.add("Generated: " + LocalDateTime.now().format(TS_FMT));
        lines.add("----------------------------------------------------------------------");
        lines.add("Bounded Analysis Range: [" + start + ", " + end + "]");
        lines.add("Expected Packages: " + analytics.getExpectedCount());
        lines.add("Received Packages: " + analytics.getReceivedCount());
        lines.add("Missing Packages: " + analytics.getMissingCount());
        lines.add("Completion Percentage: " + analytics.getCompletionPercentage() + "%");
        lines.add("Received Intervals in Range: " + analytics.getReceivedIntervalCount());
        lines.add("Missing Intervals in Range: " + analytics.getMissingIntervalCount());
        lines.add("Largest Missing Range: " + (analytics.getLargestMissingRange() != null ? analytics.getLargestMissingRange() : "None"));
        lines.add("Smallest Missing Range: " + (analytics.getSmallestMissingRange() != null ? analytics.getSmallestMissingRange() : "None"));
        lines.add("----------------------------------------------------------------------");
        lines.add("Global Index Statistics:");
        lines.add("  Total Unique Packages: " + stats.getTotalUniquePackages());
        lines.add("  Active Disjoint Intervals: " + stats.getIntervalCount());
        lines.add("  Bridge Merges: " + stats.getBridgeMergeCount() + " | Extensions: " + stats.getExtensionCount());
        lines.add("  Duplicates: " + stats.getDuplicateCount() + " (" + stats.getDuplicatePercentage() + "%)");
        lines.add("----------------------------------------------------------------------");
        lines.add("Missing Intervals (up to first 20 shown):");

        List<Interval> missing = analytics.getMissingIntervals();
        if (missing.isEmpty()) {
            lines.add("  None - 100% range completeness achieved.");
        } else {
            int limit = Math.min(20, missing.size());
            for (int i = 0; i < limit; i++) {
                Interval iv = missing.get(i);
                lines.add("  " + (i + 1) + ". [" + iv.getStart() + ", " + iv.getEnd() + "] (" + iv.size() + " packages)");
            }
            if (missing.size() > limit) {
                lines.add("  ... and " + (missing.size() - limit) + " more missing intervals.");
            }
        }

        return buildSimplePdf(lines);
    }

    private byte[] buildSimplePdf(List<String> lines) {
        // Minimal valid PDF 1.4 single-page text document
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 10 Tf\n50 760 Td\n14 TL\n");
        for (String line : lines) {
            stream.append("(").append(escapePdfText(line)).append(") Tj T*\n");
        }
        stream.append("ET");
        byte[] streamBytes = stream.toString().getBytes(StandardCharsets.US_ASCII);

        List<String> objects = new ArrayList<>();
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        objects.add("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        objects.add("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n");
        objects.add("4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n" + new String(streamBytes, StandardCharsets.US_ASCII) + "\nendstream\nendobj\n");
        objects.add("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (String obj : objects) {
            offsets.add(out.size());
            writeAscii(out, obj);
        }
        int xrefPos = out.size();
        writeAscii(out, "xref\n0 " + (objects.size() + 1) + "\n");
        writeAscii(out, "0000000000 65535 f \n");
        for (int off : offsets) {
            writeAscii(out, String.format("%010d 00000 n \n", off));
        }
        writeAscii(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n");
        writeAscii(out, "startxref\n" + xrefPos + "\n%%EOF");
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private String escapePdfText(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String escapeCsv(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
    }
}
