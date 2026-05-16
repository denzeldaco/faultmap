package io.faultmap.core;

import io.faultmap.core.domain.*;
import io.faultmap.core.domain.Module;
import io.faultmap.core.engine.*;
import io.faultmap.core.target.RepositoryContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScanService.
 *
 * We test the engine orchestration logic here — not the scan rules themselves.
 * Each scan rule has its own dedicated test class.
 */
@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock private ScanResultRepository scanResultRepository;
    @Mock private FindingRepository    findingRepository;

    private ScanService scanService;

    private static final String ORG_ID = "org-lendr-001";

    @BeforeEach
    void setUp() {
        // We'll build rules manually in each test
    }

    @Test
    @DisplayName("Scan with no applicable rules produces COMPLETED result with zero findings")
    void scan_noApplicableRules_completesWithZeroFindings() {
        // Rule that supports nothing
        ScanRule nonApplicableRule = buildRule("RULE_X", Module.AI_AUDIT, false, List.of());
        scanService = new ScanService(List.of(nonApplicableRule),
                scanResultRepository, findingRepository);

        RepositoryContent target = repoTarget();
        ScanResult result = scanService.scan(target, ORG_ID);

        assertThat(result.getStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(result.totalFindings()).isZero();
        verify(findingRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("Scan with one matching rule persists its findings")
    void scan_oneMatchingRule_persistsFindings() {
        Finding mockFinding = Finding.builder()
                .ruleId("AWS_SECRET_KEY")
                .title("AWS Secret Key detected")
                .location("config/aws.properties:14")
                .severity(Severity.CRITICAL)
                .module(Module.CORE)
                .status(FindingStatus.OPEN)
                .build();

        ScanRule rule = buildRule("AWS_SECRET_KEY", Module.CORE, true, List.of(mockFinding));
        scanService = new ScanService(List.of(rule), scanResultRepository, findingRepository);

        ScanResult result = scanService.scan(repoTarget(), ORG_ID);

        assertThat(result.getStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(result.getCriticalCount()).isEqualTo(1);
        assertThat(result.totalFindings()).isEqualTo(1);
        verify(findingRepository).saveAll(argThat(findings ->
                ((List<?>) findings).size() == 1));
    }

    @Test
    @DisplayName("A failing rule does not stop the rest of the scan")
    void scan_oneRuleFails_otherRulesStillRun() {
        ScanRule failingRule = mock(ScanRule.class);
        when(failingRule.supports(any())).thenReturn(true);
        when(failingRule.ruleId()).thenReturn("FAILING_RULE");
        when(failingRule.scan(any())).thenThrow(new RuntimeException("Simulated failure"));

        Finding goodFinding = Finding.builder()
                .ruleId("GOOD_RULE").title("Good finding")
                .location("src/main.java:1").severity(Severity.HIGH)
                .module(Module.CORE).status(FindingStatus.OPEN).build();

        ScanRule goodRule = buildRule("GOOD_RULE", Module.CORE, true, List.of(goodFinding));

        scanService = new ScanService(List.of(failingRule, goodRule),
                scanResultRepository, findingRepository);

        ScanResult result = scanService.scan(repoTarget(), ORG_ID);

        // Scan should complete despite one rule failing
        assertThat(result.getStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(result.getHighCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ScanResult is saved with correct organisation ID and module")
    void scan_resultHasCorrectOrgAndModule() {
        scanService = new ScanService(List.of(), scanResultRepository, findingRepository);

        ScanResult result = scanService.scan(repoTarget(), ORG_ID);

        assertThat(result.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(result.getModule()).isEqualTo(Module.CORE);
        assertThat(result.getTargetRef()).contains("github:");
        verify(scanResultRepository, times(2)).save(any()); // once RUNNING, once COMPLETED
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RepositoryContent repoTarget() {
        return RepositoryContent.builder()
                .repoFullName("lendrfinance/payments-service")
                .ref("main")
                .files(Map.of(
                        "src/main/resources/application.yml",
                        "spring.datasource.password=supersecret123\n"
                ))
                .build();
    }

    private ScanRule buildRule(String id, Module module,
                               boolean supports, List<Finding> findings) {
        return new ScanRule() {
            @Override public String   ruleId()               { return id; }
            @Override public Module   module()               { return module; }
            @Override public boolean  supports(ScanTarget t) { return supports; }
            @Override public List<Finding> scan(ScanTarget t){ return findings; }
        };
    }
}