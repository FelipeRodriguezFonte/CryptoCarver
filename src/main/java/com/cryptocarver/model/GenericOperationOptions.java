package com.cryptocarver.model;

import java.util.List;

/** Shared option codes used by Generic operation controls and their help catalogue. */
public final class GenericOperationOptions {
    public static final List<String> FILE_FORMATS = List.of("Binary", "Text", "Hex", "Base64");
    public static final List<String> FILE_ENCODINGS = List.of("UTF-8", "ASCII", "ISO-8859-1");
    public static final List<String> EBCDIC_DIRECTIONS = List.of("Decode EBCDIC → UTF-8", "Encode UTF-8 → EBCDIC");
    public static final List<String> ENDIAN_WORD_SIZES = List.of(
            "16 bits (2 bytes)", "32 bits (4 bytes)", "64 bits (8 bytes)", "128 bits (16 bytes)");
    public static final List<String> MODULAR_OPERATIONS = List.of(
            "Addition (a + b) mod m", "Subtraction (a - b) mod m", "Inverse -a mod m",
            "Multiplication (a * b) mod m", "Exponentiation (a^b) mod m", "Reciprocal (1/a) mod m",
            "GCD(a, b)", "LCM(a, b)", "Extended GCD", "XOR (Hex Input)", "XOR (Decimal Input)");

    private GenericOperationOptions() { }
}
