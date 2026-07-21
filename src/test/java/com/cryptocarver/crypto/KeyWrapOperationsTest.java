package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyWrapOperationsTest {
    private static final byte[] KEK = DataConverter.hexToBytes("000102030405060708090A0B0C0D0E0F");

    @Test
    void rfc3394MatchesPublishedVectorAndRoundTrips() throws Exception {
        byte[] keyData = DataConverter.hexToBytes("00112233445566778899AABBCCDDEEFF");
        byte[] wrapped = KeyWrapOperations.wrapRfc3394(KEK, keyData);
        assertArrayEquals(DataConverter.hexToBytes("1FA68B0A8112B447AEF34BD8FB5A7B829D3E862371D2CFE5"), wrapped);
        assertArrayEquals(keyData, KeyWrapOperations.unwrapRfc3394(KEK, wrapped));
    }

    @Test
    void rfc5649RoundTripsShortUnalignedKeyDataAndDetectsTampering() throws Exception {
        byte[] keyData = DataConverter.hexToBytes("466F7250617369");
        byte[] wrapped = KeyWrapOperations.wrapRfc5649(KEK, keyData);
        assertArrayEquals(keyData, KeyWrapOperations.unwrapRfc5649(KEK, wrapped));
        wrapped[0] ^= 0x01;
        assertThrows(InvalidCipherTextException.class, () -> KeyWrapOperations.unwrapRfc5649(KEK, wrapped));
    }

    @Test
    void rejectsWrongKekAndInvalidRfc3394Payload() {
        assertThrows(IllegalArgumentException.class,
                () -> KeyWrapOperations.wrapRfc3394(new byte[15], new byte[16]));
        assertThrows(IllegalArgumentException.class,
                () -> KeyWrapOperations.wrapRfc3394(KEK, new byte[8]));
    }
}
