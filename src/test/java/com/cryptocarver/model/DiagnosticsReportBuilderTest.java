package com.cryptocarver.model;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsReportBuilderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsExpectedSafeRuntimeReport() {
        String report = DiagnosticsReportBuilder.build("synthetic-display");
        assertTrue(report.startsWith("CryptoCarver diagnostics\n"));
        assertTrue(report.contains("Application version:"));
        assertTrue(report.contains("Display: synthetic-display"));
        assertTrue(report.contains("No key material, input data, credentials or file paths"));
        assertFalse(report.contains(System.getProperty("user.home")));
    }

    @Test
    void writesTheExactReportContentToTheSelectedFile() throws Exception {
        Path report = temporaryDirectory.resolve("diagnostics.txt");
        String content = "CryptoCarver diagnostics\nSynthetic safe report";
        DiagnosticsReportBuilder.write(report, content);
        assertEquals(content, Files.readString(report));
    }
}
