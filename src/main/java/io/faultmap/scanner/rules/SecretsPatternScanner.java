package io.faultmap.scanner.rules;

import io.faultmap.config.PatternRegistry;
import io.faultmap.core.domain.*;
import io.faultmap.core.domain.Module;
import io.faultmap.core.engine.ScanRule;
import io.faultmap.core.engine.ScanTarget;
import io.faultmap.core.target.RepositoryContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Core scanner rule: detects secrets and sensitive credentials in repo files.
 *
 * Driven entirely by the patterns defined in secrets.yml — adding a new
 * secret type requires only a new YAML entry, not a new class.
 *
 * Scans every text file in the repository against every SECRETS-category
 * pattern. For each match, produces a Finding with:
 * - The exact file path and line number
 * - A redacted excerpt of the matched line (never stores the actual secret)
 * - The remediation steps for that specific secret type
 * - All applicable NDPC / CBN / PCI-DSS regulatory references
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecretsPatternScanner implements ScanRule {

    private final PatternRegistry patternRegistry;

    @Override
    public String ruleId() {
        return "SECRETS_PATTERN_SCANNER";
    }

    @Override
    public String displayName() {
        return "Secrets & Credential Pattern Scanner";
    }

    @Override
    public Module module() {
        return Module.CORE;
    }

    @Override
    public boolean supports(ScanTarget target) {
        return target instanceof RepositoryContent;
    }

    @Override
    public List<Finding> scan(ScanTarget target) {
        RepositoryContent repo = (RepositoryContent) target;
        List<Finding> findings = new ArrayList<>();

        // Only run patterns in the SECRETS category
        List<PatternRegistry.PatternEntry> secretPatterns =
                patternRegistry.getByCategory("SECRETS");

        for (Map.Entry<String, String> fileEntry : repo.getFiles().entrySet()) {
            String filePath    = fileEntry.getKey();
            String fileContent = fileEntry.getValue();

            // Skip files unlikely to contain secrets
            if (shouldSkipFile(filePath)) continue;

            String[] lines = fileContent.split("\n");

            for (PatternRegistry.PatternEntry pattern : secretPatterns) {
                Matcher matcher = pattern.getPattern().matcher(fileContent);

                while (matcher.find()) {
                    int lineNumber = getLineNumber(fileContent, matcher.start());
                    String matchedLine = lineNumber <= lines.length
                            ? lines[lineNumber - 1].strip()
                            : "";

                    findings.add(buildFinding(pattern, filePath, lineNumber,
                            matchedLine, repo.getRepoFullName()));
                }
            }
        }

        log.debug("[SecretsPatternScanner] Scanned {} files in {}, found {} secrets",
                repo.getFiles().size(), repo.getRepoFullName(), findings.size());

        return findings;
    }

    private Finding buildFinding(PatternRegistry.PatternEntry pattern,
                                 String filePath,
                                 int lineNumber,
                                 String matchedLine,
                                 String repoName) {
        String location  = filePath + ":" + lineNumber;
        String redacted  = redactSecret(matchedLine);

        return Finding.builder()
                .module(Module.CORE)
                .ruleId(pattern.getId())
                .title(pattern.getName() + " detected")
                .location(location)
                .description(pattern.getDescription().strip())
                .severity(pattern.getSeverity())
                .remediation(buildRemediation(pattern, filePath))
                .regulatoryRefs(pattern.getRegulatoryRefs())
                .rawEvidence("Repo: " + repoName + " | Line: " + redacted)
                .status(FindingStatus.OPEN)
                .build();
    }

    private String buildRemediation(PatternRegistry.PatternEntry pattern, String filePath) {
        return """
                **Immediate actions:**
                1. Revoke and rotate the exposed credential immediately — assume it has been compromised.
                2. Check audit logs for any unauthorised access using this credential from the date it was first committed.
                3. Remove the secret from `%s` and all previous Git commits (use `git filter-branch` or BFG Repo Cleaner).
                4. Store secrets using environment variables injected at runtime, or a secrets manager (AWS Secrets Manager, HashiCorp Vault).
                5. Add this file pattern to `.gitignore` to prevent recurrence.
                6. Consider enabling GitHub secret scanning alerts on your repository.
                """.formatted(filePath);
    }

    /**
     * Replace the value portion of a matched line with redaction markers.
     * We never store the actual secret value in the database.
     */
    private String redactSecret(String line) {
        if (line.length() <= 10) return "[REDACTED]";
        // Show the key name but not the value
        int equalsIdx = line.indexOf('=');
        int colonIdx  = line.indexOf(':');
        int splitAt   = Math.max(equalsIdx, colonIdx);

        if (splitAt > 0 && splitAt < line.length() - 1) {
            return line.substring(0, splitAt + 1) + " [REDACTED]";
        }
        return "[REDACTED]";
    }

    private boolean shouldSkipFile(String filePath) {
        String lower = filePath.toLowerCase();
        // Skip test fixtures, compiled outputs, documentation
        return lower.contains("/test/resources/")
                || lower.contains("__snapshots__")
                || lower.endsWith(".md")
                || lower.endsWith(".lock")
                || lower.endsWith(".sum");
    }

    private int getLineNumber(String content, int charIndex) {
        int line = 1;
        for (int i = 0; i < charIndex && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }
}