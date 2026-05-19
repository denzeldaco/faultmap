package io.faultmap.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.faultmap.api.dto.ScanRequest;
import io.faultmap.api.dto.ScanResponse;
import io.faultmap.core.domain.Module;
import io.faultmap.core.domain.ScanResult;
import io.faultmap.core.domain.ScanStatus;
import io.faultmap.core.engine.ScanResultRepository;
import io.faultmap.core.engine.ScanService;
import io.faultmap.core.target.RepositoryContent;
import io.faultmap.github.GitHubFetcher;
import io.faultmap.faultmap.FaultmapApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = FaultmapApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:faultmap_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureMockMvc
@WithMockUser(username = "admin")
class ScanControllerTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;

    @MockBean private GitHubFetcher        gitHubFetcher;
    @MockBean private ScanService          scanService;
    @MockBean private ScanResultRepository scanResultRepository;

    private static final String BASE_URL    = "/api/v1/scans";
    private static final String REPO        = "acme/payments-service";
    private static final String REF         = "main";
    private static final String ORG_ID      = "org-001";
    private static final String SCAN_ID     = "SCN-ABCD1234";

    // ── POST /api/v1/scans ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST returns 202 with scanResultId and PENDING status")
    void post_validRequest_returns202WithScanResultId() throws Exception {
        stubSuccessfulFetch();
        when(scanService.submit(any(), eq(ORG_ID))).thenReturn(pendingResult());

        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(REF))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.scanResultId").value(SCAN_ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.targetRef").value("github:" + REPO + "@" + REF));
    }

    @Test
    @DisplayName("POST defaults ref to 'main' when ref is absent")
    void post_noRef_usesMainAsDefault() throws Exception {
        stubSuccessfulFetch();
        when(scanService.submit(any(), eq(ORG_ID))).thenReturn(pendingResult());

        ScanRequest withoutRef = new ScanRequest(REPO, null, ORG_ID);
        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutRef)))
                .andExpect(status().isAccepted());

        verify(gitHubFetcher).fetch(REPO, "main");
    }

    @Test
    @DisplayName("POST uses the provided ref, not always 'main'")
    void post_explicitRef_passesRefToFetcher() throws Exception {
        RepositoryContent content = repoContent("feature/payments-v2");
        when(gitHubFetcher.fetch(REPO, "feature/payments-v2")).thenReturn(content);
        when(scanService.submit(any(), eq(ORG_ID))).thenReturn(
                buildResult(SCAN_ID, "github:" + REPO + "@feature/payments-v2", ScanStatus.PENDING));

        ScanRequest req = new ScanRequest(REPO, "feature/payments-v2", ORG_ID);
        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        verify(gitHubFetcher).fetch(REPO, "feature/payments-v2");
    }

    @Test
    @DisplayName("POST passes the fetched RepositoryContent to ScanService.submit()")
    void post_validRequest_passesContentToScanService() throws Exception {
        RepositoryContent content = stubSuccessfulFetch();
        when(scanService.submit(any(), eq(ORG_ID))).thenReturn(pendingResult());

        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(REF))))
                .andExpect(status().isAccepted());

        verify(scanService).submit(eq(content), eq(ORG_ID));
    }

    @Test
    @DisplayName("POST returns 422 when GitHub fetch fails")
    void post_githubFetchFails_returns422() throws Exception {
        when(gitHubFetcher.fetch(any(), any()))
                .thenThrow(new IOException("Repository not found or access denied"));

        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(REF))))
                .andExpect(status().isUnprocessableEntity());

        verify(scanService, never()).submit(any(), any());
    }

    @Test
    @DisplayName("POST returns 400 when repoFullName is blank")
    void post_blankRepoFullName_returns400() throws Exception {
        ScanRequest bad = new ScanRequest("", REF, ORG_ID);

        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());

        verify(gitHubFetcher, never()).fetch(any(), any());
    }

    @Test
    @DisplayName("POST returns 400 when repoFullName has no slash (invalid format)")
    void post_repoFullNameWithoutSlash_returns400() throws Exception {
        ScanRequest bad = new ScanRequest("invalid-no-slash", REF, ORG_ID);

        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST returns 400 when organisationId is blank")
    void post_blankOrganisationId_returns400() throws Exception {
        ScanRequest bad = new ScanRequest(REPO, REF, "");

        mockMvc.perform(post(BASE_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/scans/{scanResultId} ──────────────────────────────────────

    @Test
    @DisplayName("GET returns 200 with scan status for a known scanResultId")
    void get_existingScan_returns200WithStatus() throws Exception {
        when(scanResultRepository.findById(SCAN_ID))
                .thenReturn(Optional.of(pendingResult()));

        mockMvc.perform(get(BASE_URL + "/" + SCAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanResultId").value(SCAN_ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.targetRef").value("github:" + REPO + "@" + REF))
                .andExpect(jsonPath("$.module").value("CORE"));
    }

    @Test
    @DisplayName("GET returns 404 for an unknown scanResultId")
    void get_unknownScan_returns404() throws Exception {
        when(scanResultRepository.findById("SCN-UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE_URL + "/SCN-UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET returns finding counts on a completed scan")
    void get_completedScan_returnsCorrectFindingCounts() throws Exception {
        ScanResult completed = ScanResult.builder()
                .scanResultId(SCAN_ID)
                .organisationId(ORG_ID)
                .module(Module.CORE)
                .targetRef("github:" + REPO + "@" + REF)
                .status(ScanStatus.COMPLETED)
                .completedAt(Instant.now())
                .criticalCount(2)
                .highCount(5)
                .mediumCount(3)
                .lowCount(1)
                .build();
        when(scanResultRepository.findById(SCAN_ID)).thenReturn(Optional.of(completed));

        mockMvc.perform(get(BASE_URL + "/" + SCAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.criticalCount").value(2))
                .andExpect(jsonPath("$.highCount").value(5))
                .andExpect(jsonPath("$.mediumCount").value(3))
                .andExpect(jsonPath("$.lowCount").value(1))
                .andExpect(jsonPath("$.totalFindings").value(11));
    }

    @Test
    @DisplayName("GET returns errorMessage on a failed scan")
    void get_failedScan_includesErrorMessage() throws Exception {
        ScanResult failed = ScanResult.builder()
                .scanResultId(SCAN_ID)
                .organisationId(ORG_ID)
                .module(Module.CORE)
                .targetRef("github:" + REPO + "@" + REF)
                .status(ScanStatus.FAILED)
                .completedAt(Instant.now())
                .errorMessage("Rule execution timed out")
                .build();
        when(scanResultRepository.findById(SCAN_ID)).thenReturn(Optional.of(failed));

        mockMvc.perform(get(BASE_URL + "/" + SCAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Rule execution timed out"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScanRequest validRequest(String ref) {
        return new ScanRequest(REPO, ref, ORG_ID);
    }

    private RepositoryContent repoContent(String ref) {
        return RepositoryContent.builder()
                .repoFullName(REPO)
                .ref(ref)
                .files(Map.of("src/App.java", "public class App {}"))
                .build();
    }

    private RepositoryContent stubSuccessfulFetch() throws IOException {
        RepositoryContent content = repoContent(REF);
        when(gitHubFetcher.fetch(REPO, REF)).thenReturn(content);
        return content;
    }

    private ScanResult pendingResult() {
        return buildResult(SCAN_ID, "github:" + REPO + "@" + REF, ScanStatus.PENDING);
    }

    private ScanResult buildResult(String id, String targetRef, ScanStatus status) {
        return ScanResult.builder()
                .scanResultId(id)
                .organisationId(ORG_ID)
                .module(Module.CORE)
                .targetRef(targetRef)
                .status(status)
                .build();
    }
}
