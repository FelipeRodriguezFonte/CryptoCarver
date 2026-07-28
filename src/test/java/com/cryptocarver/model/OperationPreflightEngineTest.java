package com.cryptocarver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OperationPreflightEngine Pure Inspection Tests")
class OperationPreflightEngineTest {

    @Test
    @DisplayName("Symmetric Cipher: Empty input yields INCOMPLETE status")
    void testSymmetricCipherEmptyInput() {
        PreflightReport report = OperationPreflightEngine.checkSymmetricCipher(
                "", "Plain Text", "AES-256", "CBC", "PKCS5Padding",
                "Manual Input", "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF",
                null, false, "0102030405060708090A0B0C0D0E0F10", null, null, true
        );

        assertEquals(PreflightStatus.INCOMPLETE, report.getOverallStatus());
        assertFalse(report.isExecutable());
        assertNotNull(report.getFirstNonReadyCheck());
        assertEquals("cipherInputArea", report.getFirstNonReadyCheck().getTargetControlKey());
    }

    @Test
    @DisplayName("Symmetric Cipher: Invalid hex key yields BLOCKED status")
    void testSymmetricCipherInvalidKeyHex() {
        PreflightReport report = OperationPreflightEngine.checkSymmetricCipher(
                "Hello World", "Plain Text", "AES-256", "CBC", "PKCS5Padding",
                "Manual Input", "INVALID_HEX_KEY_12345",
                null, false, "0102030405060708090A0B0C0D0E0F10", null, null, true
        );

        assertEquals(PreflightStatus.BLOCKED, report.getOverallStatus());
        assertFalse(report.isExecutable());
        assertEquals("symmetricKeyField", report.getFirstNonReadyCheck().getTargetControlKey());
    }

    @Test
    @DisplayName("Symmetric Cipher: Metadata-Only key yields BLOCKED status")
    void testSymmetricCipherMetadataOnlyKey() {
        PreflightReport report = OperationPreflightEngine.checkSymmetricCipher(
                "Hello World", "Plain Text", "AES-256", "GCM", "NoPadding",
                "Simulated HSM", null,
                "KEY-REF-001", true, "0102030405060708090A0B0C", null, null, true
        );

        assertEquals(PreflightStatus.BLOCKED, report.getOverallStatus());
        assertFalse(report.isExecutable());
        assertTrue(report.getSummaryMessage().toUpperCase().contains("BLOCKED"));
    }

    @Test
    @DisplayName("Symmetric Cipher: ECB mode yields WARNING status (Executable)")
    void testSymmetricCipherECBWarning() {
        PreflightReport report = OperationPreflightEngine.checkSymmetricCipher(
                "Hello World", "Plain Text", "AES-256", "ECB", "PKCS5Padding",
                "Manual Input", "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF",
                null, false, null, null, null, true
        );

        assertEquals(PreflightStatus.WARNING, report.getOverallStatus());
        assertTrue(report.isExecutable());
        assertTrue(report.getChecks().stream().anyMatch(c -> c.getMessage().contains("ECB mode does not use an IV")));
    }

    @Test
    @DisplayName("Symmetric Cipher: AES-GCM valid 12-byte IV yields READY status")
    void testSymmetricCipherGcmValidReady() {
        PreflightReport report = OperationPreflightEngine.checkSymmetricCipher(
                "Hello World", "Plain Text", "AES-256", "GCM", "NoPadding",
                "Manual Input", "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF",
                null, false, "0102030405060708090A0B0C", null, null, true
        );

        assertEquals(PreflightStatus.READY, report.getOverallStatus());
        assertTrue(report.isExecutable());
        assertNull(report.getFirstNonReadyCheck());
    }

    @Test
    @DisplayName("Symmetric Cipher: 3DES rejects unsupported key lengths")
    void testTripleDesInvalidKeyLength() {
        PreflightReport report = OperationPreflightEngine.checkSymmetricCipher(
                "Hello World", "Plain Text", "3DES", "CBC", "PKCS5Padding",
                "Manual Input", "00112233445566778899AABBCCDDEEFF00",
                null, false, "0102030405060708", null, null, true
        );

        assertEquals(PreflightStatus.BLOCKED, report.getOverallStatus());
        assertTrue(report.getChecks().stream().anyMatch(check -> check.getMessage().contains("3DES requires")));
    }

    @Test
    @DisplayName("Hashing: Empty payload yields INCOMPLETE")
    void testHashingEmptyPayload() {
        PreflightReport report = OperationPreflightEngine.checkHashing("", "Plain Text", "SHA-256");
        assertEquals(PreflightStatus.INCOMPLETE, report.getOverallStatus());
        assertEquals("hashInputArea", report.getFirstNonReadyCheck().getTargetControlKey());
    }

    @Test
    @DisplayName("Hashing: MD5 yields WARNING status")
    void testHashingMD5Warning() {
        PreflightReport report = OperationPreflightEngine.checkHashing("Sample Data", "Plain Text", "MD5");
        assertEquals(PreflightStatus.WARNING, report.getOverallStatus());
        assertTrue(report.isExecutable());
    }

    @Test
    @DisplayName("Digital Signature: Metadata-Only key yields BLOCKED")
    void testDigitalSignatureMetadataOnlyKey() {
        PreflightReport report = OperationPreflightEngine.checkDigitalSignature(
                "Payload to sign", "RSA-SHA256-PKCS1", "KEY-REF-001", true, true
        );

        assertEquals(PreflightStatus.BLOCKED, report.getOverallStatus());
        assertFalse(report.isExecutable());
    }

    @Test
    @DisplayName("MAC: selected HSM key is valid without manual key text")
    void testMacHsmKeyDoesNotRequireHiddenManualField() {
        PreflightReport report = OperationPreflightEngine.checkMac(
                "payload", "HMAC-SHA256", "Simulated HSM", "", "mac-key-01", false
        );

        assertEquals(PreflightStatus.READY, report.getOverallStatus());
        assertTrue(report.isExecutable());
    }

    @Test
    @DisplayName("RSA: public-key preflight focuses the cipher panel public key field")
    void testAsymmetricCipherUsesActualCipherKeyControl() {
        PreflightReport report = OperationPreflightEngine.checkAsymmetricCipher(
                "Payload", "", false, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", true
        );

        assertEquals(PreflightStatus.INCOMPLETE, report.getOverallStatus());
        assertEquals("publicKeyArea", report.getFirstNonReadyCheck().getTargetControlKey());
    }
}
