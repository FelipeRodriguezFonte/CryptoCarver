package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CharsetInspectorTest {

    @Test
    void testAscii(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ascii.txt");
        Files.writeString(file, "Hello, world!");

        var h = CharsetInspector.inspect(file, "IBM037 — US/Canada");
        assertTrue(h.utf8Valid());
        assertEquals(100.0, h.asciiScore(), 0.1);
        assertEquals(0.0, h.latin1Score(), 0.1);
        assertTrue(h.conclusion().contains("ASCII or UTF-8"));
    }

    @Test
    void testValidUtf8(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("utf8.txt");
        Files.writeString(file, "¡Hola, mundo! Español");

        var h = CharsetInspector.inspect(file, "IBM037 — US/Canada");
        assertTrue(h.utf8Valid());
        assertTrue(h.latin1Score() > 0);
        assertTrue(h.conclusion().contains("UTF-8") || h.conclusion().contains("ISO-8859"));
    }

    @Test
    void testInvalidUtf8(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("invalid.bin");
        Files.write(file, new byte[] { (byte)0xFF, (byte)0xFE, 0x00, 0x01 });

        var h = CharsetInspector.inspect(file, "IBM037 — US/Canada");
        assertFalse(h.utf8Valid());
    }

    @Test
    void testWindows1252(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("win1252.txt");
        // Byte 0x80 is '€' in Windows-1252, but a control character in ISO-8859-1
        Files.write(file, new byte[] { (byte)0x80, '1', '0', '0' });

        var h = CharsetInspector.inspect(file, "IBM037 — US/Canada");
        assertTrue(h.windows1252Score() > h.latin1Score());
        assertTrue(h.conclusion().contains("Windows-1252"));
    }

    @Test
    void testEbcdic(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("ebcdic.bin");
        // "HELLO" in EBCDIC IBM037: C8 C5 D3 D3 D6
        Files.write(file, new byte[] { (byte)0xC8, (byte)0xC5, (byte)0xD3, (byte)0xD3, (byte)0xD6 });

        var h = CharsetInspector.inspect(file, "IBM037 — US/Canada");
        assertTrue(h.ebcdicScore() == 100.0);
        assertTrue(h.conclusion().contains("EBCDIC"));
    }
}
