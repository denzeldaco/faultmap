package io.faultmap.core.engine;

import io.faultmap.core.domain.Finding;
import io.faultmap.core.domain.Module;

import java.util.List;

/**
 * The core interface of the Faultmap scan engine.
 *
 * Every detection rule — across every module — implements this interface.
 * This is the design decision that makes the platform extensible:
 * adding a new rule is adding a new Spring Bean. The engine discovers
 * and runs them automatically.
 *
 * Implementing a rule:
 *
 *   @Component
 *   public class AwsSecretKeyRule implements ScanRule {
 *
 *       @Override public String ruleId()   { return "AWS_SECRET_KEY"; }
 *       @Override public Module module()   { return Module.CORE; }
 *       @Override public Severity defaultSeverity() { return Severity.CRITICAL; }
 *
 *       @Override
 *       public List<Finding> scan(ScanTarget target) {
 *           RepositoryContent repo = (RepositoryContent) target;
 *           // ... pattern matching logic
 *       }
 *
 *       @Override
 *       public boolean supports(ScanTarget target) {
 *           return target instanceof RepositoryContent;
 *       }
 *   }
 *
 * Rules are stateless — all state lives in the ScanTarget or is
 * looked up from injected Spring beans (e.g. PatternRegistry).
 */
public interface ScanRule {

    /**
     * Unique identifier for this rule.
     * Used in Finding.ruleId and for suppression configuration.
     * Convention: SCREAMING_SNAKE_CASE.
     * Examples: "AWS_SECRET_KEY", "BVN_IN_PROMPT", "RSA_2048_TLS"
     */
    String ruleId();

    /** Which module this rule belongs to. */
    Module module();

    /**
     * Whether this rule can handle the given target.
     * The ScanService calls this before scan() to route correctly.
     */
    boolean supports(ScanTarget target);

    /**
     * Execute the rule against the target and return all findings.
     *
     * Contract:
     * - Never return null — return an empty list if nothing found.
     * - Never throw unchecked exceptions — catch internally and log.
     * - Each Finding must have: ruleId, title, location, severity,
     *   description, remediation, and at least one regulatoryRef.
     */
    List<Finding> scan(ScanTarget target);

    /**
     * Human-readable name shown in the dashboard rule list.
     * Defaults to the ruleId if not overridden.
     */
    default String displayName() {
        return ruleId();
    }

    /**
     * Whether this rule is enabled by default.
     * Rules can be disabled per-organisation via config.
     */
    default boolean enabledByDefault() {
        return true;
    }
}