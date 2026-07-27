package com.cryptocarver.model.process;

import java.time.Duration;

public record NodeExecutionEvent(
    String nodeId,
    int step,
    String nodeLabel,
    String nodeType,
    NodeExecutionState state,
    Duration duration,
    Representation inputRepresentation,
    int inputSize,
    Representation outputRepresentation,
    int outputSize,
    String safeMessage
) {
}
