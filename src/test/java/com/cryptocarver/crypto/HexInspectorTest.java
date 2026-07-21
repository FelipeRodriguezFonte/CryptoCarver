package com.cryptocarver.crypto;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class HexInspectorTest {
    @Test
    void rendersOffsetHexAndAsciiColumns() {
        String result = HexInspector.render("Hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, 5);
        assertTrue(result.contains("00000000"));
        assertTrue(result.contains("48 65 6C 6C 6F"));
        assertTrue(result.contains("Hello"));
    }

    @Test
    void marksSelectedBytes() {
        String result = HexInspector.render(new byte[] {0x01, 0x02, 0x03}, 0, 3, 1, 1);
        assertTrue(result.contains("[02]"));
        assertTrue(result.contains("Selection: offset 1, length 1"));
    }
}
