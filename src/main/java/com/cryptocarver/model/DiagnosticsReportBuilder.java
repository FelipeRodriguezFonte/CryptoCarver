package com.cryptocarver.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds and writes the safe diagnostics report without JavaFX dependencies. */
public final class DiagnosticsReportBuilder {
    private DiagnosticsReportBuilder() { }

    public static String build(String displaySummary) {
        return AppDiagnostics.report(displaySummary);
    }

    public static void write(Path report, String content) throws IOException {
        Files.writeString(report, content);
    }
}
