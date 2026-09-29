package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenConfigurationImportCharacterizationTest {
    private static final String PASSWORD = "SYNTHETIC-PASSWORD";

    private ScreenConfiguration sample() {
        return new ScreenConfiguration("Symmetric Ciphers", "CIPHER", Map.of(), SecretVisibilityProfile.FULL_LAB);
    }

    @Test
    void wrongPasswordCurrentlyThrowsIllegalArgumentException() {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray());
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationCodec.decode(encrypted, "SYNTHETIC-WRONG".toCharArray()));
    }

    @Test
    void modifiedCiphertextCurrentlyThrowsIllegalArgumentException() {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray());
        String marker = "\"ciphertext\": \"";
        int start = encrypted.indexOf(marker) + marker.length();
        char original = encrypted.charAt(start);
        String modified = encrypted.substring(0, start) + (original == 'A' ? 'B' : 'A') + encrypted.substring(start + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationCodec.decode(modified, PASSWORD.toCharArray()));
    }

    @Test
    void nonJsonCurrentlyThrowsRuntimeException() {
        assertThrows(RuntimeException.class, () -> ScreenConfigurationCodec.decode("not json", null));
    }

    @Test
    void validJsonThatIsNotConfigurationCurrentlyThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationCodec.decode("{\"hello\":\"world\"}", null));
    }

    @Test
    void unsupportedVersionCurrentlyThrowsIllegalArgumentException() {
        String json = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 99");
        assertThrows(IllegalArgumentException.class, () -> ScreenConfigurationCodec.decode(json, null));
    }

    @Test
    void emptyDocumentCurrentlyThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ScreenConfigurationCodec.decode("", null));
    }

    @Test
    void validPlainDocumentCurrentlyDecodes() {
        assertDoesNotThrow(() -> ScreenConfigurationCodec.decode(ScreenConfigurationCodec.encodePlain(sample()), null));
    }

    @Test
    void validEncryptedDocumentCurrentlyDecodes() {
        assertDoesNotThrow(() -> ScreenConfigurationCodec.decode(
                ScreenConfigurationCodec.encodeEncrypted(sample(), PASSWORD.toCharArray()), PASSWORD.toCharArray()));
    }
}
