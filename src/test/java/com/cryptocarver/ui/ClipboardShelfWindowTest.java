package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardShelfWindowTest {
    @Test
    void copyWorkspaceIncludesOnlyPublicValuesOutsideUnsafeLab() {
        ClipboardEntry publicEntry = new ClipboardEntry("Hash", "ABCDEF", ClipboardEntry.Format.HEX,
                OperationDetail.Classification.PUBLIC, "Hashing");
        ClipboardEntry secretEntry = new ClipboardEntry("Key", "TOP-SECRET", ClipboardEntry.Format.TEXT,
                OperationDetail.Classification.SECRET, "Key Generation");

        String safe = ClipboardShelfWindow.buildClipboardText(
                List.of(publicEntry, secretEntry), SecretVisibility.MASKED);
        assertTrue(safe.contains("ABCDEF"));
        assertFalse(safe.contains("TOP-SECRET"));

        String unsafe = ClipboardShelfWindow.buildClipboardText(
                List.of(publicEntry, secretEntry), SecretVisibility.FULL_LAB);
        assertTrue(unsafe.contains("ABCDEF"));
        assertTrue(unsafe.contains("TOP-SECRET"));
    }
}
