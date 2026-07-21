package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernMacOperationsTest {
    @Test
    void poly1305MatchesRfc8439Vector() {
        byte[] key = DataConverter.hexToBytes("85D6BE7857556D337F4452FE42D506A80103808AFB0DB2FD4ABFF6AF4149F51B");
        byte[] data = "Cryptographic Forum Research Group".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertArrayEquals(DataConverter.hexToBytes("A8061DC1305136C6C22B8BAF0C0127A9"),
                MACOperations.generatePoly1305(data, key));
    }

    @Test
    void gmacIsDeterministicForInputsAndChangesWithData() {
        byte[] key = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");
        byte[] nonce = DataConverter.hexToBytes("101112131415161718191A1B");
        byte[] tag = MACOperations.generateGmac(new byte[] {1, 2, 3}, key, nonce);
        assertTrue(MACOperations.constantTimeEquals(tag, MACOperations.generateGmac(new byte[] {1, 2, 3}, key, nonce)));
        assertFalse(MACOperations.constantTimeEquals(tag, MACOperations.generateGmac(new byte[] {1, 2, 4}, key, nonce)));
    }

    @Test
    void validatesModernMacInputsAndConstantTimeLengthMismatch() {
        assertThrows(IllegalArgumentException.class, () -> MACOperations.generatePoly1305(new byte[0], new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> MACOperations.generateGmac(new byte[0], new byte[16], new byte[11]));
        assertFalse(MACOperations.constantTimeEquals(new byte[] {1}, new byte[] {1, 0}));
    }
}
