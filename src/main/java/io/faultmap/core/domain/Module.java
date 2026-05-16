package io.faultmap.core.domain;

/**
 * The Faultmap module that produced a Finding.
 *
 * Every module shares the same Finding model and compliance engine.
 * This field lets the dashboard filter by module and lets the
 * compliance report group findings by surface area.
 */
public enum Module {

    /** Core scanner — secrets, misconfigs, exposed APIs in repos. */
    CORE,

    /** AI Audit — agent traces, PII exposure, policy violations. */
    AI_AUDIT,

    /**
     * Quantum Migration — vulnerable crypto algorithms across
     * source code, TLS, certs, dependencies, and cloud KMS.
     */
    QUANTUM_MIGRATION,

    /** Dependency scanner — CVEs in open-source libraries. */
    DEPENDENCY,

    /** Smart contract auditor — Solidity vulnerability detection. */
    SMART_CONTRACT
}