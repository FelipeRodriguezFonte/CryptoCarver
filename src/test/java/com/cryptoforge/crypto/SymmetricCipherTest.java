package com.cryptoforge.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SymmetricCipherTest {
    @Test
    void aesGcmRoundTripWithAad() throws Exception {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        byte[] aad = "header".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "CryptoCarver".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = SymmetricCipher.encrypt(plain, key, "AES-128", "GCM", "NoPadding", iv, aad);
        assertArrayEquals(plain, SymmetricCipher.decrypt(cipher, key, "AES-128", "GCM", "NoPadding", iv, aad));
    }

    @Test
    void rejectsInvalidAesKeyAndGcmIv() {
        assertThrows(IllegalArgumentException.class,
                () -> SymmetricCipher.encrypt(new byte[] {1}, new byte[15], "AES-128", "GCM", "NoPadding", new byte[12]));
        assertThrows(IllegalArgumentException.class,
                () -> SymmetricCipher.encrypt(new byte[] {1}, new byte[16], "AES-128", "GCM", "NoPadding", new byte[7]));
    }

    @Test
    void macVerificationDetectsModifiedDataAndMac() throws Exception {
        byte[] key = new byte[32];
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        byte[] mac = MACOperations.generate(data, key, "HMAC-SHA256");
        assertTrue(MACOperations.verify(data, mac, key, "HMAC-SHA256"));
        assertFalse(MACOperations.verify("Data".getBytes(StandardCharsets.UTF_8), mac, key, "HMAC-SHA256"));
        mac[0] ^= 1;
        assertFalse(MACOperations.verify(data, mac, key, "HMAC-SHA256"));
    }
}
