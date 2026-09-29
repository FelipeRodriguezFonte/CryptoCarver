package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenConfigurationImportFailureTest {
    private static final String PASSWORD = "SYNTHETIC-PASSWORD";

    private ScreenConfiguration sample() {
        return new ScreenConfiguration("Symmetric Ciphers", "CIPHER", Map.of(), SecretVisibilityProfile.FULL_LAB);
    }

    @Test
    void wrongPasswordHasTypedSafeReason() {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray());
        ScreenConfigurationImportException failure = assertThrows(ScreenConfigurationImportException.class,
                () -> ScreenConfigurationCodec.decode(encrypted, "SYNTHETIC-WRONG".toCharArray()));
        assertEquals(ScreenConfigurationImportException.Reason.WRONG_PASSWORD_OR_TAMPERED, failure.reason());
        assertFalse(failure.getMessage().contains("SYNTHETIC-WRONG"));
        assertFalse(failure.toString().contains("SYNTHETIC-WRONG"));
    }

    @Test
    void modifiedCiphertextHasSameTypedReasonAsWrongPassword() {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray());
        ScreenConfigurationImportException failure = assertThrows(ScreenConfigurationImportException.class,
                () -> ScreenConfigurationCodec.decode(modifyCiphertext(encrypted), PASSWORD.toCharArray()));
        assertEquals(ScreenConfigurationImportException.Reason.WRONG_PASSWORD_OR_TAMPERED, failure.reason());
    }

    @Test
    void invalidJsonIsNotAConfiguration() {
        assertReason(ScreenConfigurationImportException.Reason.NOT_A_CONFIGURATION, "not json", null);
    }

    @Test
    void unrelatedJsonIsNotAConfiguration() {
        assertReason(ScreenConfigurationImportException.Reason.NOT_A_CONFIGURATION, "{\"hello\":\"world\"}", null);
    }

    @Test
    void foreignFormatWithLargeVersionIsNotAConfiguration() {
        assertReason(ScreenConfigurationImportException.Reason.NOT_A_CONFIGURATION,
                "{\"format\":\"foreign\",\"version\":99}", null);
    }

    @Test
    void unsupportedPlainVersionHasTypedReason() {
        String document = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 99");
        assertReason(ScreenConfigurationImportException.Reason.UNSUPPORTED_VERSION, document, null);
    }

    @Test
    void belowMinimumPlainVersionHasTypedReason() {
        String document = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 0");
        assertReason(ScreenConfigurationImportException.Reason.UNSUPPORTED_VERSION, document, null);
    }

    @Test
    void unsupportedEncryptedVersionHasTypedReason() {
        String document = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray())
                .replaceFirst("\"version\": 1", "\"version\": 99");
        assertReason(ScreenConfigurationImportException.Reason.UNSUPPORTED_VERSION, document, PASSWORD.toCharArray());
    }

    @Test
    void damagedEncryptedHeaderIsUnreadable() {
        String document = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray())
                .replace(ScreenConfigurationCodec.ENVELOPE_FORMAT, "damaged-envelope");
        assertReason(ScreenConfigurationImportException.Reason.UNREADABLE, document, null);
    }

    @Test
    void emptyDocumentHasTypedReason() {
        assertReason(ScreenConfigurationImportException.Reason.EMPTY, "  ", null);
    }

    private void assertReason(ScreenConfigurationImportException.Reason reason, String document, char[] password) {
        ScreenConfigurationImportException failure = assertThrows(ScreenConfigurationImportException.class,
                () -> ScreenConfigurationCodec.decode(document, password));
        assertEquals(reason, failure.reason());
    }

    private String modifyCiphertext(String encrypted) {
        String marker = "\"ciphertext\": \"";
        int start = encrypted.indexOf(marker) + marker.length();
        char original = encrypted.charAt(start);
        return encrypted.substring(0, start) + (original == 'A' ? 'B' : 'A') + encrypted.substring(start + 1);
    }
}
