package io.faultmap.github;

import io.faultmap.config.GitHubProperties;
import io.faultmap.config.ScanProperties;
import io.faultmap.core.target.RepositoryContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches all scannable text files from a GitHub repository at a given ref
 * and packages them into a {@link RepositoryContent} for the scan engine.
 *
 * <p>Authentication priority:
 * <ol>
 *   <li>OAuth / personal access token ({@code faultmap.github.token} / {@code GITHUB_TOKEN})</li>
 *   <li>GitHub App installation token ({@code faultmap.github.app-id} + {@code private-key-path})</li>
 *   <li>Anonymous — public repositories only</li>
 * </ol>
 *
 * <p>File filtering (configured under {@code faultmap.scan}):
 * <ul>
 *   <li>Only blobs — directories and submodule entries are skipped.</li>
 *   <li>Paths matching any {@code exclude-paths} prefix are skipped.</li>
 *   <li>Only files whose extension appears in {@code include-extensions} are fetched
 *       (empty list means no filter — include all).</li>
 *   <li>Files larger than {@code max-file-size-bytes} are skipped.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubFetcher {

    private final GitHubProperties githubProperties;
    private final ScanProperties   scanProperties;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetch all scannable files from {@code repoFullName} at {@code ref} and
     * return them packaged in a {@link RepositoryContent} ready for the scan engine.
     *
     * @param repoFullName  owner/repo, e.g. {@code "lendrfinance/payments-service"}
     * @param ref           branch, tag, or commit SHA
     * @return              populated RepositoryContent
     * @throws IOException  if the GitHub API cannot be reached or the repo is not found
     */
    public RepositoryContent fetch(String repoFullName, String ref) throws IOException {
        log.info("[GitHubFetcher] Fetching repo={} ref={}", repoFullName, ref);

        GitHub github = connectToGitHub(repoFullName);
        GHRepository repo = github.getRepository(repoFullName);
        GHTree tree = repo.getTreeRecursive(ref, 1);

        if (tree.isTruncated()) {
            log.warn("[GitHubFetcher] Tree response is truncated for repo={} ref={} — " +
                     "very large repos may have incomplete file coverage", repoFullName, ref);
        }

        Map<String, String> files = new LinkedHashMap<>();
        int skippedPath = 0, skippedExt = 0, skippedSize = 0, skippedError = 0;

        for (GHTreeEntry entry : tree.getTree()) {
            if (!"blob".equals(entry.getType())) continue;

            String path = entry.getPath();

            if (isExcludedPath(path))        { skippedPath++;  continue; }
            if (!hasIncludedExtension(path)) { skippedExt++;   continue; }
            if (entry.getSize() > scanProperties.getMaxFileSizeBytes()) {
                log.debug("[GitHubFetcher] Skipping {} — size {} exceeds limit {}",
                        path, entry.getSize(), scanProperties.getMaxFileSizeBytes());
                skippedSize++;
                continue;
            }

            try {
                GHContent content = repo.getFileContent(path, ref);
                try (InputStream in = content.read()) {
                    files.put(path, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                skippedError++;
                log.debug("[GitHubFetcher] Could not read {}: {}", path, e.getMessage());
            }
        }

        log.info("[GitHubFetcher] Fetched {} files | repo={} ref={} | " +
                 "skipped: {} path-excluded, {} wrong-ext, {} too-large, {} errors",
                files.size(), repoFullName, ref,
                skippedPath, skippedExt, skippedSize, skippedError);

        return RepositoryContent.builder()
                .repoFullName(repoFullName)
                .ref(ref)
                .files(files)
                .build();
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Build an authenticated GitHub client. Overridable in tests.
     */
    protected GitHub connectToGitHub(String repoFullName) throws IOException {
        if (StringUtils.hasText(githubProperties.getToken())) {
            log.debug("[GitHubFetcher] Authenticating via OAuth token");
            return new GitHubBuilder()
                    .withOAuthToken(githubProperties.getToken())
                    .build();
        }

        if (StringUtils.hasText(githubProperties.getAppId()) &&
                StringUtils.hasText(githubProperties.getPrivateKeyPath())) {
            log.debug("[GitHubFetcher] Authenticating via GitHub App");
            return buildAppInstallationClient(repoFullName);
        }

        log.warn("[GitHubFetcher] No credentials configured — " +
                 "connecting anonymously (public repos only, rate-limited)");
        return GitHub.connectAnonymously();
    }

    /**
     * Authenticate as a GitHub App and return a client with an installation
     * token scoped to the given repository.
     */
    private GitHub buildAppInstallationClient(String repoFullName) throws IOException {
        String jwt = createAppJwt(githubProperties.getAppId(),
                                  githubProperties.getPrivateKeyPath());

        GitHub appClient = new GitHubBuilder().withJwtToken(jwt).build();

        String[] parts = repoFullName.split("/", 2);
        GHAppInstallation installation = appClient.getApp()
                .getInstallationByRepository(parts[0], parts[1]);

        String installationToken = installation.createToken().create().getToken();

        return new GitHubBuilder()
                .withAppInstallationToken(installationToken)
                .build();
    }

    // ── JWT signing for GitHub App auth ──────────────────────────────────────

    /**
     * Create a signed RS256 JWT for GitHub App authentication.
     *
     * <p>The private key file must be in <strong>PKCS8 PEM</strong> format.
     * GitHub App keys are generated as PKCS1; convert with:
     * <pre>
     *   openssl pkcs8 -topk8 -inform PEM -outform PEM \
     *     -in github-app.pem -out github-app-pkcs8.pem -nocrypt
     * </pre>
     */
    private String createAppJwt(String appId, String privateKeyPath) throws IOException {
        try {
            byte[] keyBytes = Files.readAllBytes(Paths.get(privateKeyPath));
            PrivateKey privateKey = parsePkcs8PrivateKey(keyBytes);

            long now = Instant.now().getEpochSecond();
            // 60-second clock skew buffer; 10-minute expiry (GitHub App limit)
            String header  = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            String payload = base64Url(String.format(
                    "{\"iat\":%d,\"exp\":%d,\"iss\":\"%s\"}",
                    now - 60, now + 600, appId));

            String signingInput = header + "." + payload;
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sig.sign());

            return signingInput + "." + signature;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to create GitHub App JWT: " + e.getMessage(), e);
        }
    }

    private PrivateKey parsePkcs8PrivateKey(byte[] pemBytes) throws Exception {
        String pem = new String(pemBytes, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String base64Url(String input) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    // ── File filtering ────────────────────────────────────────────────────────

    private boolean isExcludedPath(String path) {
        List<String> excludes = scanProperties.getExcludePaths();
        return excludes.stream().anyMatch(path::startsWith);
    }

    private boolean hasIncludedExtension(String path) {
        List<String> extensions = scanProperties.getIncludeExtensions();
        if (extensions.isEmpty()) return true; // no filter configured — include everything
        return extensions.stream().anyMatch(path::endsWith);
    }
}
