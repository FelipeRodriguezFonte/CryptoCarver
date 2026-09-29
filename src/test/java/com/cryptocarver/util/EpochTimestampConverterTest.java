package com.cryptocarver.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EpochTimestampConverterTest {
    @Test
    void convertsEpochSecondsToUtcTimestamp() {
        assertEquals("1970-01-01T00:00:00Z", EpochTimestampConverter.toUtc("0"));
    }

    @Test
    void acceptsWhitespaceAroundEpochSeconds() {
        assertEquals("1970-01-01T00:00:01Z", EpochTimestampConverter.toUtc(" 1 "));
    }

    @Test
    void rejectsNonNumericEpochSeconds() {
        assertThrows(NumberFormatException.class, () -> EpochTimestampConverter.toUtc("invalid"));
    }
}
