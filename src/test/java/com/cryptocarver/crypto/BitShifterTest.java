package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitShifterTest {
    @Test
    void shiftsAcrossByteBoundariesAtFixedWidth() {
        assertArrayEquals(hex("4680"), BitShifter.left(hex("91A0"), 2));
        assertArrayEquals(hex("2468"), BitShifter.right(hex("91A0"), 2));
        assertArrayEquals(hex("0000"), BitShifter.left(hex("91A0"), 16));
    }

    @Test
    void rejectsNegativeShiftCounts() {
        assertThrows(IllegalArgumentException.class, () -> BitShifter.right(new byte[] {1}, -1));
    }

    private static byte[] hex(String value) { return java.util.HexFormat.of().parseHex(value); }
}
