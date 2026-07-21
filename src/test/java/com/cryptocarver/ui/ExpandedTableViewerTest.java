package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExpandedTableViewerTest {

    @Test
    void csvEscapesQuotesAndKeepsColumnOrder() {
        String csv = ExpandedTableViewer.toCsv(List.of("Name", "Value"),
                List.of(List.of("Algorithm", "AES \"GCM\"")));
        assertEquals("\"Name\",\"Value\"\n\"Algorithm\",\"AES \"\"GCM\"\"\"", csv);
    }

    @Test
    void tsvKeepsTheVisibleColumnAndRowOrder() {
        assertEquals("First\tSecond\nA\tB\nC\tD", ExpandedTableViewer.toTsv(List.of("First", "Second"),
                List.of(List.of("A", "B"), List.of("C", "D"))));
    }

    @Test
    void jsonRowsUseStableKeysForBlankOrDuplicateHeaders() {
        List<Map<String, String>> rows = ExpandedTableViewer.toJsonRows(List.of("", "Value", "Value"),
                List.of(List.of("first", "second", "third")));
        assertEquals(Map.of("Column 1", "first", "Value", "second", "Value 3", "third"), rows.get(0));
        assertEquals(List.of("Column 1", "Value", "Value 3"), new ArrayList<>(rows.get(0).keySet()));
    }
}
