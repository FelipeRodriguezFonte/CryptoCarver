package com.cryptocarver.model;

/** Pure allowlist projection using the trail's existing PUBLIC classification policy. */
public final class RedactedTrail {
    private RedactedTrail() { }

    /** Keeps metadata and public results, rebuilding the chain over only the retained fields. */
    public static OperationSessionLog from(OperationSessionLog source) {
        return source == null ? null : OperationSessionLog.publicSnapshot(source);
    }
}
