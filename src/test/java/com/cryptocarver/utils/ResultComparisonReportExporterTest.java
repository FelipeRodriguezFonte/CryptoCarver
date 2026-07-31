package com.cryptocarver.utils;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultComparisonReportExporterTest {

    @Test
    void testMarkdownReportExporterNoSecretLeak() {
        ClipboardEntry e1 = new ClipboardEntry(
                "Entry A", "SECRET_PAYLOAD_111", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.SECRET, "Enc", "AES-256-GCM",
                java.util.List.of("secret-tag"), "SECRET_NOTE_111", false
        );
        ClipboardEntry e2 = new ClipboardEntry(
                "Entry B", "SECRET_PAYLOAD_222", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.SECRET, "Enc", "AES-256-GCM",
                java.util.List.of("secret-tag"), "SECRET_NOTE_222", false
        );

        String reportMarkdown = ResultComparisonReportExporter.toMarkdown(e1, e2, SecretVisibilityProfile.REDACTED);
        assertNotNull(reportMarkdown);
        assertTrue(reportMarkdown.contains("Laboratory Result Comparison Report"));
        assertTrue(reportMarkdown.contains("AES-256-GCM"));
        assertFalse(reportMarkdown.contains("SECRET_PAYLOAD_111"), "Report must NOT leak secret payload under REDACTED profile");
        assertFalse(reportMarkdown.contains("SECRET_PAYLOAD_222"), "Report must NOT leak secret payload under REDACTED profile");
        assertFalse(reportMarkdown.contains("SECRET_NOTE_111"), "Report must NOT leak secret note under REDACTED profile");
        assertFalse(reportMarkdown.contains("secret-tag"), "Report must NOT leak secret tags under REDACTED profile");

        String reportJson = ResultComparisonReportExporter.toJson(e1, e2, SecretVisibilityProfile.REDACTED);
        assertNotNull(reportJson);
        assertTrue(reportJson.contains("LaboratoryResultComparison"));
        assertFalse(reportJson.contains("SECRET_PAYLOAD_111"), "JSON report must NOT leak secret payload under REDACTED profile");
        assertFalse(reportJson.contains("SECRET_NOTE_111"), "JSON report must NOT leak secret note under REDACTED profile");
    }
}
