package com.cryptoforge.model.batch;

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
}
