package com.cryptocarver.crypto;

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
    void tripleDesDoubleLengthKeyUsesK1K2K1() throws Exception {
        byte[] key16 = hex("0123456789ABCDEFFEDCBA9876543210");
        byte[] key24 = hex("0123456789ABCDEFFEDCBA98765432100123456789ABCDEF");
        byte[] iv = hex("1234567890ABCDEF");
        byte[] plain = hex("00112233445566778899AABBCCDDEEFF");

        byte[] cipherWith16 = SymmetricCipher.encrypt(plain, key16, "3DES (Triple DES)", "CBC", "NoPadding", iv);
        byte[] cipherWith24 = SymmetricCipher.encrypt(plain, key24, "3DES (Triple DES)", "CBC", "NoPadding", iv);

        assertArrayEquals(cipherWith24, cipherWith16);
        assertArrayEquals(plain, SymmetricCipher.decrypt(cipherWith16, key16,
                "3DES (Triple DES)", "CBC", "NoPadding", iv));
    }

    @Test
    void tripleDesRejectsUnsupportedKeyLength() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SymmetricCipher.encrypt(new byte[8], new byte[17],
                        "3DES (Triple DES)", "ECB", "NoPadding", null));
        assertTrue(error.getMessage().contains("16- or 24-byte"));
    }

    @Test
    void recommendsIvLengthFromCipherBlockSizeAndMode() {
        org.junit.jupiter.api.Assertions.assertEquals(8,
                SymmetricCipher.getRecommendedIvLength("3DES (Triple DES)", "CBC"));
        org.junit.jupiter.api.Assertions.assertEquals(8,
                SymmetricCipher.getRecommendedIvLength("DES", "CBC"));
        org.junit.jupiter.api.Assertions.assertEquals(16,
                SymmetricCipher.getRecommendedIvLength("AES-256", "CBC"));
        org.junit.jupiter.api.Assertions.assertEquals(12,
                SymmetricCipher.getRecommendedIvLength("AES-256", "GCM"));
        org.junit.jupiter.api.Assertions.assertEquals(12,
                SymmetricCipher.getRecommendedIvLength("ChaCha20", "CBC"));
        org.junit.jupiter.api.Assertions.assertEquals(24,
                SymmetricCipher.getRecommendedIvLength("XChaCha20-Poly1305", "CBC"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                SymmetricCipher.getRecommendedIvLength("3DES (Triple DES)", "ECB"));
    }

    private static byte[] hex(String value) {
        return java.util.HexFormat.of().parseHex(value);
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
