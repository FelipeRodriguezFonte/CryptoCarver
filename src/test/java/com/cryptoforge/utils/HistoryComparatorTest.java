package com.cryptoforge.utils;

import com.cryptoforge.model.HistoryItem;
import com.cryptoforge.model.OperationDetail;
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
}
