package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoryCommandTest {

    @Test
    void navigationOperationKeepsTheOriginatingWorkspaceSeparateFromTheResultLabel() {
        HistoryCommand command = new HistoryCommand("Derive - HKDF-SHA256", "", Map.of(),
                HistoryCommand.Reproducibility.REPRODUCIBLE_WITHOUT_SECRETS, "", null, null,
                "Key Derivation (KDF)");

        assertEquals("Derive - HKDF-SHA256", command.getOperation());
        assertEquals("Key Derivation (KDF)", command.getNavigationOperation());
    }

    @Test
    void defaultReproducibilityIsPartial() {
        HistoryCommand cmd = new HistoryCommand("Op", "details", Map.of());
        assertEquals(HistoryCommand.Reproducibility.REPRODUCIBLE_WITH_SECRETS, cmd.getReproducibility());
        assertEquals("Legacy execution", cmd.getReproducibilityReason());
    }

    @Test
    void explicitReproducibilityIsHonored() {
        HistoryCommand cmd = new HistoryCommand(
                "Op",
                "details",
                Map.of(),
                HistoryCommand.Reproducibility.NOT_REPRODUCIBLE,
                "Lacks random salt",
                "input",
                "output"
        );
        assertEquals(HistoryCommand.Reproducibility.NOT_REPRODUCIBLE, cmd.getReproducibility());
        assertEquals("Lacks random salt", cmd.getReproducibilityReason());
    }

    @Test
    void handlesNullUiStateGracefully() {
        HistoryCommand cmd = new HistoryCommand("Op", "details", null);
        assertNotNull(cmd.getParameters());
        assertTrue(cmd.getParameters().isEmpty());
    }
}
