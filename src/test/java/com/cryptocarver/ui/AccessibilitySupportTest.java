package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessibilitySupportTest {
    @Test
    void humanizesFxIdsForAccessibleNames() {
        assertEquals("Open Clipboard Shelf", AccessibilitySupport.humanize("openClipboardShelf"));
        assertEquals("Button", AccessibilitySupport.humanize(""));
        assertEquals("Button", AccessibilitySupport.humanize(null));
    }
}
