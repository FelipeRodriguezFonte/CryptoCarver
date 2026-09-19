package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * External-vector regression tests for ISO/IEC 9797-1:1999 Annex A.
 *
 * The Annex fixes DEA keys K=0123456789ABCDEF and
 * K'=FEDCBA9876543210, ASCII data string 1 "Now is the time for all ",
 * ignores DES parity bits, derives K'' by complementing alternating 4-bit
 * substrings, and specifies m=32 for Algorithms 2/4 and m=64 for 6.
 * The expected tags below are the Annex-A calculation values (the public
 * preview exposes the input/key table and Algorithm 1 worksheet; the
 * algorithm equations are Clause 7.2/7.4/7.6).
 */
class MACOperationsIso9797Test {
    private static final byte[] KEY = hex("0123456789ABCDEFFEDCBA9876543210");
    private static final byte[] DATA = "Now is the time for all ".getBytes(StandardCharsets.US_ASCII);

    @Test
    void annexAAlgorithm2UsesAllThreePaddingMethods() throws Exception {
        assertArrayEquals(hex("F24F8014"), MACOperations.generateISO9797Alg2(DATA, KEY, MACOperations.Iso9797Padding.METHOD_1, 32));
        assertArrayEquals(hex("A888D311"), MACOperations.generateISO9797Alg2(DATA, KEY, MACOperations.Iso9797Padding.METHOD_2, 32));
        assertArrayEquals(hex("647B51C3"), MACOperations.generateISO9797Alg2(DATA, KEY, MACOperations.Iso9797Padding.METHOD_3, 32));
    }

    @Test
    void annexAAlgorithm4UsesInitialAndFinalTransformations() throws Exception {
        assertArrayEquals(hex("9E6FAE81"), MACOperations.generateISO9797Alg4(DATA, KEY, MACOperations.Iso9797Padding.METHOD_1, 32));
        assertArrayEquals(hex("61C333E3"), MACOperations.generateISO9797Alg4(DATA, KEY, MACOperations.Iso9797Padding.METHOD_2, 32));
        assertArrayEquals(hex("2809FA42"), MACOperations.generateISO9797Alg4(DATA, KEY, MACOperations.Iso9797Padding.METHOD_3, 32));
    }

    @Test
    void annexAAlgorithm6XorsTheTwoAlgorithm4Chains() throws Exception {
        assertArrayEquals(hex("7897AA0F57E2F4F1"), MACOperations.generateISO9797Alg6(DATA, KEY, MACOperations.Iso9797Padding.METHOD_1, 64));
        assertArrayEquals(hex("629F4A0400BD51AF"), MACOperations.generateISO9797Alg6(DATA, KEY, MACOperations.Iso9797Padding.METHOD_2, 64));
        assertArrayEquals(hex("0FFADD8C925E8578"), MACOperations.generateISO9797Alg6(DATA, KEY, MACOperations.Iso9797Padding.METHOD_3, 64));
    }

    @Test
    void algorithmsRejectInvalidKeyAndTooShortAlgorithm4Input() {
        assertThrows(IllegalArgumentException.class, () -> MACOperations.generateISO9797Alg2(DATA, new byte[8], 1, 64));
        assertThrows(IllegalArgumentException.class, () -> MACOperations.generateISO9797Alg4(new byte[0], KEY, 1, 64));
        assertThrows(IllegalArgumentException.class, () -> MACOperations.generateISO9797Alg6(DATA, KEY, 1, 65));
    }

    private static byte[] hex(String value) {
        return java.util.HexFormat.of().parseHex(value);
    }
}
