package com.cryptocarver.crypto;

import com.cryptocarver.crypto.HashOperations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.security.NoSuchAlgorithmException;

/**
 * Unit tests for HashOperations
 */
class HashOperationsTest {

    @Test
    void testMD5Hash() throws NoSuchAlgorithmException {
        byte[] data = "Hello World".getBytes();
        byte[] hash = HashOperations.calculateHash(data, "MD5");

        assertNotNull(hash);
        assertEquals(16, hash.length); // MD5 produces 16 bytes
    }

    @Test
    void testSHA256Hash() throws NoSuchAlgorithmException {
        byte[] data = "Hello World".getBytes();
        byte[] hash = HashOperations.calculateHash(data, "SHA-256");

        assertNotNull(hash);
        assertEquals(32, hash.length); // SHA-256 produces 32 bytes
    }

    @Test
    void testSHA512Hash() throws NoSuchAlgorithmException {
        byte[] data = "Test Data".getBytes();
        byte[] hash = HashOperations.calculateHash(data, "SHA-512");

        assertNotNull(hash);
        assertEquals(64, hash.length); // SHA-512 produces 64 bytes
    }

    @Test
    void testCRC32() throws NoSuchAlgorithmException {
        byte[] data = "Test".getBytes();
        byte[] crc = HashOperations.calculateHash(data, "CRC32");

        assertNotNull(crc);
        assertEquals(4, crc.length); // CRC32 produces 4 bytes
    }

    @Test
    void legacyPublicDigestsAreAvailableThroughBouncyCastle() throws Exception {
        assertEquals("A448017AAF21D8525FC10AE87AA6729D", hex(HashOperations.calculateHash("abc".getBytes(), "MD4")));
        assertEquals(64, HashOperations.calculateHash("abc".getBytes(), "WHIRLPOOL").length);
        assertEquals(24, HashOperations.calculateHash("abc".getBytes(), "TIGER-192").length);
    }

    @Test
    void crc32VariantsMatchTheirPublishedCheckValuesFor123456789() {
        byte[] vector = "123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals("CBF43926", hex(HashOperations.calculateCrc32(vector, HashOperations.Crc32Variant.ISO_HDLC)));
        assertEquals("FC891918", hex(HashOperations.calculateCrc32(vector, HashOperations.Crc32Variant.BZIP2)));
        assertEquals("0376E6E7", hex(HashOperations.calculateCrc32(vector, HashOperations.Crc32Variant.MPEG2)));
        assertEquals("765E7680", hex(HashOperations.calculateCrc32(vector, HashOperations.Crc32Variant.POSIX)));
        assertEquals("340BC6D9", hex(HashOperations.calculateCrc32(vector, HashOperations.Crc32Variant.JAMCRC)));
        assertEquals("E3069283", hex(HashOperations.calculateCrc32(vector, HashOperations.Crc32Variant.CASTAGNOLI)));
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().withUpperCase().formatHex(bytes);
    }

    @Test
    void testNullDataThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            HashOperations.calculateHash(null, "SHA-256");
        });
    }

    @Test
    void testIsSupported() {
        assertTrue(HashOperations.isSupported("MD5"));
        assertTrue(HashOperations.isSupported("SHA-256"));
        assertTrue(HashOperations.isSupported("SHA-512"));
        assertTrue(HashOperations.isSupported("CRC32"));
        assertFalse(HashOperations.isSupported("INVALID"));
        assertFalse(HashOperations.isSupported(null));
    }
}
