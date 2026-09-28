package com.cryptocarver.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryRecordExporterTest {

    @Test
    void unsafeRecordIncludesInputAndOutputButMaskedRecordDoesNot() {
        HistoryCommand item = new HistoryCommand("SHA-256", "", Map.of());
        item.setStructuredDetails(List.of(
                OperationDetail.sensitiveDetail("Input (10 bytes)", "SYNTHETIC_INPUT_VALUE"),
                OperationDetail.sensitiveDetail("Output (32 bytes)", "SYNTHETIC_OUTPUT_VALUE"),
                OperationDetail.secretDetail("Synthetic secret field", "SYNTHETIC_SECRET_VALUE")));

        String unsafe = HistoryRecordExporter.toJson(item, SecretVisibilityProfile.FULL_LAB);
        assertTrue(unsafe.contains("SYNTHETIC_INPUT_VALUE"));
        assertTrue(unsafe.contains("SYNTHETIC_OUTPUT_VALUE"));
        assertTrue(unsafe.contains("SYNTHETIC_SECRET_VALUE"));

        String masked = HistoryRecordExporter.toJson(item, SecretVisibilityProfile.MASKED);
        assertFalse(masked.contains("SYNTHETIC_INPUT_VALUE"));
        assertFalse(masked.contains("SYNTHETIC_SECRET_VALUE"));
        assertTrue(masked.contains("***MASKED***"));

        String redacted = HistoryRecordExporter.toJson(item, SecretVisibilityProfile.REDACTED);
        assertFalse(redacted.contains("SYNTHETIC_SECRET_VALUE"));
        assertFalse(redacted.contains("Synthetic secret field"));
        assertTrue(redacted.contains("Input (10 bytes)"));
    }

    @Test
    void collectionExportAppliesTheSamePolicyToEveryRecord() {
        HistoryCommand first = new HistoryCommand("One", "", Map.of());
        first.setStructuredDetails(List.of(OperationDetail.sensitiveDetail("Input", "SYNTHETIC_FIRST_INPUT")));
        HistoryCommand second = new HistoryCommand("Two", "", Map.of());
        second.setStructuredDetails(List.of(OperationDetail.secretDetail("Synthetic secret field", "SYNTHETIC_SECOND_SECRET")));

        String unsafe = HistoryRecordExporter.toJson(List.of(first, second), SecretVisibilityProfile.FULL_LAB);
        assertTrue(unsafe.contains("cryptocarver-history-export-v1"));
        assertTrue(unsafe.contains("SYNTHETIC_FIRST_INPUT"));
        assertTrue(unsafe.contains("SYNTHETIC_SECOND_SECRET"));

        String redacted = HistoryRecordExporter.toJson(List.of(first, second), SecretVisibilityProfile.REDACTED);
        assertFalse(redacted.contains("SYNTHETIC_SECOND_SECRET"));
        assertFalse(redacted.contains("Synthetic secret field"));
        assertTrue(redacted.contains("***MASKED***"));
    }
}
