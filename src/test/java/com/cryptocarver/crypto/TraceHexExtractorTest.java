package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceHexExtractorTest {
    @Test
    void extractsTcpdumpStyleBytePairsWithoutTreatingOffsetsAsPayload() {
        String trace = "0000: 01 02 03 04  5a 6b 7c 8d\n0008: DEADBEEF";
        TraceHexExtractor.Extraction result = TraceHexExtractor.extract(trace);
        assertEquals("010203045A6B7C8DDEADBEEF", result.hex());
        assertEquals(12, result.byteCount());
    }

    @Test
    void rejectsTextWithoutACompleteExplicitPayload() {
        assertThrows(IllegalArgumentException.class, () -> TraceHexExtractor.extract("port 443, id 01"));
    }
}
