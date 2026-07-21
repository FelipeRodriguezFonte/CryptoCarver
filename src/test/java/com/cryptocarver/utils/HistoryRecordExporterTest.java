package com.cryptocarver.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.model.HistoryItem;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibility;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryRecordExporterTest {

    @Test
    void unsafeRecordIncludesInputAndOutputButMaskedRecordDoesNot() {
        HistoryItem item = new HistoryItem("SHA-256", "", Map.of());
        item.setStructuredDetails(List.of(
                OperationDetail.sensitiveDetail("Input (10 bytes)", "hash input"),
                OperationDetail.sensitiveDetail("Output (32 bytes)", "A1B2C3"),
                OperationDetail.secretDetail("Private key", "private-material")));

        String unsafe = HistoryRecordExporter.toJson(item, SecretVisibility.FULL_LAB);
        assertTrue(unsafe.contains("hash input"));
        assertTrue(unsafe.contains("A1B2C3"));
        assertTrue(unsafe.contains("private-material"));

        String masked = HistoryRecordExporter.toJson(item, SecretVisibility.MASKED);
        assertFalse(masked.contains("hash input"));
        assertFalse(masked.contains("private-material"));
        assertTrue(masked.contains("***MASKED***"));

        String redacted = HistoryRecordExporter.toJson(item, SecretVisibility.REDACTED);
        assertFalse(redacted.contains("private-material"));
        assertFalse(redacted.contains("Private key"));
        assertTrue(redacted.contains("Input (10 bytes)"));
    }

    @Test
    void collectionExportAppliesTheSamePolicyToEveryRecord() {
        HistoryItem first = new HistoryItem("One", "", Map.of());
        first.setStructuredDetails(List.of(OperationDetail.sensitiveDetail("Input", "first input")));
        HistoryItem second = new HistoryItem("Two", "", Map.of());
        second.setStructuredDetails(List.of(OperationDetail.secretDetail("Private key", "private material")));

        String unsafe = HistoryRecordExporter.toJson(List.of(first, second), SecretVisibility.FULL_LAB);
        assertTrue(unsafe.contains("cryptocarver-history-export-v1"));
        assertTrue(unsafe.contains("first input"));
        assertTrue(unsafe.contains("private material"));

        String redacted = HistoryRecordExporter.toJson(List.of(first, second), SecretVisibility.REDACTED);
        assertFalse(redacted.contains("private material"));
        assertFalse(redacted.contains("Private key"));
        assertTrue(redacted.contains("***MASKED***"));
    }
}
