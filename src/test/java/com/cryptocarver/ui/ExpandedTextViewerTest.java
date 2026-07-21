package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExpandedTextViewerTest {

    @Test
    void findsAllNonOverlappingOccurrencesCaseInsensitively() {
        assertEquals(List.of(0, 6, 12), ExpandedTextViewer.findOccurrences("Alpha ALPHA alpha", "alpha"));
    }

    @Test
    void doesNotTreatBlankSearchAsAMatch() {
        assertEquals(List.of(), ExpandedTextViewer.findOccurrences("result", "  "));
    }
}
