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
