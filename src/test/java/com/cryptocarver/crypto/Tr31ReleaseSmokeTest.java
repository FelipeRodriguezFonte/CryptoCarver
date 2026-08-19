package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reproducible JUnit counterpart of the historical TR-31 command-line smoke. */
class Tr31ReleaseSmokeTest {

    @Test
    void wrapsAndUnwrapsTheKnownSafeTr31Sample() throws Exception {
        String kbpk = "00112233445566778899AABBCCDDEEFF";
        String key = "0123456789ABCDEFFEDCBA9876543210";

        String block = TR31Operations.wrapKey(kbpk, key, "B1", 'D', 'T', 'E', 'S', "");

        assertTrue(block.startsWith("D0112B1TE00S0000"), "TR-31 header must remain stable");
        assertEquals(Integer.parseInt(block.substring(1, 5)), block.length());
        assertEquals(key, TR31Operations.unwrapKey(kbpk, block));
    }
}
