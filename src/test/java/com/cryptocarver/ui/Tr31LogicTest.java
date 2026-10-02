package com.cryptocarver.ui;

import com.cryptocarver.crypto.Tr31TestVectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class Tr31LogicTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void unwrapsPublishedVectorsAndFormatsTheirAttributesWithoutFxml(int vector) throws Exception {
        var result = Tr31Logic.importBlock(Tr31TestVectors.protectionKeys[vector], Tr31TestVectors.blocks[vector]);
        assertEquals(Tr31TestVectors.keys[vector], result.unwrappedKey());
        assertEquals(String.valueOf(Tr31TestVectors.versions[vector]), result.header().versionId);
        assertTrue(result.report().contains("TR-31 KEY BLOCK IMPORT"));
        assertTrue(result.report().contains("Key Length: 16 bytes (32 hex characters)"));
    }

    @Test
    void validatesMissingProtectionKeyBeforeParsingSelectors() {
        var error = assertThrows(KeyDistributionValidation.class,
                () -> Tr31Logic.export("", "", null, null, null, null, null, "", key -> key));
        assertEquals("module.keys.tr31.required", error.messageKey());
        assertEquals("tr31KbpkExportField", error.fieldKey());
    }

    @Test
    void validatesInvalidKeyAgainstItsFieldWithoutLeakingItsValue() {
        var error = assertThrows(KeyDistributionValidation.class,
                () -> Tr31Logic.export(Tr31TestVectors.protectionKeys[1], "synthetic invalid value", null, null, null, null, null, "", key -> key));
        assertEquals("module.keys.tr31.keyInvalid", error.messageKey());
        assertEquals("tr31KeyToWrapField", error.fieldKey());
        assertFalse(error.toString().contains("synthetic invalid value"));
    }

    @Test
    void headerFormattingUsesSuppliedLocalizationAndPublicOptionalBlocks() throws Exception {
        var result = Tr31Logic.parseHeader(Tr31TestVectors.blocks[2], key -> "translated " + key);
        assertTrue(result.report().contains("translated module.keys.tr31.decoded"));
        assertTrue(result.report().contains("TRANSLATED MODULE.KEYS.TR31.HEADERWARNINGS"));
        assertTrue(result.report().contains("KS ("));
    }
}
