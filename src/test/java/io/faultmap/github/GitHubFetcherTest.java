package io.faultmap.github;

import io.faultmap.config.GitHubProperties;
import io.faultmap.config.ScanProperties;
import io.faultmap.core.target.RepositoryContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GitHubFetcher.
 *
 * The protected connectToGitHub() method is overridden in a test subclass
 * to inject a mock GitHub client, keeping the tests fast and offline.
 */
@ExtendWith(MockitoExtension.class)
class GitHubFetcherTest {

    @Mock private GitHub        mockGitHub;
    @Mock private GHRepository  mockRepo;
    @Mock private GHTree        mockTree;

    private ScanProperties   scanProperties;
    private GitHubFetcher    fetcher;

    private static final String REPO     = "acme/payments-service";
    private static final String REF      = "main";

    @BeforeEach
    void setUp() throws IOException {
        scanProperties = new ScanProperties();
        scanProperties.setMaxFileSizeBytes(524_288);
        scanProperties.setIncludeExtensions(List.of(".java", ".yml", ".properties", ".env"));
        scanProperties.setExcludePaths(List.of("node_modules/", ".git/", "target/", "build/"));

        // Override connectToGitHub so tests never hit the network
        fetcher = new GitHubFetcher(new GitHubProperties(), scanProperties) {
            @Override
            protected GitHub connectToGitHub(String repoFullName) {
                return mockGitHub;
            }
        };

        when(mockGitHub.getRepository(REPO)).thenReturn(mockRepo);
        when(mockRepo.getTreeRecursive(REF, 1)).thenReturn(mockTree);
        when(mockTree.isTruncated()).thenReturn(false);
    }

    // ── Filtering: extensions ─────────────────────────────────────────────────

    @Test
    @DisplayName("Files with included extensions are fetched")
    void fetch_includedExtension_fileIsIncluded() throws IOException {
        GHTreeEntry javaEntry = blobEntry("src/main/App.java", 100);
        when(mockTree.getTree()).thenReturn(List.of(javaEntry));
        stubFileContent("src/main/App.java", "public class App {}");

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).containsKey("src/main/App.java");
        assertThat(result.getFiles().get("src/main/App.java")).isEqualTo("public class App {}");
    }

    @Test
    @DisplayName("Files with excluded extensions are not fetched")
    void fetch_excludedExtension_fileIsSkipped() throws IOException {
        GHTreeEntry pngEntry = blobEntry("assets/logo.png"); // filtered by ext before size is checked
        when(mockTree.getTree()).thenReturn(List.of(pngEntry));

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).isEmpty();
        verify(mockRepo, never()).getFileContent(anyString(), anyString());
    }

    @Test
    @DisplayName("Multiple extensions are all accepted")
    void fetch_multipleIncludedExtensions_allFetched() throws IOException {
        GHTreeEntry ymlEntry  = blobEntry("src/main/resources/application.yml", 200);
        GHTreeEntry propEntry = blobEntry("src/main/resources/db.properties", 150);
        when(mockTree.getTree()).thenReturn(List.of(ymlEntry, propEntry));
        stubFileContent("src/main/resources/application.yml", "server.port=8082");
        stubFileContent("src/main/resources/db.properties",  "db.url=jdbc:postgresql://...");

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).hasSize(2)
                .containsKey("src/main/resources/application.yml")
                .containsKey("src/main/resources/db.properties");
    }

    // ── Filtering: excluded paths ─────────────────────────────────────────────

    @Test
    @DisplayName("Files under excluded directories are skipped")
    void fetch_excludedPath_fileIsSkipped() throws IOException {
        GHTreeEntry nodeModulesEntry = blobEntry("node_modules/lodash/index.js"); // filtered by path before size
        GHTreeEntry targetEntry      = blobEntry("target/classes/App.class");     // filtered by path before size
        when(mockTree.getTree()).thenReturn(List.of(nodeModulesEntry, targetEntry));

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).isEmpty();
        verify(mockRepo, never()).getFileContent(anyString(), anyString());
    }

    @Test
    @DisplayName("Files outside excluded directories are still included")
    void fetch_nonExcludedPathWithIncludedExt_fileIsIncluded() throws IOException {
        GHTreeEntry envEntry = blobEntry("config/.env", 80);
        when(mockTree.getTree()).thenReturn(List.of(envEntry));
        stubFileContent("config/.env", "DATABASE_URL=postgres://secret@host/db");

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).containsKey("config/.env");
    }

    // ── Filtering: file size ──────────────────────────────────────────────────

    @Test
    @DisplayName("Files exceeding max-file-size-bytes are skipped")
    void fetch_oversizedFile_fileIsSkipped() throws IOException {
        GHTreeEntry hugeEntry = blobEntry("data/schema.yml"); // included extension
        when(hugeEntry.getSize()).thenReturn(600_000L);        // > 512 KB
        when(mockTree.getTree()).thenReturn(List.of(hugeEntry));

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).isEmpty();
        verify(mockRepo, never()).getFileContent(anyString(), anyString());
    }

    @Test
    @DisplayName("Files at exactly max-file-size-bytes are included")
    void fetch_fileSizeAtLimit_fileIsIncluded() throws IOException {
        GHTreeEntry entry = blobEntry("config/big.yml");
        when(entry.getSize()).thenReturn(524_288L); // exactly 512 KB
        when(mockTree.getTree()).thenReturn(List.of(entry));
        stubFileContent("config/big.yml", "content");

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).containsKey("config/big.yml");
    }

    // ── Tree entries: non-blob types ──────────────────────────────────────────

    @Test
    @DisplayName("Tree (directory) entries are skipped without error")
    void fetch_treeTypeEntry_isIgnored() throws IOException {
        GHTreeEntry dirEntry = mock(GHTreeEntry.class);
        when(dirEntry.getType()).thenReturn("tree");
        when(mockTree.getTree()).thenReturn(List.of(dirEntry));

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).isEmpty();
    }

    // ── RepositoryContent construction ────────────────────────────────────────

    @Test
    @DisplayName("Returned RepositoryContent carries the correct repoFullName and ref")
    void fetch_resultHasCorrectMetadata() throws IOException {
        when(mockTree.getTree()).thenReturn(List.of());

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getRepoFullName()).isEqualTo(REPO);
        assertThat(result.getRef()).isEqualTo(REF);
        assertThat(result.getTargetRef()).isEqualTo("github:acme/payments-service@main");
    }

    @Test
    @DisplayName("RepositoryContent is empty when no files match filters")
    void fetch_noMatchingFiles_returnsEmptyContent() throws IOException {
        GHTreeEntry pngEntry = blobEntry("logo.png"); // .png not in include-extensions
        when(mockTree.getTree()).thenReturn(List.of(pngEntry));

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).isEmpty();
    }

    // ── Error resilience ──────────────────────────────────────────────────────

    @Test
    @DisplayName("A single file read failure does not abort the rest of the fetch")
    void fetch_oneFileReadFails_otherFilesStillFetched() throws IOException {
        GHTreeEntry goodEntry  = blobEntry("src/Good.java", 100);
        GHTreeEntry badEntry   = blobEntry("src/Bad.java",  100);
        when(mockTree.getTree()).thenReturn(List.of(goodEntry, badEntry));

        stubFileContent("src/Good.java", "class Good {}");

        GHContent badContent = mock(GHContent.class);
        when(mockRepo.getFileContent("src/Bad.java", REF)).thenReturn(badContent);
        when(badContent.read()).thenThrow(new IOException("network blip"));

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles())
                .containsKey("src/Good.java")
                .doesNotContainKey("src/Bad.java");
    }

    // ── No extension filter ───────────────────────────────────────────────────

    @Test
    @DisplayName("Empty include-extensions list means include all file types")
    void fetch_noExtensionFilter_includesAllExtensions() throws IOException {
        scanProperties.setIncludeExtensions(List.of()); // no filter

        GHTreeEntry mdEntry  = blobEntry("README.md",  200);
        GHTreeEntry sqlEntry = blobEntry("schema.sql", 300);
        when(mockTree.getTree()).thenReturn(List.of(mdEntry, sqlEntry));
        stubFileContent("README.md",  "# My App");
        stubFileContent("schema.sql", "CREATE TABLE users (...);");

        RepositoryContent result = fetcher.fetch(REPO, REF);

        assertThat(result.getFiles()).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Blob entry without a size stub — use when path/ext filtering kicks in before size. */
    private GHTreeEntry blobEntry(String path) {
        GHTreeEntry entry = mock(GHTreeEntry.class);
        when(entry.getType()).thenReturn("blob");
        when(entry.getPath()).thenReturn(path);
        return entry;
    }

    /** Blob entry with a size stub — use when the file passes path/ext filters and size IS checked. */
    private GHTreeEntry blobEntry(String path, long size) {
        GHTreeEntry entry = blobEntry(path);
        when(entry.getSize()).thenReturn(size);
        return entry;
    }

    private void stubFileContent(String path, String content) throws IOException {
        GHContent ghContent = mock(GHContent.class);
        when(mockRepo.getFileContent(path, REF)).thenReturn(ghContent);
        when(ghContent.read()).thenReturn(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }
}
