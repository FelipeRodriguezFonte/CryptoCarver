package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ModernMainControllerFormatNormalizationTest {

    @Test
    void mapsLegacyPlainTextLabelsToTheToolbarTextUtf8Value() {
        assertEquals("Text (UTF-8)", ModernMainController.normalizeToolbarFormat("Plain Text"));
        assertEquals("Text (UTF-8)", ModernMainController.normalizeToolbarFormat("plain text"));
        assertEquals("Text (UTF-8)", ModernMainController.normalizeToolbarFormat("Text"));
    }

    @Test
    void preservesCanonicalAndTechnicalFormatNames() {
        assertEquals("Text (UTF-8)", ModernMainController.normalizeToolbarFormat("Text (UTF-8)"));
        assertEquals("Hexadecimal", ModernMainController.normalizeToolbarFormat("Hexadecimal"));
        assertEquals("Base64", ModernMainController.normalizeToolbarFormat("Base64"));
        assertNull(ModernMainController.normalizeToolbarFormat(null));
    }
}
