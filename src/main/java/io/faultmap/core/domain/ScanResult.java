package io.faultmap.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A ScanResult represents one complete scan session.
 *
 * A customer triggers a scan (manually or on push). Faultmap creates a
 * ScanResult, runs all applicable ScanRules, and attaches all produced
 * Findings to this result via scanResultId.
 *
 * The ScanResult is what the dashboard's "Last scan: 2h ago" refers to.
 * The compliance report is generated from the most recent ScanResult
 * per module, or across a rolling time window.
 */
@Entity
@Table(name = "scan_results",
        indexes = {
                @Index(name = "idx_scan_results_org",    columnList = "organisation_id"),
                @Index(name = "idx_scan_results_status", columnList = "status"),
                @Index(name = "idx_scan_results_module", columnList = "module")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResult {

    @Id
    @Column(name = "scan_result_id", updatable = false, nullable = false)
    private String scanResultId;

    /** Which organisation triggered this scan. */
    @Column(name = "organisation_id", nullable = false)
    private String organisationId;

    /** Which module ran. A full platform scan creates one ScanResult per module. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Module module;

    /**
     * What was scanned.
     * For Core: "github:lendrfinance/payments-service"
     * For PQC: "tls:api.lendrfinance.com"
     */
    @Column(name = "target_ref", nullable = false)
    private String targetRef;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ScanStatus status = ScanStatus.PENDING;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Total files / endpoints / sessions scanned. */
    @Column(name = "items_scanned")
    private int itemsScanned;

    @Column(name = "critical_count") private int criticalCount;
    @Column(name = "high_count")     private int highCount;
    @Column(name = "medium_count")   private int mediumCount;
    @Column(name = "low_count")      private int lowCount;

    /** Error message if the scan failed. */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    public void assignId() {
        if (this.scanResultId == null) {
            this.scanResultId = "SCN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    /** Convenience — total findings across all severities. */
    public int totalFindings() {
        return criticalCount + highCount + mediumCount + lowCount;
    }
}