package com.cryptocarver.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultPanelContractTest {
    @Test
    void groupsHexWithoutChangingDigits() {
        assertEquals("0011 2233 4455", ResultPanel.groupHex("00 11 22 33 44 55", 4));
        assertEquals("ABC", ResultPanel.groupHex("ABC", 4));
        assertEquals("not-hex", ResultPanel.groupHex("not-hex", 4));
    }

    @Test
    void rejectsInvalidBlockSize() {
        assertThrows(IllegalArgumentException.class, () -> ResultPanel.groupHex("00", 0));
    }
}
