package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCipherInputsTest {
    private static final String KEY = "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF";

    @Test
    void gcmChoiceIsAuthenticatedAes() {
        FileCipherInputs.Parameters parameters = FileCipherInputs.parameters("AES-256-GCM", KEY, false,
                "CAFEBABEFACEDBADDECAF888", "");

        assertEquals("AES-256", parameters.algorithm());
        assertEquals("GCM", parameters.mode());
        assertTrue(parameters.aead());
        assertEquals(12, parameters.nonce().length);
        assertNull(parameters.aad());
    }

    @Test
    void chachaChoiceIsAuthenticatedWithoutMode() {
        FileCipherInputs.Parameters parameters = FileCipherInputs.parameters("ChaCha20-Poly1305", KEY, false,
                "000000000000000000000001", "FEED");

        assertEquals("ChaCha20-Poly1305", parameters.algorithm());
        assertEquals("", parameters.mode());
        assertTrue(parameters.aead());
        assertArrayEquals(new byte[] {(byte) 0xFE, (byte) 0xED}, parameters.aad());
    }

    @Test
    void lineModeIgnoresTheNonceExceptForCbc() {
        assertNull(FileCipherInputs.parameters("AES-256-GCM", KEY, true, "", "").nonce());
        assertEquals(16, FileCipherInputs.parameters("AES-256-CBC", KEY, true,
                "000102030405060708090A0B0C0D0E0F", "").nonce().length);
        assertFalse(FileCipherInputs.parameters("AES-256-CTR", KEY, false,
                "000102030405060708090A0B0C0D0E0F", "").aead());
    }

    @Test
    void whitespaceInHexIsIgnored() {
        assertArrayEquals(new byte[] {0x0A, 0x0B}, FileCipherInputs.requiredHex(" 0A 0B\n", "Key"));
    }

    @Test
    void missingAndMalformedValuesKeepTheirMessages() {
        assertEquals("Key is required",
                assertThrows(IllegalArgumentException.class, () -> FileCipherInputs.requiredHex(" ", "Key")).getMessage());
        assertEquals("AAD must be hexadecimal",
                assertThrows(IllegalArgumentException.class, () -> FileCipherInputs.optionalHex("zz", "AAD")).getMessage());
        assertEquals("Source file path is required",
                assertThrows(IllegalArgumentException.class, () -> FileCipherInputs.requiredPath("", "Source file path")).getMessage());
        assertEquals(Path.of("/tmp/a.bin"), FileCipherInputs.requiredPath("  /tmp/a.bin ", "Source"));
    }
}
