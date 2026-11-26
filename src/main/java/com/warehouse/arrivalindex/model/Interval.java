package com.warehouse.arrivalindex.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * Represents a consecutive, non-overlapping range of package IDs [start, end] inclusive.
 */
@Schema(description = "Represents a consecutive range of received package IDs [start, end] inclusive")
public class Interval implements Comparable<Interval> {

    @Schema(description = "Start package ID of the interval (inclusive)", example = "1")
    private long start;

    @Schema(description = "End package ID of the interval (inclusive)", example = "3")
    private long end;

    public Interval() {
    }

    public Interval(long start, long end) {
        if (start > end) {
            throw new IllegalArgumentException("Interval start (" + start + ") cannot be greater than end (" + end + ")");
        }
        this.start = start;
        this.end = end;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    /**
     * Calculates the number of discrete package IDs in this interval.
     */
    public long size() {
        return end - start + 1;
    }

    /**
     * Checks if this interval contains the given package ID.
     */
    public boolean contains(long packageId) {
        return packageId >= start && packageId <= end;
    }

    @Override
    public int compareTo(Interval other) {
        return Long.compare(this.start, other.start);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Interval interval = (Interval) o;
        return start == interval.start && end == interval.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + "]";
    }
}
