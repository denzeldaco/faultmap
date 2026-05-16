package io.faultmap.scanner;

import io.faultmap.config.PatternRegistry;
import io.faultmap.core.domain.Module;
import io.faultmap.core.domain.Severity;
import io.faultmap.core.domain.Finding;
import io.faultmap.core.target.RepositoryContent;
import io.faultmap.scanner.rules.SecretsPatternScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SecretsPatternScanner.
 *
 * Uses the real PatternRegistry loaded from secrets.yml — this ensures
 * changes to the pattern file are caught immediately in tests.
 */
class SecretsPatternScannerTest {

    private SecretsPatternScanner scanner;

    @BeforeEach
    void setUp() {
        PatternRegistry registry = new PatternRegistry(new DefaultResourceLoader());
        registry.load();
        scanner = new SecretsPatternScanner(registry);
    }

    @Test
    @DisplayName("Detects AWS secret key in a config file")
    void detectsAwsSecretKey() {
        RepositoryContent repo = repoWithFile(
                "services/payments/config/aws.properties",
                "aws_secret_access_key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\n"
        );

        List<Finding> findings = scanner.scan(repo);

        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f ->
                f.getRuleId().equals("AWS_SECRET_KEY")
                        && f.getSeverity() == Severity.CRITICAL
                        && f.getLocation().contains("aws.properties")
                        && f.getModule() == Module.CORE
        );
    }

    @Test
    @DisplayName("Detects database password in application.properties")
    void detectsDatabasePassword() {
        RepositoryContent repo = repoWithFile(
                "src/main/resources/application.properties",
                "server.port=8080\nspring.datasource.password=MyS3cr3tPass!\nspring.datasource.url=jdbc:postgresql://localhost/faultmap\n"
        );

        List<Finding> findings = scanner.scan(repo);

        assertThat(findings).anyMatch(f ->
                f.getRuleId().equals("DATABASE_PASSWORD")
                        && f.getSeverity() == Severity.CRITICAL
        );
    }

    @Test
    @DisplayName("Detects JWT secret in .env file")
    void detectsJwtSecret() {
        RepositoryContent repo = repoWithFile(
                ".env",
                "JWT_SECRET=myultrasecretjwtkey12345\nPORT=8080\n"
        );

        List<Finding> findings = scanner.scan(repo);

        assertThat(findings).anyMatch(f -> f.getRuleId().equals("JWT_SECRET"));
    }

    @Test
    @DisplayName("Clean file with no secrets produces no findings")
    void cleanFileProducesNoFindings() {
        RepositoryContent repo = repoWithFile(
                "src/main/java/io/faultmap/HelloService.java",
                "package io.faultmap;\npublic class HelloService {}\n"
        );

        List<Finding> findings = scanner.scan(repo);

        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("Findings include at least one regulatory reference")
    void findingsHaveRegulatoryReferences() {
        RepositoryContent repo = repoWithFile(
                "config.properties",
                "aws_access_key_id=AKIAIOSFODNN7EXAMPLE\n"
        );

        List<Finding> findings = scanner.scan(repo);

        assertThat(findings).isNotEmpty();
        findings.forEach(f ->
                assertThat(f.getRegulatoryRefs())
                        .as("Finding %s must have regulatory references", f.getRuleId())
                        .isNotEmpty()
        );
    }

    @Test
    @DisplayName("Finding location includes file path and line number")
    void findingLocationIncludesLineNumber() {
        RepositoryContent repo = repoWithFile(
                "services/auth/.env",
                "PORT=8080\nHOST=localhost\nJWT_SECRET=supersecretkey99999\nDEBUG=false\n"
        );

        List<Finding> findings = scanner.scan(repo);

        assertThat(findings).isNotEmpty();
        assertThat(findings.get(0).getLocation())
                .contains("services/auth/.env")
                .contains(":"); // has line number
    }

    @Test
    @DisplayName("Raw evidence is redacted — does not contain the actual secret value")
    void rawEvidenceIsRedacted() {
        String actualSecret = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
        RepositoryContent repo = repoWithFile(
                "aws.properties",
                "aws_secret_access_key=" + actualSecret + "\n"
        );

        List<Finding> findings = scanner.scan(repo);

        assertThat(findings).isNotEmpty();
        findings.forEach(f ->
                assertThat(f.getRawEvidence())
                        .as("Raw evidence must not contain the actual secret")
                        .doesNotContain(actualSecret)
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RepositoryContent repoWithFile(String path, String content) {
        return RepositoryContent.builder()
                .repoFullName("lendrfinance/test-repo")
                .ref("main")
                .files(Map.of(path, content))
                .build();
    }
}