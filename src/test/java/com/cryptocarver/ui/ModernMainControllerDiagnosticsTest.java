package com.cryptocarver.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ModernMainControllerDiagnosticsTest {
    @Test
    void writesTheExactSafeDiagnosticsSnapshot() throws Exception {
        Path report = Files.createTempFile("cryptocarver-diagnostics-", ".txt");
        try {
            String content = "CryptoCarver diagnostics\nApplication version: test";
            ModernMainController.writeDiagnosticsReport(report, content);
            assertEquals(content, Files.readString(report));
        } finally {
            Files.deleteIfExists(report);
        }
    }
}
