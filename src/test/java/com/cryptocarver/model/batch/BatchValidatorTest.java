package com.cryptocarver.model.batch;

import com.cryptocarver.model.process.DryRunSummary;
import com.cryptocarver.model.process.StepValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BatchValidatorTest {

    @Test
    void testUnknownBatchOperationIsBlocked() {
        List<Map<String, String>> rows = List.of(Map.of("input", "test"));
        DryRunSummary summary = BatchValidator.dryRun(rows, "UNKNOWN_BATCH_OP", "input", "output", null, null, null);

        assertEquals(0, summary.readyCount());
        assertTrue(summary.blockedCount() > 0, "Unknown batch operation must be blocked");
        assertNotNull(summary.firstBlockedReason());
        assertTrue(summary.firstBlockedReason().contains("Unknown batch operation"), "Reason must mention unknown operation");
    }

    @Test
    void testInvalidHexKeyAndIvInBatchAreBlocked() {
        List<Map<String, String>> rows = List.of(Map.of("input", "test"));
        DryRunSummary summary = BatchValidator.dryRun(rows, "AES Cipher", "input", "output", "AES-256-CBC", "not-hex-key", "invalid-iv");

        assertTrue(summary.blockedCount() > 0, "Invalid hex key/IV must be blocked");
        assertNotNull(summary.firstBlockedReason());
        assertTrue(summary.firstBlockedReason().contains("Invalid Hex key format"), "First blocked reason must mention invalid hex key");
    }

    @Test
    void testBatchCountersIncludeBothConfigAndRowErrors() {
        // Row 1 has column, Row 2 missing column 'input'
        List<Map<String, String>> rows = List.of(
            Map.of("input", "val1"),
            Map.of("wrong_col", "val2")
        );
        // Cipher with missing algorithm and missing key (2 config incomplete errors)
        DryRunSummary summary = BatchValidator.dryRun(rows, "AES Cipher", "input", "output", "", "", "");

        // Validations contain: batch_algo (incomplete), batch_key (incomplete), Row 1 (ready), Row 2 (blocked)
        // Total validation items: 4
        assertEquals(4, summary.totalSteps(), "Total steps must equal sum of config and row validation items");
        assertEquals(1, summary.readyCount(), "Row 1 is ready");
        assertEquals(2, summary.incompleteCount(), "Algorithm and key are incomplete");
        assertEquals(1, summary.blockedCount(), "Row 2 is blocked");
    }
}
