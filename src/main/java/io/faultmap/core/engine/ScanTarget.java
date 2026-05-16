package io.faultmap.core.engine;

import io.faultmap.core.domain.Module;

/**
 * Abstraction over anything that can be scanned.
 *
 * This is the key architectural decision that lets Faultmap add new modules
 * without touching the engine. A ScanTarget might be:
 *
 *   - RepositoryContent   (Core scanner — files from a GitHub repo)
 *   - AgentEvent          (AI Audit — a captured LLM session)
 *   - TlsEndpoint         (PQC scanner — a live TLS handshake)
 *   - DependencyManifest  (Dependency scanner — a parsed pom.xml)
 *   - SolidityContract    (Smart contract — raw Solidity source)
 *
 * Each ScanRule declares which Module it belongs to, and the ScanService
 * only routes targets to rules whose module matches.
 */
public interface ScanTarget {

    /**
     * Which module this target belongs to.
     * The ScanService uses this to route targets to the right rules.
     */
    Module getModule();

    /**
     * A human-readable identifier for this target.
     * Used in ScanResult.targetRef and Finding.location prefixes.
     *
     * Examples:
     *   "github:lendrfinance/payments-service"
     *   "tls:api.lendrfinance.com"
     *   "agent:LoanAssistAgent:session-8823"
     */
    String getTargetRef();
}