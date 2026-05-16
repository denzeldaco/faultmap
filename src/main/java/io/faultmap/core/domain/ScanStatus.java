package io.faultmap.core.domain;

/**
 * Status of a scan session.
 *
 * PENDING    → queued, not started yet
 * RUNNING    → currently executing scan rules
 * COMPLETED  → all rules ran successfully
 * FAILED     → an unrecoverable error stopped the scan
 * CANCELLED  → manually cancelled by user
 */
public enum ScanStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}