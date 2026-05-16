package io.faultmap.core.engine;

import io.faultmap.core.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Faultmap scan engine orchestrator.
 *
 * Responsibilities:
 * 1. Accept a ScanTarget
 * 2. Find all ScanRules that support that target
 * 3. Execute each rule and collect Findings
 * 4. Persist the ScanResult and all Findings
 * 5. Return the completed ScanResult
 *
 * The engine is module-agnostic — it doesn't know or care whether
 * it's scanning a GitHub repo or an AI agent session. The ScanRules
 * declare what they support via supports(target).
 *
 * Scans run asynchronously so the API can return a scan ID immediately
 * and the client can poll for results — important for large repos.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScanService {

    private final List<ScanRule>         allRules;
    private final ScanResultRepository   scanResultRepository;
    private final FindingRepository      findingRepository;

    /**
     * Trigger a scan asynchronously.
     *
     * @param target         The thing to scan
     * @param organisationId Which organisation owns this scan
     * @return A future resolving to the completed ScanResult
     */
    @Async
    public CompletableFuture<ScanResult> scanAsync(ScanTarget target, String organisationId) {
        return CompletableFuture.completedFuture(scan(target, organisationId));
    }

    /**
     * Trigger a scan synchronously (used in tests and scheduled jobs).
     */
    public ScanResult scan(ScanTarget target, String organisationId) {

        // 1. Create and persist a ScanResult in RUNNING state
        ScanResult result = ScanResult.builder()
                .organisationId(organisationId)
                .module(target.getModule())
                .targetRef(target.getTargetRef())
                .status(ScanStatus.RUNNING)
                .build();
        scanResultRepository.save(result);

        log.info("[Faultmap] Scan started | id={} org={} module={} target={}",
                result.getScanResultId(), organisationId,
                target.getModule(), target.getTargetRef());

        List<Finding> allFindings = new ArrayList<>();

        try {
            // 2. Find applicable rules and run each one
            List<ScanRule> applicableRules = allRules.stream()
                    .filter(rule -> rule.supports(target))
                    .toList();

            log.info("[Faultmap] Running {} rules for target={}",
                    applicableRules.size(), target.getTargetRef());

            for (ScanRule rule : applicableRules) {
                try {
                    List<Finding> ruleFindings = rule.scan(target);

                    // Attach this scan's result ID to every finding
                    ruleFindings.forEach(f -> f.setScanResultId(result.getScanResultId()));
                    allFindings.addAll(ruleFindings);

                    log.debug("[Faultmap] Rule {} produced {} findings",
                            rule.ruleId(), ruleFindings.size());

                } catch (Exception e) {
                    // A rule failure must not stop the rest of the scan
                    log.error("[Faultmap] Rule {} failed for target={}: {}",
                            rule.ruleId(), target.getTargetRef(), e.getMessage(), e);
                }
            }

            // 3. Persist all findings
            findingRepository.saveAll(allFindings);

            // 4. Update ScanResult with counts and COMPLETED status
            result.setStatus(ScanStatus.COMPLETED);
            result.setCompletedAt(Instant.now());
            result.setCriticalCount(countBySeverity(allFindings, Severity.CRITICAL));
            result.setHighCount(countBySeverity(allFindings, Severity.HIGH));
            result.setMediumCount(countBySeverity(allFindings, Severity.MEDIUM));
            result.setLowCount(countBySeverity(allFindings, Severity.LOW));

            log.info("[Faultmap] Scan completed | id={} findings={} critical={}",
                    result.getScanResultId(), allFindings.size(), result.getCriticalCount());

        } catch (Exception e) {
            result.setStatus(ScanStatus.FAILED);
            result.setCompletedAt(Instant.now());
            result.setErrorMessage(e.getMessage());
            log.error("[Faultmap] Scan failed | id={} error={}",
                    result.getScanResultId(), e.getMessage(), e);
        }

        scanResultRepository.save(result);
        return result;
    }

    private int countBySeverity(List<Finding> findings, Severity severity) {
        return (int) findings.stream()
                .filter(f -> f.getSeverity() == severity)
                .count();
    }
}