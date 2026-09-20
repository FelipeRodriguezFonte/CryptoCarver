package com.cryptocarver.crypto;

/**
 * Fixed-width logical shifts for byte strings.  The result always retains the
 * input width; bits shifted past either end are discarded and zeroes enter at
 * the opposite end.  This is useful for inspecting wire values without the
 * sign extension that {@link java.math.BigInteger} would introduce.
 */
public final class BitShifter {
    private BitShifter() { }

    public static byte[] left(byte[] input, int bits) {
        return shift(input, bits, true);
    }

    public static byte[] right(byte[] input, int bits) {
        return shift(input, bits, false);
    }

    private static byte[] shift(byte[] input, int bits, boolean left) {
        if (input == null) throw new IllegalArgumentException("Input cannot be null");
        if (bits < 0) throw new IllegalArgumentException("Shift count cannot be negative");
        byte[] output = input.clone();
        for (int count = 0; count < bits; count++) {
            int carry = 0;
            if (left) {
                for (int i = output.length - 1; i >= 0; i--) {
                    int value = output[i] & 0xFF;
                    int nextCarry = (value >>> 7) & 1;
                    output[i] = (byte) ((value << 1) | carry);
                    carry = nextCarry;
                }
            } else {
                for (int i = 0; i < output.length; i++) {
                    int value = output[i] & 0xFF;
                    int nextCarry = value & 1;
                    output[i] = (byte) ((value >>> 1) | (carry << 7));
                    carry = nextCarry;
                }
            }
        }
        return output;
    }
}
