package com.cryptocarver.model.help;

import java.util.List;

/** Localized resource keys plus stable operation and action identities. */
public record OperationHelpDefinition(String operationId, String purposeKey, List<ActionHelp> actions,
                                      String referenceId, boolean complete) {
    public OperationHelpDefinition {
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("Operation ID is required");
        purposeKey = purposeKey == null ? "" : purposeKey;
        actions = actions == null ? List.of() : List.copyOf(actions);
        referenceId = referenceId == null ? "" : referenceId;
    }
}
