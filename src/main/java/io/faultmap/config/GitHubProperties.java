package io.faultmap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "faultmap.github")
public class GitHubProperties {

    /** GitHub App ID — set via GITHUB_APP_ID env var. */
    private String appId;

    /** Path to the GitHub App private key (PKCS8 PEM). Set via GITHUB_PRIVATE_KEY_PATH. */
    private String privateKeyPath;

    /** Webhook secret for verifying incoming GitHub events. Set via GITHUB_WEBHOOK_SECRET. */
    private String webhookSecret;

    /**
     * Personal access token or fine-grained token for simpler setups.
     * Takes priority over GitHub App credentials when set.
     * Set via GITHUB_TOKEN env var.
     */
    private String token;
}
