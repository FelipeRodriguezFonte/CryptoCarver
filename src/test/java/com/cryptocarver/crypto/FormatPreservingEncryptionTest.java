package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class FormatPreservingEncryptionTest {
    private static final String DECIMAL = "0123456789";
    private static final byte[] AES128 = HexFormat.of().parseHex("2b7e151628aed2a6abf7158809cf4f3c");
    private static final byte[] AES192 = HexFormat.of().parseHex(
            "2b7e151628aed2a6abf7158809cf4f3cef4359d8d580aa4f");
    private static final byte[] AES256 = HexFormat.of().parseHex(
            "2b7e151628aed2a6abf7158809cf4f3cef4359d8d580aa4f7f036d6f04fc6a94");

    @Test
    void nistFf1Aes128DecimalVector() {
        String encrypted = FormatPreservingEncryption.encrypt("0123456789", AES128, DECIMAL, new byte[0]);
        assertEquals("2433477484", encrypted);
        assertEquals("0123456789", FormatPreservingEncryption.decrypt(encrypted, AES128, DECIMAL, new byte[0]));
    }

    @Test
    void nistFf1Aes192AndAes256DecimalVectors() {
        assertEquals("2830668132", FormatPreservingEncryption.encrypt("0123456789", AES192, DECIMAL, new byte[0]));
        assertEquals("6657667009", FormatPreservingEncryption.encrypt("0123456789", AES256, DECIMAL, new byte[0]));
    }

    @Test
    void roundTripWithTweakAndHexAlphabet() {
        String alphabet = "0123456789abcdef";
        String input = "deadbeef01";
        byte[] key = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f");
        byte[] tweak = HexFormat.of().parseHex("39383736353433323130");
        String ciphertext = FormatPreservingEncryption.encrypt(input, key, alphabet, tweak);
        assertEquals(input.length(), ciphertext.length());
        assertEquals(input, FormatPreservingEncryption.decrypt(ciphertext, key, alphabet, tweak));
    }

    @Test
    void ff3_1RoundTripWithRequiredSevenByteTweak() {
        String input = "890121234567890000";
        byte[] tweak = HexFormat.of().parseHex("39383736353433");
        String ciphertext = FormatPreservingEncryption.encrypt(
                FormatPreservingEncryption.Algorithm.FF3_1, input, AES128, DECIMAL, tweak);
        assertEquals(input.length(), ciphertext.length());
        assertEquals(input, FormatPreservingEncryption.decrypt(
                FormatPreservingEncryption.Algorithm.FF3_1, ciphertext, AES128, DECIMAL, tweak));
    }

    @Test
    void ff3_1CompatibilityVector() {
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String plaintext = "YbpT3hDo0J9xwCQ5qUWt93iv";
        byte[] key = HexFormat.of().parseHex("7793833ce891b496381bd5b882f77ea1");
        byte[] tweak = HexFormat.of().parseHex("c58797c2580174");
        String ciphertext = FormatPreservingEncryption.encrypt(
                FormatPreservingEncryption.Algorithm.FF3_1, plaintext, key, alphabet, tweak);
        assertEquals("dDEYxViK56lGbV1WdZTPTe4w", ciphertext);
        assertEquals(plaintext, FormatPreservingEncryption.decrypt(
                FormatPreservingEncryption.Algorithm.FF3_1, ciphertext, key, alphabet, tweak));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> FormatPreservingEncryption.encrypt("0123456789", new byte[15], DECIMAL, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> FormatPreservingEncryption.encrypt("0123456789", AES128, "0012345678", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> FormatPreservingEncryption.encrypt("012345678x", AES128, DECIMAL, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> FormatPreservingEncryption.encrypt("01234", AES128, DECIMAL, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> FormatPreservingEncryption.encrypt("0123456789", AES128, DECIMAL, null));
        assertThrows(IllegalArgumentException.class,
                () -> FormatPreservingEncryption.encrypt(FormatPreservingEncryption.Algorithm.FF3_1,
                        "0123456789", AES128, DECIMAL, new byte[6]));
    }
}
