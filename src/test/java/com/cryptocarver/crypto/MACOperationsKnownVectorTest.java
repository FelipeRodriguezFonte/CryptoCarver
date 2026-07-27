package com.cryptocarver.crypto;

import com.cryptocarver.util.DataConverter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MACOperationsKnownVectorTest {

    /**
     * ANSI X9.19 / ISO 9797-1 Algorithm 3 public example using two-key TDES.
     */
    @Test
    void ansiX919MatchesKnownVectorAndRejectsAlteredData() throws Exception {
        byte[] key = DataConverter.hexToBytes("0123456789ABCDEFFEDCBA9876543210");
        byte[] message = "Now is the time for all ".getBytes(StandardCharsets.US_ASCII);
        byte[] expectedMac = DataConverter.hexToBytes("A1C72E74EA3FA9B6");

        byte[] actualMac = MACOperations.generate(message, key, "ANSI-X9.19");

        assertArrayEquals(expectedMac, actualMac);
        assertTrue(MACOperations.verify(message, expectedMac, key, "ANSI-X9.19"));

        byte[] alteredMessage = message.clone();
        alteredMessage[alteredMessage.length - 1] ^= 0x01;
        assertFalse(MACOperations.verify(alteredMessage, expectedMac, key, "ANSI-X9.19"));
    }
}
