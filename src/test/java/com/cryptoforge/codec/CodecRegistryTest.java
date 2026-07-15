package com.cryptoforge.codec;

import com.cryptoforge.util.DataConverter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CodecRegistryTest {

    private final CodecRegistry registry = CodecRegistry.getInstance();

    @Test
    void testHexEquivalence() {
        byte[] input = { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF };
        String oldHex = DataConverter.bytesToHex(input);
        String newHex = registry.encode(input, ByteFormat.HEX);
        assertEquals(oldHex.toUpperCase(), newHex.toUpperCase());
        assertArrayEquals(DataConverter.hexToBytes(oldHex), registry.decode(oldHex, ByteFormat.HEX));
        
        // Invalid lengths and chars
        assertThrowsCodecException(() -> registry.decode("ABC", ByteFormat.HEX), ByteFormat.HEX);
        assertThrowsCodecException(() -> registry.decode("G1", ByteFormat.HEX), ByteFormat.HEX);
    }
    
    @Test
    void testBase64Equivalence() {
        byte[] input = "Prueba Base64".getBytes(StandardCharsets.UTF_8);
        String oldB64 = java.util.Base64.getEncoder().encodeToString(input);
        String newB64 = registry.encode(input, ByteFormat.BASE64);
        assertEquals(oldB64, newB64);
        assertArrayEquals(DataConverter.decodeBase64Flexible(oldB64), registry.decode(oldB64, ByteFormat.BASE64));
    }

    @Test
    void testBase64UrlEquivalenceAndStrictness() {
        // Encodings of lengths 1, 2, 3 have different padding requirements in standard base64
        byte[] input1 = { 1 };
        byte[] input2 = { 1, 2 };
        byte[] input3 = { 1, 2, 3 };
        
        String enc1 = registry.encode(input1, ByteFormat.BASE64_URL);
        String enc2 = registry.encode(input2, ByteFormat.BASE64_URL);
        String enc3 = registry.encode(input3, ByteFormat.BASE64_URL);
        
        // Ensure no padding is present in encode
        assertFalse(enc1.contains("="));
        assertFalse(enc2.contains("="));
        assertFalse(enc3.contains("="));
        
        assertArrayEquals(input1, registry.decode(enc1, ByteFormat.BASE64_URL));
        assertArrayEquals(input2, registry.decode(enc2, ByteFormat.BASE64_URL));
        assertArrayEquals(input3, registry.decode(enc3, ByteFormat.BASE64_URL));

        // Reject padding
        assertThrowsCodecException(() -> registry.decode(enc1 + "==", ByteFormat.BASE64_URL), ByteFormat.BASE64_URL);
        
        // Reject standard Base64 chars
        assertThrowsCodecException(() -> registry.decode("ab+cd/ef", ByteFormat.BASE64_URL), ByteFormat.BASE64_URL);
        
        // Reject whitespace
        assertThrowsCodecException(() -> registry.decode(enc1 + " ", ByteFormat.BASE64_URL), ByteFormat.BASE64_URL);
    }

    @Test
    void testBase32Equivalence() {
        byte[] input = "CryptoForge CodecRegistry".getBytes(StandardCharsets.UTF_8);
        String oldBase32 = DataConverter.bytesToBase32(input);
        String newBase32 = registry.encode(input, ByteFormat.BASE32);
        assertEquals(oldBase32, newBase32);

        assertArrayEquals(DataConverter.decodeBase32(oldBase32), registry.decode(oldBase32, ByteFormat.BASE32));
    }
    
    @Test
    void testBase58() {
        byte[] input = { 0, 1, 2, 3 };
        String enc = registry.encode(input, ByteFormat.BASE58);
        assertArrayEquals(input, registry.decode(enc, ByteFormat.BASE58));
        
        // Invalid Base58 character 'O'
        assertThrowsCodecException(() -> registry.decode("123O456", ByteFormat.BASE58), ByteFormat.BASE58);
    }
    
    @Test
    void testBase58Check() {
        byte[] input = { 1, 2, 3 };
        String enc = registry.encode(input, ByteFormat.BASE58_CHECK);
        assertArrayEquals(input, registry.decode(enc, ByteFormat.BASE58_CHECK));
        
        // Invalid checksum: mutate one char
        char mutated = enc.charAt(0) == '1' ? '2' : '1';
        String badEnc = mutated + enc.substring(1);
        assertThrowsCodecException(() -> registry.decode(badEnc, ByteFormat.BASE58_CHECK), ByteFormat.BASE58_CHECK);
    }

    @Test
    void testBinaryEquivalence() {
        byte[] input = { (byte) 0xAA, (byte) 0x55 };
        String oldBinary = DataConverter.bytesToBinary(input);
        String newBinary = registry.encode(input, ByteFormat.BINARY);
        assertEquals(oldBinary, newBinary);

        assertArrayEquals(input, registry.decode(newBinary, ByteFormat.BINARY));
        
        // Not a multiple of 8
        assertThrowsCodecException(() -> registry.decode("0101", ByteFormat.BINARY), ByteFormat.BINARY);
        // Invalid character
        assertThrowsCodecException(() -> registry.decode("01010102", ByteFormat.BINARY), ByteFormat.BINARY);
    }

    @Test
    void testDecimalEquivalence() {
        byte[] input = { 10, 20, (byte) 200, (byte) 255 };
        String oldDecimal = DataConverter.bytesToDecimal(input);
        String newDecimal = registry.encode(input, ByteFormat.DECIMAL);
        assertEquals(oldDecimal, newDecimal);

        assertArrayEquals(DataConverter.decimalToBytes(oldDecimal), registry.decode(oldDecimal, ByteFormat.DECIMAL));
        
        // Out of bounds
        assertThrowsCodecException(() -> registry.decode("256", ByteFormat.DECIMAL), ByteFormat.DECIMAL);
        assertThrowsCodecException(() -> registry.decode("-1", ByteFormat.DECIMAL), ByteFormat.DECIMAL);
        // Invalid format
        assertThrowsCodecException(() -> registry.decode("12a", ByteFormat.DECIMAL), ByteFormat.DECIMAL);
    }

    @Test
    void testUtf8EquivalenceAndStrictness() {
        byte[] input = "Prueba de caracteres: áéíóú ñ!".getBytes(StandardCharsets.UTF_8);
        String newUtf8 = registry.encode(input, ByteFormat.TEXT_UTF8);
        assertEquals(new String(input, StandardCharsets.UTF_8), newUtf8);
        assertArrayEquals(input, registry.decode(newUtf8, ByteFormat.TEXT_UTF8));
        
        // Invalid UTF-8 byte sequence
        byte[] invalidBytes = { (byte) 0xFF, (byte) 0xFF };
        assertThrowsCodecException(() -> registry.encode(invalidBytes, ByteFormat.TEXT_UTF8), ByteFormat.TEXT_UTF8);
        
        // Isolated surrogate in String
        String invalidString = "Test\uD83D"; // high surrogate without low surrogate
        assertThrowsCodecException(() -> registry.decode(invalidString, ByteFormat.TEXT_UTF8), ByteFormat.TEXT_UTF8);
    }
    
    @Test
    void testAsciiEquivalence() {
        byte[] input = "Test ASCII".getBytes(StandardCharsets.US_ASCII);
        String enc = registry.encode(input, ByteFormat.TEXT_ASCII);
        assertEquals("Test ASCII", enc);
        assertArrayEquals(input, registry.decode(enc, ByteFormat.TEXT_ASCII));
        
        // Non-ASCII byte
        assertThrowsCodecException(() -> registry.encode(new byte[] { (byte) 0x80 }, ByteFormat.TEXT_ASCII), ByteFormat.TEXT_ASCII);
        // Non-ASCII char
        assertThrowsCodecException(() -> registry.decode("ñ", ByteFormat.TEXT_ASCII), ByteFormat.TEXT_ASCII);
    }
    
    @Test
    void testIso88591Equivalence() {
        byte[] input = "Test ISO-8859-1 ñ".getBytes(StandardCharsets.ISO_8859_1);
        String enc = registry.encode(input, ByteFormat.TEXT_ISO_8859_1);
        assertEquals("Test ISO-8859-1 ñ", enc);
        assertArrayEquals(input, registry.decode(enc, ByteFormat.TEXT_ISO_8859_1));
        
        // Char out of range for ISO-8859-1 (e.g., Euro symbol)
        assertThrowsCodecException(() -> registry.decode("€", ByteFormat.TEXT_ISO_8859_1), ByteFormat.TEXT_ISO_8859_1);
    }
    
    private void assertThrowsCodecException(org.junit.jupiter.api.function.Executable executable, ByteFormat expectedFormat) {
        CodecException e = assertThrows(CodecException.class, executable);
        assertEquals(expectedFormat, e.getFormat());
    }
}
