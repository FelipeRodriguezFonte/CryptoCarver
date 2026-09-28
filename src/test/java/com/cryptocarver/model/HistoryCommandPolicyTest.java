package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HistoryCommandPolicyTest {
    @Test
    void createsStructuredRecipeAndUsesResolvableNavigationOrOperationFallback() {
        HistoryCommand command = HistoryCommandPolicy.create("Synthetic result",
                List.of(OperationDetail.publicDetail("Marker", "SYNTHETIC")),
                Map.of("SyntheticController.secretField", "[REDACTED_SECRET]", "mode", "safe"),
                "Hex", "Base64", "Synthetic module", Set.of("Synthetic module")::contains);

        assertEquals("Synthetic result", command.getOperation());
        assertEquals("Synthetic module", command.getNavigationOperation());
        assertEquals("Hex", command.getInputFormat());
        assertEquals("Base64", command.getOutputFormat());
        assertEquals(HistoryCommand.Reproducibility.REPRODUCIBLE_WITH_SECRETS, command.getReproducibility());
        assertTrue(command.getDetails().contains("SYNTHETIC"));
        assertEquals("[REDACTED_SECRET]", command.getParameters().get("SyntheticController.secretField"));

        assertEquals("Synthetic result", HistoryCommandPolicy.effectiveNavigationTarget(
                "Synthetic result", "Unknown route", ignored -> false));
    }
}
