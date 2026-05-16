package io.faultmap.config;

import io.faultmap.core.domain.RegulatoryReference;
import io.faultmap.core.domain.Severity;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PatternRegistry {

    private final ResourceLoader resourceLoader;
    private final String patternsPath;

    @Getter
    private final List<PatternEntry> patterns = new ArrayList<>();

    public PatternRegistry(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.patternsPath   = "classpath:patterns/secrets.yml";
    }

    @PostConstruct
    public void load() {
        try {
            Resource resource = resourceLoader.getResource(patternsPath);
            try (InputStream in = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(in);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawPatterns =
                        (List<Map<String, Object>>) root.get("patterns");

                for (Map<String, Object> raw : rawPatterns) {
                    PatternEntry entry = parseEntry(raw);
                    patterns.add(entry);
                    log.debug("[PatternRegistry] Loaded pattern: {}", entry.getId());
                }

                log.info("[PatternRegistry] Loaded {} patterns from {}",
                        patterns.size(), patternsPath);
            }
        } catch (Exception e) {
            log.error("[PatternRegistry] Failed to load patterns: {}", e.getMessage(), e);
            throw new IllegalStateException(
                    "Cannot start Faultmap without pattern registry", e);
        }
    }

    public List<PatternEntry> getByCategory(String category) {
        return patterns.stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .toList();
    }

    public Optional<PatternEntry> getById(String id) {
        return patterns.stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private PatternEntry parseEntry(Map<String, Object> raw) {
        String patternStr = (String) raw.get("pattern");

        List<RegulatoryReference> regs = new ArrayList<>();
        List<Map<String, String>> rawRegs =
                (List<Map<String, String>>) raw.getOrDefault("regulations", List.of());

        for (Map<String, String> rawReg : rawRegs) {
            regs.add(RegulatoryReference.builder()
                    .framework(rawReg.get("framework"))
                    .article(rawReg.get("article"))
                    .obligation(rawReg.get("obligation"))
                    .docUrl(rawReg.get("docUrl"))
                    .build());
        }

        return PatternEntry.builder()
                .id((String) raw.get("id"))
                .name((String) raw.get("name"))
                .description((String) raw.get("description"))
                .pattern(Pattern.compile(patternStr, Pattern.MULTILINE))
                .severity(Severity.valueOf((String) raw.get("severity")))
                .category((String) raw.get("category"))
                .regulatoryRefs(regs)
                .build();
    }

    @Getter
    @Builder
    public static class PatternEntry {
        private final String                    id;
        private final String                    name;
        private final String                    description;
        private final Pattern                   pattern;
        private final Severity                  severity;
        private final String                    category;
        private final List<RegulatoryReference> regulatoryRefs;
    }
}