package com.cryptocarver.model.batch;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BatchInputCodecTest {
    @Test void parsesQuotedCsvAndJsonlScalars() {
        assertEquals(List.of(Map.of("name", "Ada, Lovelace", "value", "42")), BatchInputCodec.parseCsv("name,value\n\"Ada, Lovelace\",42\n"));
        assertEquals("hello", BatchInputCodec.parseJsonLines("{\"data\":\"hello\",\"count\":2}").get(0).get("data"));
    }
    @Test void rejectsInvalidBatchShapes() {
        assertThrows(IllegalArgumentException.class, () -> BatchInputCodec.parseCsv("a,a\n1,2"));
        assertThrows(IllegalArgumentException.class, () -> BatchInputCodec.parseJsonLines("{\"nested\":{\"x\":1}}"));
    }

    @Test void limitsMaxCellSize() {
        String giant = "a".repeat(100_001);
        assertThrows(IllegalArgumentException.class, () -> BatchInputCodec.parseCsv("a\n" + giant));
        assertThrows(IllegalArgumentException.class, () -> BatchInputCodec.parseJsonLines("{\"data\":\"" + giant + "\"}"));
    }

    @Test void limitsMaxInputSize() {
        java.io.Reader maliciousReader = new java.io.Reader() {
            int read = 0;
            @Override public int read(char[] cbuf, int off, int len) {
                if (read > 100_000_000) return -1;
                for (int i = 0; i < len; i++) cbuf[off + i] = 'a';
                read += len;
                return len;
            }
            @Override public void close() {}
        };
        assertThrows(IllegalArgumentException.class, () -> BatchInputCodec.parseCsv(maliciousReader, 10));
    }
}
