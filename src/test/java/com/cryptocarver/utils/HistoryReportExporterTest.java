package com.cryptocarver.utils;

import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryReportExporterTest {
    @Test
    void appliesSecretVisibilityProfileToReadableReports() {
        HistoryCommand item = new HistoryCommand("Key generation", "", Map.of());
        item.setStructuredDetails(List.of(
                OperationDetail.publicDetail("Algorithm", "AES-256"),
                OperationDetail.sensitiveDetail("Label", "Lab key"),
                OperationDetail.secretDetail("Key", "0011223344556677")));

        String redacted = HistoryReportExporter.toMarkdown(item, SecretVisibilityProfile.REDACTED);
        assertTrue(redacted.contains("AES-256"));
        assertTrue(redacted.contains("***MASKED***"));
        assertFalse(redacted.contains("0011223344556677"));
        assertFalse(redacted.contains("|Key|"));

        String masked = HistoryReportExporter.toMarkdown(item, SecretVisibilityProfile.MASKED);
        assertTrue(masked.contains("|Key|SECRET||***MASKED***|"));
        assertFalse(masked.contains("0011223344556677"));

        assertTrue(HistoryReportExporter.toMarkdown(item, SecretVisibilityProfile.FULL_LAB).contains("0011223344556677"));
    }
}
