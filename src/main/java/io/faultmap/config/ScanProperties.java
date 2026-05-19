package io.faultmap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "faultmap.scan")
public class ScanProperties {

    /** Maximum file size to scan in bytes. Files larger than this are skipped. */
    private long maxFileSizeBytes = 524_288; // 512 KB

    /**
     * File extensions to include in the Core scanner.
     * An empty list means include everything (no extension filter).
     */
    private List<String> includeExtensions = new ArrayList<>();

    /**
     * Path prefixes to always exclude.
     * e.g. "node_modules/", ".git/", "target/"
     */
    private List<String> excludePaths = new ArrayList<>();
}
