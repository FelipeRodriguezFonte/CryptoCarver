package com.cryptocarver.crypto.smartcard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtrParserTest {
    @Test
    void parsesDirectConventionAndHistoricalBytes() {
        AtrParser.Atr atr = AtrParser.parse("3B 11 11 22");
        assertEquals("direct", atr.convention());
        assertEquals(0x11, atr.interfaceGroups().get(0).ta().getAsInt());
        assertEquals("22", atr.historicalHex());
        assertTrue(atr.protocols().isEmpty());
        assertTrue(atr.tck().isEmpty());
        assertTrue(atr.tckValid());
    }

    @Test
    void parsesTEqualsOneAndValidatesTck() {
        AtrParser.Atr atr = AtrParser.parse("3B 80 01 00 81");
        assertEquals(SetOf.one(), atr.protocols());
        assertEquals(0x81, atr.tck().getAsInt());
        assertTrue(atr.tckValid());
    }

    @Test
    void reportsBadTckWithoutInventingProtocolData() {
        AtrParser.Atr atr = AtrParser.parse("3B 80 01 00 80");
        assertFalse(atr.tckValid());
    }

    @Test
    void rejectsTruncatedInterfaceOrHistoricalData() {
        assertThrows(IllegalArgumentException.class, () -> AtrParser.parse("3B 91 11"));
        assertThrows(IllegalArgumentException.class, () -> AtrParser.parse("3B 02 01"));
    }

    private static final class SetOf {
        private static java.util.Set<Integer> one() { return java.util.Set.of(1); }
    }
}
