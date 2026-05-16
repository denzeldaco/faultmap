package io.faultmap.core.target;

import io.faultmap.core.domain.Module;
import io.faultmap.core.engine.ScanTarget;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * ScanTarget implementation for the Core scanner.
 *
 * Represents the content of a GitHub repository at a point in time.
 * The GitHub integration fetches file contents and populates this object.
 * The Core scanner rules then work purely against this — no GitHub API
 * calls happen inside any ScanRule.
 *
 * files: map of { "relative/path/to/file.ext" → "file contents as string" }
 *
 * Only text files are included. Binary files (images, compiled artifacts)
 * are excluded by the GitHubFetcher before this object is constructed.
 */
@Getter
@Builder
public class RepositoryContent implements ScanTarget {

    /** e.g. "lendrfinance/payments-service" */
    private final String repoFullName;

    /** The branch or commit SHA that was scanned. */
    private final String ref;

    /**
     * All text file contents in the repo.
     * Key:   relative file path  e.g. "src/main/resources/application.yml"
     * Value: full file content as a string
     */
    private final Map<String, String> files;

    @Override
    public Module getModule() {
        return Module.CORE;
    }

    @Override
    public String getTargetRef() {
        return "github:" + repoFullName + "@" + ref;
    }
}