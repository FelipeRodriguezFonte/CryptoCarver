package com.cryptoforge.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DataConverterTest {
    @Test
    void decimalRoundTripUsesUnsignedBytes() {
        byte[] bytes = DataConverter.decimalToBytes("0, 127 128 255");
        assertArrayEquals(new byte[] {0, 127, (byte) 128, (byte) 255}, bytes);
        assertEquals("0 127 128 255", DataConverter.bytesToDecimal(bytes));
    }

    @Test
    void decimalRejectsOutOfRangeAndInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> DataConverter.decimalToBytes("256"));
        assertThrows(IllegalArgumentException.class, () -> DataConverter.decimalToBytes("-1"));
        assertThrows(IllegalArgumentException.class, () -> DataConverter.decimalToBytes("12 nope"));
    }

    @Test
    void hexAndBase64AcceptCommonTransportRepresentations() {
        assertArrayEquals(new byte[] {0x48, 0x65}, DataConverter.hexToBytes("48:65"));
        assertArrayEquals("Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                DataConverter.decodeBase64Flexible("SGVsbG8"));
        assertArrayEquals("Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                DataConverter.decodeBase64Flexible("SGVsbG8"));
    }

    @Test
    void base64UrlRoundTripIsUnpaddedAndUrlSafe() {
        byte[] bytes = {(byte) 0xfb, (byte) 0xff, 0x00};
        String encoded = DataConverter.bytesToBase64Url(bytes);
        assertEquals("-_8A", encoded);
        assertArrayEquals(bytes, DataConverter.decodeBase64Url(encoded));
    }

    @Test
    void base64UrlAlsoAcceptsPaddedInput() {
        assertArrayEquals(new byte[] {(byte) 0xfb, (byte) 0xff, 0x00},
                DataConverter.decodeBase64Url("-_8A"));
        assertArrayEquals("Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                DataConverter.decodeBase64Url("SGVsbG8="));
    }

    @Test
    void base32RoundTripUsesRfc4648Representation() {
        byte[] bytes = "Hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("JBSWY3DP", DataConverter.bytesToBase32(bytes));
        assertArrayEquals(bytes, DataConverter.decodeBase32("JBSWY3DP"));
    }

    @Test
    void xorRejectsDifferentLengthsAndReturnsExpectedBytes() {
        assertArrayEquals(new byte[] {(byte) 0xFF, 0x00},
                DataConverter.xor(new byte[] {0x0F, 0x55}, new byte[] {(byte) 0xF0, 0x55}));
        assertThrows(IllegalArgumentException.class, () -> DataConverter.xor(new byte[] {1}, new byte[] {1, 2}));
    }

    @Test
    void visualizationMakesControlBytesExplicit() {
        assertEquals("A<NUL><TAB><LF>\n<CR><0xFF>",
                DataConverter.visualizeBytes(new byte[] {'A', 0, 9, 10, 13, (byte) 0xFF}));
    }

    @Test
    void bcdAndComp3RoundTrips() {
        assertEquals("012345", DataConverter.packedBcdToDecimal(DataConverter.decimalToPackedBcd("12345")));
        assertEquals("12345", DataConverter.comp3ToDecimal(DataConverter.decimalToComp3("12345")));
        assertEquals("-12345", DataConverter.comp3ToDecimal(DataConverter.decimalToComp3("-12345")));
    }

    @Test
    void quotedPrintableRoundTripPreservesUtf8Bytes() {
        byte[] bytes = "café".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertArrayEquals(bytes, DataConverter.decodeQuotedPrintable(DataConverter.bytesToQuotedPrintable(bytes)));
    }

    @Test
    void base58AndBase58CheckRoundTrip() {
        byte[] bytes = new byte[] {0, 0, 1, 2, 3};
        assertArrayEquals(bytes, DataConverter.decodeBase58(DataConverter.bytesToBase58(bytes)));
        assertArrayEquals(bytes, DataConverter.decodeBase58Check(DataConverter.bytesToBase58Check(bytes)));
        assertThrows(IllegalArgumentException.class, () -> DataConverter.decodeBase58("0OIl"));
    }
}
