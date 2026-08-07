package com.cryptocarver.model.process;

import java.util.List;

/** Summary report produced by a Dry Run simulation. */
public record DryRunSummary(
    int totalSteps,
    int readyCount,
    int warningCount,
    int incompleteCount,
    int blockedCount,
    String firstBlockedReason,
    List<String> executionOrder,
    List<String> resolvedDependencies,
    List<StepValidationResult> stepValidations
) {
    public boolean isRunnable() {
        return blockedCount == 0 && totalSteps > 0;
    }
}
