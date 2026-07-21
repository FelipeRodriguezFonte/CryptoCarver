package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cryptocarver.util.DataConverter;

class EBCDICConverterTest {

    private static final String IBM037 = "IBM037 — US/Canada";

    @Test
    void decodesCp037BytesToUtf8Text() {
        byte[] ebcdic = EBCDICConverter.parseBytes("C8 85 93 93 96", "Hexadecimal");
        assertEquals("Hello", EBCDICConverter.decode(ebcdic, IBM037));
    }

    @Test
    void encodesUtf8TextAsCp037Bytes() {
        assertArrayEquals(new byte[] { (byte) 0xC8, (byte) 0x85, (byte) 0x93, (byte) 0x93, (byte) 0x96 },
                EBCDICConverter.encode("Hello", IBM037));
        assertEquals("Hello", new String("Hello".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    @Test
    void convertsUtf8HexBytesToEBCDICBytesForTheSelectedCodePage() {
        byte[] utf8Bytes = DataConverter.hexToBytes("48656C6C6F");
        String text = DataConverter.utf8BytesToString(utf8Bytes);

        assertEquals("C885939396", DataConverter.bytesToHex(EBCDICConverter.encode(text, IBM037)));
    }

    @Test
    void rejectsMalformedUtf8BeforeEncodingToEBCDIC() {
        assertThrows(IllegalArgumentException.class,
                () -> DataConverter.utf8BytesToString(new byte[] {(byte) 0xC3, 0x28}));
    }

    @Test
    void allListedCodePagesAreAvailableInTheRuntime() {
        EBCDICConverter.supportedCodePages().values()
                .forEach(charset -> org.junit.jupiter.api.Assertions.assertTrue(
                        java.nio.charset.Charset.isSupported(charset), charset + " must be available"));
    }
}
