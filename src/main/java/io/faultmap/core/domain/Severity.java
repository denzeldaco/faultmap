package io.faultmap.core.domain;

/**
 * Severity levels for a Finding.
 *
 * Ordering matters — CRITICAL > HIGH > MEDIUM > LOW > INFO.
 * Used for prioritisation in the dashboard and compliance report.
 */
public enum Severity {

    /**
     * Immediate action required. Likely active risk of breach or regulatory penalty.
     * Example: AWS secret key in a public GitHub repository.
     */
    CRITICAL,

    /**
     * Urgent — resolve within 48 hours.
     * Example: debug mode enabled in production config.
     */
    HIGH,

    /**
     * Resolve within the current sprint.
     * Example: dependency with a known CVE but no public exploit yet.
     */
    MEDIUM,

    /**
     * Best-practice violation with low immediate risk.
     * Example: verbose error messages in API responses.
     */
    LOW,

    /**
     * Informational — no action required, logged for awareness.
     */
    INFO
}