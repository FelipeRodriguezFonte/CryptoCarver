package com.cryptocarver.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScreenConfigurationCodecTest {

    private ScreenConfiguration sample() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("CipherController.symmetricAlgorithmCombo", "AES");
        state.put("CipherController.cipherModeCombo", "GCM");
        state.put("CipherController.symmetricKeyField", "00112233445566778899AABBCCDDEEFF");
        state.put("CipherController.ivField", "00112233445566778899AABB");
        state.put("CipherController.fileCipherCompactCbcCheck", true);
        return new ScreenConfiguration("Symmetric Ciphers", "CIPHER", state);
    }

    @Test
    void plainConfigurationRoundTripsTypedValues() {
        ScreenConfiguration decoded = ScreenConfigurationCodec.decode(
                ScreenConfigurationCodec.encodePlain(sample()), null);
        assertEquals("Symmetric Ciphers", decoded.operation());
        assertEquals("CIPHER", decoded.module());
        assertEquals("GCM", decoded.toState().get("CipherController.cipherModeCombo"));
        assertEquals(true, decoded.toState().get("CipherController.fileCipherCompactCbcCheck"));
        assertTrue(decoded.mayContainSecrets());
    }

    @Test
    void encryptedConfigurationRequiresTheCorrectPasswordAndDetectsModification() {
        String encrypted = ScreenConfigurationCodec.encodeEncrypted(sample(), "correct horse".toCharArray());
        assertTrue(ScreenConfigurationCodec.isEncrypted(encrypted));
        ScreenConfiguration decoded = ScreenConfigurationCodec.decode(encrypted, "correct horse".toCharArray());
        assertEquals("00112233445566778899AABBCCDDEEFF",
                decoded.toState().get("CipherController.symmetricKeyField"));

        IllegalArgumentException wrongPassword = assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationCodec.decode(encrypted, "wrong password".toCharArray()));
        assertTrue(wrongPassword.getMessage().contains("password")
                || wrongPassword.getMessage().contains("modified"));

        String marker = "\"ciphertext\": \"";
        int ciphertextStart = encrypted.indexOf(marker) + marker.length();
        assertTrue(ciphertextStart >= marker.length());
        char original = encrypted.charAt(ciphertextStart);
        char replacement = original == 'A' ? 'B' : 'A';
        String modified = encrypted.substring(0, ciphertextStart)
                + replacement
                + encrypted.substring(ciphertextStart + 1);
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationCodec.decode(modified, "correct horse".toCharArray()));
    }

    @Test
    void rejectsUnknownFormatsAndWeakPasswords() {
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfiguration.fromJson("{\"format\":\"foreign\",\"version\":1}"));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfiguration.fromJson("{\"format\":\"cryptocarver-screen-configuration\","
                        + "\"version\":3,\"operation\":\"Hashing\",\"module\":\"GENERIC\",\"values\":{}}"));
        assertThrows(IllegalArgumentException.class,
                () -> ScreenConfigurationCodec.encodeEncrypted(sample(), "short".toCharArray()));
    }

    @Test
    void acceptsLegacyVersionOneDocuments() {
        String legacy = sample().toJson().replaceFirst("\"version\": 2", "\"version\": 1");
        ScreenConfiguration decoded = ScreenConfiguration.fromJson(legacy);
        assertEquals(1, decoded.version());
        assertEquals("Symmetric Ciphers", decoded.operation());
    }
}
