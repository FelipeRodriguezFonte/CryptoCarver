package com.cryptocarver.model;

import java.util.Objects;

/**
 * Represents an individual execution readiness check result.
 */
public final class PreflightCheck {
    private final String name;
    private final PreflightStatus status;
    private final String message;
    private final String targetControlKey;

    public PreflightCheck(String name, PreflightStatus status, String message, String targetControlKey) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.targetControlKey = targetControlKey;
    }

    public String getName() {
        return name;
    }

    public PreflightStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getTargetControlKey() {
        return targetControlKey;
    }

    @Override
    public String toString() {
        return "[" + status + "] " + name + ": " + message + (targetControlKey != null ? " (Focus: " + targetControlKey + ")" : "");
    }
}
