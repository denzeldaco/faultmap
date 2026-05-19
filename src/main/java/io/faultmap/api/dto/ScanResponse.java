package io.faultmap.api.dto;

import io.faultmap.core.domain.ScanResult;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * API response for both POST /api/v1/scans (202 Accepted) and
 * GET /api/v1/scans/{scanResultId} (200 OK).
 *
 * <p>On 202: status will be PENDING, finding counts will be zero.
 * <p>On 200: status reflects the current state; counts are populated once COMPLETED.
 */
@Getter
@Builder
public class ScanResponse {

    private final String  scanResultId;
    private final String  status;
    private final String  targetRef;
    private final String  module;
    private final Instant startedAt;
    private final Instant completedAt;
    private final int     criticalCount;
    private final int     highCount;
    private final int     mediumCount;
    private final int     lowCount;
    private final int     totalFindings;
    private final String  errorMessage;

    public static ScanResponse from(ScanResult result) {
        return ScanResponse.builder()
                .scanResultId(result.getScanResultId())
                .status(result.getStatus().name())
                .targetRef(result.getTargetRef())
                .module(result.getModule().name())
                .startedAt(result.getStartedAt())
                .completedAt(result.getCompletedAt())
                .criticalCount(result.getCriticalCount())
                .highCount(result.getHighCount())
                .mediumCount(result.getMediumCount())
                .lowCount(result.getLowCount())
                .totalFindings(result.totalFindings())
                .errorMessage(result.getErrorMessage())
                .build();
    }
}
