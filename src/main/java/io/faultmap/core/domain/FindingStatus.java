package io.faultmap.core.domain;

/**
 * Lifecycle status of a Finding.
 *
 * OPEN        → newly detected, no action taken yet
 * IN_PROGRESS → engineer has acknowledged, fix in progress (PR open)
 * RESOLVED    → fix confirmed — re-scan passed, finding no longer detected
 * SUPPRESSED  → deliberately accepted risk, documented reason required
 */
public enum FindingStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    SUPPRESSED
}