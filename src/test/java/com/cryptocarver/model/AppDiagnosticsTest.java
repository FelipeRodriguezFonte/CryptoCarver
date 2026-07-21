package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AppDiagnosticsTest {
    @Test
    void reportContainsRuntimeFactsAndExplicitlyExcludesSecrets() {
        String report = AppDiagnostics.report();

        assertTrue(report.contains("CryptoCarver diagnostics"));
        assertTrue(report.contains("Application version: " + BuildInfo.version()));
        assertTrue(report.contains("Release channel: " + BuildInfo.channel()));
        assertTrue(report.contains("Build Java release: " + BuildInfo.javaRelease()));
        assertTrue(report.contains("Java:"));
        assertTrue(report.contains("Operating system:"));
        assertTrue(report.contains("No key material"));
    }

    @Test
    void buildInfoProvidesNonBlankReleaseMetadata() {
        assertFalse(BuildInfo.name().isBlank());
        assertFalse(BuildInfo.version().isBlank());
        assertFalse(BuildInfo.javaRelease().isBlank());
        assertFalse(BuildInfo.channel().isBlank());
    }
}
