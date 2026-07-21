package com.cryptocarver.utils;

import com.cryptocarver.model.HistoryItem;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoryComparatorTest {

    @Test
    void testCombineAndOverride() {
        HistoryItem item1 = new HistoryItem(
                "Op",
                "Details",
                Map.of("uiKey1", "uiVal1", "conflictKey", "uiConflictVal1")
        );
        item1.setStructuredDetails(List.of(OperationDetail.publicDetail("conflictKey", "structConflictVal1")));

        HistoryItem item2 = new HistoryItem(
                "Op",
                "Details",
                Map.of("uiKey1", "uiVal2", "conflictKey", "uiConflictVal2")
        );
        item2.setStructuredDetails(List.of(OperationDetail.publicDetail("conflictKey", "structConflictVal2")));

        List<HistoryComparator.DiffEntry> diffs = HistoryComparator.compare(item1, item2);

        // We expect uiKey1 to have values from map, and conflictKey to have values from structured details
        assertEquals(2, diffs.size());

        HistoryComparator.DiffEntry uiDiff = diffs.stream().filter(d -> d.key.equals("uiKey1")).findFirst().get();
        assertEquals("uiVal1", uiDiff.value1);
        assertEquals("uiVal2", uiDiff.value2);
        assertTrue(uiDiff.isDifferent);

        HistoryComparator.DiffEntry structDiff = diffs.stream().filter(d -> d.key.equals("conflictKey")).findFirst().get();
        assertEquals("structConflictVal1", structDiff.value1);
        assertEquals("structConflictVal2", structDiff.value2);
        assertTrue(structDiff.isDifferent);
    }

    @Test
    void maskedComparisonKeepsDifferenceSignalWithoutRevealingSensitiveValues() {
        HistoryItem item1 = new HistoryItem("One", "", Map.of("unclassifiedState", "do-not-show"));
        item1.setStructuredDetails(List.of(
                OperationDetail.publicDetail("Algorithm", "SHA-256"),
                OperationDetail.sensitiveDetail("Input", "first secret input"),
                OperationDetail.secretDetail("Private key", "private-one")));
        HistoryItem item2 = new HistoryItem("Two", "", Map.of("unclassifiedState", "also-do-not-show"));
        item2.setStructuredDetails(List.of(
                OperationDetail.publicDetail("Algorithm", "SHA-256"),
                OperationDetail.sensitiveDetail("Input", "second secret input"),
                OperationDetail.secretDetail("Private key", "private-two")));

        List<HistoryComparator.DiffEntry> masked = HistoryComparator.compare(item1, item2, SecretVisibility.MASKED);
        HistoryComparator.DiffEntry input = masked.stream().filter(d -> d.key.equals("Input")).findFirst().orElseThrow();
        assertEquals("***MASKED***", input.value1);
        assertEquals("***MASKED***", input.value2);
        assertTrue(input.isDifferent);
        assertFalse(masked.stream().anyMatch(d -> d.value1.contains("secret input") || d.value2.contains("secret input")));
        assertFalse(masked.stream().anyMatch(d -> d.key.equals("unclassifiedState")));

        List<HistoryComparator.DiffEntry> redacted = HistoryComparator.compare(item1, item2, SecretVisibility.REDACTED);
        assertFalse(redacted.stream().anyMatch(d -> d.key.equals("Private key")));

        List<HistoryComparator.DiffEntry> unsafe = HistoryComparator.compare(item1, item2, SecretVisibility.FULL_LAB);
        HistoryComparator.DiffEntry unsafeInput = unsafe.stream().filter(d -> d.key.equals("Input")).findFirst().orElseThrow();
        assertEquals("first secret input", unsafeInput.value1);
        assertEquals("second secret input", unsafeInput.value2);
        assertFalse(unsafe.stream().anyMatch(d -> d.key.equals("unclassifiedState")),
                "UI state is not an operation result and must never pollute the comparison");
    }
}
