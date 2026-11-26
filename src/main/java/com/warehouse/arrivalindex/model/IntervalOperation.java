package com.warehouse.arrivalindex.model;

/**
 * Classifies the TreeMap decision taken when evaluating a package arrival.
 */
public enum IntervalOperation {
    NEW_INTERVAL,
    LEFT_EXTENSION,
    RIGHT_EXTENSION,
    BRIDGE_MERGE,
    DUPLICATE_DETECTED
}
