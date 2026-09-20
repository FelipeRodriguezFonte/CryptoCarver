package com.cryptocarver.crypto;

import com.cryptocarver.pin.TDes;

import java.math.BigInteger;
import java.util.Arrays;

public final class BpsTdesFormatPreservingEncryption {
    public static final int DEFAULT_ROUNDS = 4;
    public static final int LEGACY_ROUNDS = 8;

    private static final long[] POWERS_OF_TEN = {
            1L, 10L, 100L, 1_000L, 10_000L, 100_000L,
            1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L
    };

    private BpsTdesFormatPreservingEncryption() {
    }

    public static String encrypt(String plaintext, byte[] key, byte[] tweak) {
        return encrypt(plaintext, key, tweak, DEFAULT_ROUNDS, TweakTransform.IDENTITY);
    }

    public static String encrypt(String plaintext, byte[] key, byte[] tweak, int rounds) {
        return encrypt(plaintext, key, tweak, rounds, TweakTransform.IDENTITY);
    }

    public static String encrypt(String plaintext, byte[] key, byte[] tweak, int rounds,
            TweakTransform tweakTransform) {
        return crypt(true, plaintext, key, tweak, rounds, tweakTransform);
    }

    public static String decrypt(String ciphertext, byte[] key, byte[] tweak) {
        return decrypt(ciphertext, key, tweak, DEFAULT_ROUNDS, TweakTransform.IDENTITY);
    }

    public static String decrypt(String ciphertext, byte[] key, byte[] tweak, int rounds) {
        return decrypt(ciphertext, key, tweak, rounds, TweakTransform.IDENTITY);
    }

    public static String decrypt(String ciphertext, byte[] key, byte[] tweak, int rounds,
            TweakTransform tweakTransform) {
        return crypt(false, ciphertext, key, tweak, rounds, tweakTransform);
    }

    public static void validate(String input, byte[] key, byte[] tweak, int rounds) {
        if (input == null || input.length() < 2 || input.length() > 18) {
            throw new IllegalArgumentException("Input must contain from 2 to 18 decimal digits");
        }
        for (int index = 0; index < input.length(); index++) {
            char digit = input.charAt(index);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("Input must contain only decimal digits");
            }
        }
        if (key == null || (key.length != 8 && key.length != 16 && key.length != 24)) {
            throw new IllegalArgumentException("TDES key must contain 8, 16, or 24 bytes");
        }
        if (tweak == null || tweak.length != 8) {
            throw new IllegalArgumentException("Tweak must contain exactly 8 bytes");
        }
        if (rounds != DEFAULT_ROUNDS && rounds != LEGACY_ROUNDS) {
            throw new IllegalArgumentException("Rounds must be 4 or 8");
        }
    }

    private static String crypt(boolean encrypt, String input, byte[] key, byte[] tweak, int rounds,
            TweakTransform tweakTransform) {
        validate(input, key, tweak, rounds);
        if (tweakTransform == null) {
            throw new IllegalArgumentException("Tweak transform must not be null");
        }

        int leftLength = (input.length() + 1) / 2;
        int rightLength = input.length() / 2;
        long left = decodeLittleEndianDigits(input, 0, leftLength);
        long right = decodeLittleEndianDigits(input, leftLength, rightLength);
        long leftModulus = POWERS_OF_TEN[leftLength];
        long rightModulus = POWERS_OF_TEN[rightLength];
        byte[] transformedTweak = tweakTransform.apply(tweak);
        long tl = readUInt32(transformedTweak, 0);
        long tr = readUInt32(transformedTweak, 4);
        byte[] tdesKey = Arrays.copyOf(key, key.length);

        if (encrypt) {
            for (int round = 0; round < rounds; round++) {
                if ((round & 1) == 0) {
                    left = addMod(left, roundValue(tdesKey, tr, round, right), leftModulus);
                } else {
                    right = addMod(right, roundValue(tdesKey, tl, round, left), rightModulus);
                }
            }
        } else {
            for (int round = rounds - 1; round >= 0; round--) {
                if ((round & 1) == 0) {
                    left = subtractMod(left, roundValue(tdesKey, tr, round, right), leftModulus);
                } else {
                    right = subtractMod(right, roundValue(tdesKey, tl, round, left), rightModulus);
                }
            }
        }

        return encodeLittleEndianDigits(left, leftLength) + encodeLittleEndianDigits(right, rightLength);
    }

    private static long roundValue(byte[] key, long tweakHalf, int round, long branch) {
        byte[] block = new byte[8];
        writeUInt32((tweakHalf ^ round) & 0xFFFF_FFFFL, block, 0);
        writeUInt32(branch, block, 4);
        byte[] encrypted = TDes.encryptEcbNoPadding(key, block);
        return new BigInteger(1, encrypted).longValue();
    }

    private static long addMod(long value, long addend, long modulus) {
        return (value + Long.remainderUnsigned(addend, modulus)) % modulus;
    }

    private static long subtractMod(long value, long subtrahend, long modulus) {
        long result = value - Long.remainderUnsigned(subtrahend, modulus);
        return result < 0 ? result + modulus : result;
    }

    private static long decodeLittleEndianDigits(String input, int offset, int length) {
        long result = 0;
        long factor = 1;
        for (int index = 0; index < length; index++) {
            result += (input.charAt(offset + index) - '0') * factor;
            factor *= 10;
        }
        return result;
    }

    private static String encodeLittleEndianDigits(long value, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append((char) ('0' + value % 10));
            value /= 10;
        }
        return result.toString();
    }

    private static long readUInt32(byte[] value, int offset) {
        return ((long) (value[offset] & 0xFF) << 24)
                | ((long) (value[offset + 1] & 0xFF) << 16)
                | ((long) (value[offset + 2] & 0xFF) << 8)
                | (value[offset + 3] & 0xFFL);
    }

    private static void writeUInt32(long value, byte[] output, int offset) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
    }

    public record TweakTransform(long leftXor, long rightXor) {
        public static final TweakTransform IDENTITY = new TweakTransform(0, 0);

        public TweakTransform {
            if ((leftXor & ~0xFFFF_FFFFL) != 0 || (rightXor & ~0xFFFF_FFFFL) != 0) {
                throw new IllegalArgumentException("Tweak XOR values must fit in 32 bits");
            }
        }

        private byte[] apply(byte[] tweak) {
            byte[] result = Arrays.copyOf(tweak, tweak.length);
            writeUInt32(readUInt32(result, 0) ^ leftXor, result, 0);
            writeUInt32(readUInt32(result, 4) ^ rightXor, result, 4);
            return result;
        }
    }
}
