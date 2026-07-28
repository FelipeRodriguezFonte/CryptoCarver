package com.cryptocarver.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates all readiness checks for a given cryptographic operation.
 */
public final class PreflightReport {
    private final PreflightStatus overallStatus;
    private final String summaryMessage;
    private final List<PreflightCheck> checks;

    public PreflightReport(PreflightStatus overallStatus, String summaryMessage, List<PreflightCheck> checks) {
        this.overallStatus = Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        this.summaryMessage = Objects.requireNonNull(summaryMessage, "summaryMessage must not be null");
        this.checks = Collections.unmodifiableList(Objects.requireNonNull(checks, "checks must not be null"));
    }

    public PreflightStatus getOverallStatus() {
        return overallStatus;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    public List<PreflightCheck> getChecks() {
        return checks;
    }

    public boolean isExecutable() {
        return overallStatus.isExecutable();
    }

    public PreflightCheck getFirstNonReadyCheck() {
        for (PreflightCheck check : checks) {
            if (check.getStatus() == PreflightStatus.BLOCKED || check.getStatus() == PreflightStatus.INCOMPLETE) {
                return check;
            }
        }
        for (PreflightCheck check : checks) {
            if (check.getStatus() != PreflightStatus.READY) {
                return check;
            }
        }
        return null;
    }
}
