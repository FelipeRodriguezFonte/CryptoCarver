package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.AppSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class KeyCertificateWorkbenchControllerTest {

    private KeyCertificateWorkbenchController controller;

    @BeforeEach
    void setUp() {
        controller = new KeyCertificateWorkbenchController();
    }

    @AfterEach
    void tearDown() {
        // Reset singleton to FULL_LAB for other tests
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibility(com.cryptocarver.model.SecretVisibility.FULL_LAB);
    }

    @Test
    void testCanLoadFromShelf_SecretWithFullLab() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibility(com.cryptocarver.model.SecretVisibility.FULL_LAB);
        ClipboardEntry entry = new ClipboardEntry("Label", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.SECRET, "Source");
        assertTrue(controller.canLoadFromShelf(entry));
    }

    @Test
    void testCanLoadFromShelf_SecretWithMasked() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibility(com.cryptocarver.model.SecretVisibility.MASKED);
        ClipboardEntry entry = new ClipboardEntry("Label", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.SECRET, "Source");
        assertFalse(controller.canLoadFromShelf(entry));
    }

    @Test
    void testCanLoadFromShelf_SecretWithRedacted() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibility(com.cryptocarver.model.SecretVisibility.REDACTED);
        ClipboardEntry entry = new ClipboardEntry("Label", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.SECRET, "Source");
        assertFalse(controller.canLoadFromShelf(entry));
    }

    @Test
    void testCanLoadFromShelf_PublicWithRedacted() {
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibility(com.cryptocarver.model.SecretVisibility.REDACTED);
        ClipboardEntry entry = new ClipboardEntry("Label", "Value", ClipboardEntry.Format.TEXT, OperationDetail.Classification.PUBLIC, "Source");
        assertTrue(controller.canLoadFromShelf(entry));
    }

    @Test
    void testCanLoadFile_WithinLimit() throws IOException {
        File tempFile = File.createTempFile("test", ".txt");
        tempFile.deleteOnExit();
        assertTrue(controller.canLoadFile(tempFile));
    }

    @Test
    void testCanLoadFile_ExceedsLimit() throws IOException {
        File tempFile = File.createTempFile("test_large", ".txt");
        tempFile.deleteOnExit();
        // We don't want to actually write 1MB+ to disk just to test file.length(),
        // but java.io.File length() returns actual file size.
        // We'll just write 1024 * 1024 + 1 bytes.
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[10 * 1024 * 1024 + 1]);
        }
        assertFalse(controller.canLoadFile(tempFile));
    }
}
