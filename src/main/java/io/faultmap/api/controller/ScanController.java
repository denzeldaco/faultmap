package io.faultmap.api.controller;

import io.faultmap.api.dto.ScanRequest;
import io.faultmap.api.dto.ScanResponse;
import io.faultmap.core.domain.ScanResult;
import io.faultmap.core.engine.ScanResultRepository;
import io.faultmap.core.engine.ScanService;
import io.faultmap.core.target.RepositoryContent;
import io.faultmap.github.GitHubFetcher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * REST controller for the Core scanner.
 *
 * <pre>
 *   POST /api/v1/scans              — start a scan, returns 202 + scanResultId immediately
 *   GET  /api/v1/scans/{id}         — poll scan status and finding counts
 * </pre>
 *
 * Flow for POST:
 * 1. Validate the request.
 * 2. Call GitHubFetcher to build a RepositoryContent (GitHub API calls happen here).
 * 3. Call ScanService.submit() — persists a PENDING record, fires scan asynchronously.
 * 4. Return 202 Accepted with the scanResultId so the client can poll.
 *
 * Note: step 2 runs on the HTTP thread. For large repos the fetch may take a few seconds.
 * A future improvement could move the fetch into the background task as well.
 */
@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
public class ScanController {

    private final GitHubFetcher       gitHubFetcher;
    private final ScanService         scanService;
    private final ScanResultRepository scanResultRepository;

    // ── POST /api/v1/scans ────────────────────────────────────────────────────

    /**
     * Start a new scan. Returns 202 Accepted with the scanResultId immediately.
     * The scan runs in the background — use GET /api/v1/scans/{id} to poll for results.
     *
     * @throws ResponseStatusException 422 if the GitHub repository cannot be fetched
     * @throws ResponseStatusException 400 if the request body fails validation
     */
    @PostMapping
    public ResponseEntity<ScanResponse> startScan(@Valid @RequestBody ScanRequest request) {
        String ref = StringUtils.hasText(request.ref()) ? request.ref() : "main";

        log.info("[ScanController] Scan requested | repo={} ref={} org={}",
                request.repoFullName(), ref, request.organisationId());

        RepositoryContent content;
        try {
            content = gitHubFetcher.fetch(request.repoFullName(), ref);
        } catch (IOException e) {
            log.warn("[ScanController] GitHub fetch failed | repo={} ref={} error={}",
                    request.repoFullName(), ref, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not fetch repository '" + request.repoFullName() + "': " + e.getMessage());
        }

        ScanResult pending = scanService.submit(content, request.organisationId());
        return ResponseEntity.accepted().body(ScanResponse.from(pending));
    }

    // ── GET /api/v1/scans/{scanResultId} ──────────────────────────────────────

    /**
     * Poll the status and finding counts for a previous scan.
     *
     * @return 200 with the ScanResponse, or 404 if the scanResultId is not found
     */
    @GetMapping("/{scanResultId}")
    public ResponseEntity<ScanResponse> getScan(@PathVariable String scanResultId) {
        return scanResultRepository.findById(scanResultId)
                .map(result -> ResponseEntity.ok(ScanResponse.from(result)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Scan not found: " + scanResultId));
    }
}
