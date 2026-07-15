package com.cryptoforge.crypto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ByteStatisticsTest {
    @Test
    void analysisReportsExpectedFields() {
        String result = ByteStatistics.analyze(new byte[] {0, 0, 1, 2});
        assertTrue(result.contains("Bytes: 4"));
        assertTrue(result.contains("Distinct values: 3"));
        assertTrue(result.contains("00 (2)"));
    }
}
