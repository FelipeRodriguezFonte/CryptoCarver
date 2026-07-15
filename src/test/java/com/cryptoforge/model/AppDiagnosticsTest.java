package com.cryptoforge.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppDiagnosticsTest {
    @Test
    void reportContainsRuntimeFactsAndExplicitlyExcludesSecrets() {
        String report = AppDiagnostics.report();

        assertTrue(report.contains("CryptoCarver diagnostics"));
        assertTrue(report.contains("Java:"));
        assertTrue(report.contains("Operating system:"));
        assertTrue(report.contains("No key material"));
    }
}
