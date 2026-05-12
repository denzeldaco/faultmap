package io.faultmap.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Finding is the atomic output of a ScanRule.
 *
 * Every module (Core scanner, AI Audit, PQC, Dependencies, Smart Contracts)
 * produces Findings. They all share this model — which is what allows the
 * shared compliance engine to aggregate across modules into one report.
 *
 * Key design decisions:
 * - findingId is a UUID, not a DB sequence, so it can be generated before persist
 * - location is a human-readable string (file path, endpoint URL, agent session ID)
 * - regulatoryRefs is embedded — no join table, keeps the query simple
 * - remediation is markdown — rendered in the dashboard and PDF report
 * - status tracks the lifecycle: OPEN → IN_PROGRESS → RESOLVED → SUPPRESSED
 */
@Entity
@Table(name = "findings",
       indexes = {
           @Index(name = "idx_findings_scan_result", columnList = "scan_result_id"),
           @Index(name = "idx_findings_severity",    columnList = "severity"),
           @Index(name = "idx_findings_status",      columnList = "status"),
           @Index(name = "idx_findings_module",      columnList = "module")
       })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Finding {

    @Id
    @Column(name = "finding_id", updatable = false, nullable = false)
    private String findingId;

    /** Which Faultmap module produced this finding. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Module module;

    /** The rule that fired. e.g. "AWS_SECRET_KEY", "BVN_IN_PROMPT" */
    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    /** Human-readable title shown in the dashboard. */
    @Column(nullable = false)
    private String title;

    /**
     * Where the issue was found.
     * For Core: "services/payments/config/aws.properties:14"
     * For AI Audit: "LoanAssistAgent · session 8823"
     * For PQC: "api.lendrfinance.com (TLS endpoint)"
     */
    @Column(nullable = false)
    private String location;

    /** Full description of the finding — what it is, why it's dangerous. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    /** Step-by-step remediation in markdown. Shown in dashboard + PDF. */
    @Column(columnDefinition = "TEXT")
    private String remediation;

    /**
     * The regulatory obligations this finding violates.
     * Multiple regulations can apply to a single finding.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "finding_regulatory_refs",
        joinColumns = @JoinColumn(name = "finding_id")
    )
    @Builder.Default
    private List<RegulatoryReference> regulatoryRefs = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FindingStatus status = FindingStatus.OPEN;

    /**
     * Raw evidence — the exact line of code, the exact prompt payload,
     * the exact certificate details. Stored but not shown by default
     * in the UI — requires explicit "view evidence" action for security.
     */
    @Column(name = "raw_evidence", columnDefinition = "TEXT")
    private String rawEvidence;

    @CreationTimestamp
    @Column(name = "detected_at", updatable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Which scan session produced this finding. */
    @Column(name = "scan_result_id")
    private String scanResultId;

    @PrePersist
    public void assignId() {
        if (this.findingId == null) {
            this.findingId = "FND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
