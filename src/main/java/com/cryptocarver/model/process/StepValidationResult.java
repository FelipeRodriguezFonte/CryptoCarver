package com.cryptocarver.model.process;

/** Represents readiness status, explanation, and target navigation details for a workflow step or batch task. */
public record StepValidationResult(
    Status status,
    String message,
    String targetNodeId,
    String targetFieldKey
) {
    public enum Status {
        READY,
        WARNING,
        INCOMPLETE,
        BLOCKED,
        NOT_APPLICABLE
    }

    public static StepValidationResult ready(String nodeId, String message) {
        return new StepValidationResult(Status.READY, message, nodeId, null);
    }

    public static StepValidationResult warning(String nodeId, String fieldKey, String message) {
        return new StepValidationResult(Status.WARNING, message, nodeId, fieldKey);
    }

    public static StepValidationResult incomplete(String nodeId, String fieldKey, String message) {
        return new StepValidationResult(Status.INCOMPLETE, message, nodeId, fieldKey);
    }

    public static StepValidationResult blocked(String nodeId, String fieldKey, String message) {
        return new StepValidationResult(Status.BLOCKED, message, nodeId, fieldKey);
    }

    public static StepValidationResult notApplicable(String nodeId, String message) {
        return new StepValidationResult(Status.NOT_APPLICABLE, message, nodeId, null);
    }
}
