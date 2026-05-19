package io.faultmap.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/v1/scans.
 *
 * @param repoFullName  owner/repo, e.g. "lendrfinance/payments-service"
 * @param ref           branch, tag, or commit SHA — defaults to "main" when omitted
 * @param organisationId  the Faultmap organisation that owns this scan
 */
public record ScanRequest(

        @NotBlank(message = "repoFullName is required")
        @Pattern(regexp = ".+/.+", message = "repoFullName must be in 'owner/repo' format")
        String repoFullName,

        String ref,

        @NotBlank(message = "organisationId is required")
        String organisationId
) {}
