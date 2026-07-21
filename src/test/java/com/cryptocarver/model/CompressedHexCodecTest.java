package com.cryptocarver.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CompressedHexCodecTest {
    @Test
    void expandsAndCompressesTheHostTwoRowRepresentation() {
        assertEquals("ACFAD11223", CompressedHexCodec.expandTwoRows("AFD12\nCA123"));
        assertEquals("AFD12" + System.lineSeparator() + "CA123",
                CompressedHexCodec.compressToTwoRows("ACFAD11223"));
    }

    @Test
    void rejectsAmbiguousRowsAndInvalidHex() {
        assertThrows(IllegalArgumentException.class, () -> CompressedHexCodec.expandTwoRows("ABC\n12"));
        assertThrows(IllegalArgumentException.class, () -> CompressedHexCodec.expandTwoRows("ABG\n123"));
        assertThrows(IllegalArgumentException.class, () -> CompressedHexCodec.compressToTwoRows("ABC"));
    }
}
