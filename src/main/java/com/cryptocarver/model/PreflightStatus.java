package com.cryptocarver.model;

/**
 * Represents the readiness status of a preflight execution check.
 */
public enum PreflightStatus {
    /**
     * All checks passed; operation is ready for safe execution.
     */
    READY,

    /**
     * Operation can be executed, but security or non-standard configuration warnings exist
     * (e.g. ECB mode, unauthenticated CBC, DES usage, non-12-byte GCM IV).
     */
    WARNING,

    /**
     * Required parameter or input data is missing (e.g. empty key, empty input, missing IV).
     */
    INCOMPLETE,

    /**
     * Execution cannot proceed due to an invalid format, incompatible key, or metadata-only reference.
     */
    BLOCKED;

    public boolean isExecutable() {
        return this == READY || this == WARNING;
    }
}
