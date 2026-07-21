package com.cryptocarver.utils;

import com.cryptocarver.model.HistoryItem;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibility;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryReportExporterTest {
    @Test
    void appliesSecretVisibilityToReadableReports() {
        HistoryItem item = new HistoryItem("Key generation", "", Map.of());
        item.setStructuredDetails(List.of(
                OperationDetail.publicDetail("Algorithm", "AES-256"),
                OperationDetail.sensitiveDetail("Label", "Lab key"),
                OperationDetail.secretDetail("Key", "0011223344556677")));

        String redacted = HistoryReportExporter.toMarkdown(item, SecretVisibility.REDACTED);
        assertTrue(redacted.contains("AES-256"));
        assertTrue(redacted.contains("***MASKED***"));
        assertFalse(redacted.contains("0011223344556677"));
        assertFalse(redacted.contains("|Key|"));

        String masked = HistoryReportExporter.toMarkdown(item, SecretVisibility.MASKED);
        assertTrue(masked.contains("|Key|SECRET||***MASKED***|"));
        assertFalse(masked.contains("0011223344556677"));

        assertTrue(HistoryReportExporter.toMarkdown(item, SecretVisibility.FULL_LAB).contains("0011223344556677"));
    }
}
