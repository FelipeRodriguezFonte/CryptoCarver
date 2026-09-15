package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyOperationsKcvLengthTest {

    private static final byte[] AES_KEY = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");
    private static final byte[] DES_KEY = DataConverter.hexToBytes("0123456789ABCDEF");

    @Test
    void selectableKcvMethodsReturnFourBytesAndKeepTheLegacyPrefix() throws Exception {
        assertFourByteExtension(KeyOperations.calculateKCV_AES(AES_KEY), KeyOperations.calculateKCV_AES(AES_KEY, 4));
        assertFourByteExtension(KeyOperations.calculateKCV_VISA(DES_KEY), KeyOperations.calculateKCV_VISA(DES_KEY, 4));
        assertFourByteExtension(KeyOperations.calculateKCV_ATALLA(DES_KEY), KeyOperations.calculateKCV_ATALLA(DES_KEY, 4));
        assertFourByteExtension(KeyOperations.calculateKCV_SHA256(AES_KEY), KeyOperations.calculateKCV_SHA256(AES_KEY, 4));
        assertFourByteExtension(KeyOperations.calculateKCV_CMAC(AES_KEY), KeyOperations.calculateKCV_CMAC(AES_KEY, 4));
    }

    @Test
    void invalidRequestedLengthIsRejectedClearly() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> KeyOperations.calculateKCV_AES(AES_KEY, 17));
        assertEquals("KCV length must be between 1 and 16 bytes", error.getMessage());
    }

    private static void assertFourByteExtension(byte[] legacy, byte[] fourByte) {
        assertEquals(3, legacy.length);
        assertEquals(4, fourByte.length);
        assertArrayEquals(legacy, java.util.Arrays.copyOf(fourByte, legacy.length));
    }
}
