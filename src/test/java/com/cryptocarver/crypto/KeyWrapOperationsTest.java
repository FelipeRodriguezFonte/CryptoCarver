package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyWrapOperationsTest {
    private static final byte[] KEK = DataConverter.hexToBytes(KdfWrapTestVectors.KEK_128);

    @Test
    void rfc3394MatchesPublishedVectorAndRoundTrips() throws Exception {
        byte[] keyData = DataConverter.hexToBytes(KdfWrapTestVectors.KEY_DATA_128);
        byte[] wrapped = KeyWrapOperations.wrapRfc3394(KEK, keyData);
        assertArrayEquals(DataConverter.hexToBytes(KdfWrapTestVectors.RFC3394_WRAPPED_KEY_DATA_128), wrapped);
        assertArrayEquals(keyData, KeyWrapOperations.unwrapRfc3394(KEK, wrapped));
    }

    @Test
    void rfc5649RoundTripsShortUnalignedKeyDataAndDetectsTampering() throws Exception {
        byte[] keyData = DataConverter.hexToBytes(KdfWrapTestVectors.KWP_SHORT_PLAINTEXT);
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
