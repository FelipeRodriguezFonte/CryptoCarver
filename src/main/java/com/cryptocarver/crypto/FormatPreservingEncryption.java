package com.cryptocarver.crypto;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.bouncycastle.crypto.fpe.FPEFF1Engine;
import org.bouncycastle.crypto.fpe.FPEFF3_1Engine;
import org.bouncycastle.crypto.params.FPEParameters;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * Format-preserving encryption primitives.
 *
 * <p>The representation passed to FF1 is an alphabet string.  Every character
 * in the input must occur exactly once in that alphabet; the returned value has
 * the same length and consists of the same characters.  This class deliberately
 * contains no UI or text encoding policy: callers choose the alphabet explicitly.
 */
public final class FormatPreservingEncryption {
    /** Algorithms exposed by this API.  More algorithms may be added later. */
    public enum Algorithm { FF1, FF3_1 }

    private static final int MAX_INPUT_LENGTH = 4096;
    private static final int MAX_TWEAK_LENGTH = 1024;
    private static final BigInteger MIN_DOMAIN = BigInteger.valueOf(1_000_000L);

    private FormatPreservingEncryption() {}

    public static String encrypt(String plaintext, byte[] key, String alphabet, byte[] tweak) {
        return encrypt(Algorithm.FF1, plaintext, key, alphabet, tweak);
    }

    public static String decrypt(String ciphertext, byte[] key, String alphabet, byte[] tweak) {
        return decrypt(Algorithm.FF1, ciphertext, key, alphabet, tweak);
    }

    public static String encrypt(Algorithm algorithm, String plaintext, byte[] key,
            String alphabet, byte[] tweak) {
        return crypt(true, algorithm, plaintext, key, alphabet, tweak);
    }

    public static String decrypt(Algorithm algorithm, String ciphertext, byte[] key,
            String alphabet, byte[] tweak) {
        return crypt(false, algorithm, ciphertext, key, alphabet, tweak);
    }

    /** Validates all FF1 arguments without performing encryption. */
    public static void validate(String input, byte[] key, String alphabet, byte[] tweak) {
        validate(Algorithm.FF1, input, key, alphabet, tweak);
    }

    public static void validate(Algorithm algorithm, String input, byte[] key,
            String alphabet, byte[] tweak) {
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm must not be null");
        }
        if (algorithm != Algorithm.FF1 && algorithm != Algorithm.FF3_1) {
            throw new IllegalArgumentException("Unsupported FPE algorithm: " + algorithm);
        }
        if (input == null) {
            throw new IllegalArgumentException("Input must not be null");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Input length exceeds " + MAX_INPUT_LENGTH);
        }
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes");
        }
        if (alphabet == null || alphabet.length() < 2 || alphabet.length() > 256) {
            throw new IllegalArgumentException("Alphabet radix must be between 2 and 256");
        }
        Map<Character, Integer> symbols = new HashMap<>();
        for (int i = 0; i < alphabet.length(); i++) {
            char c = alphabet.charAt(i);
            // BC represents each digit in one unsigned byte.  Restricting the
            // alphabet to Latin-1 avoids lossy UTF-16 to digit conversions.
            if (c > 0xFF) {
                throw new IllegalArgumentException("Alphabet must contain only single-byte characters");
            }
            if (symbols.put(c, i) != null) {
                throw new IllegalArgumentException("Alphabet symbols must be unique");
            }
        }
        if (tweak == null) {
            throw new IllegalArgumentException("Tweak must not be null (use an empty array for no tweak)");
        }
        if (tweak.length > MAX_TWEAK_LENGTH) {
            throw new IllegalArgumentException("Tweak length exceeds " + MAX_TWEAK_LENGTH);
        }
        if (algorithm == Algorithm.FF3_1 && tweak.length != 7) {
            throw new IllegalArgumentException("FF3-1 requires a 7-byte tweak");
        }
        if (input.length() < 2) {
            throw new IllegalArgumentException("Input must contain at least two symbols");
        }
        BigInteger domain = BigInteger.valueOf(alphabet.length()).pow(input.length());
        if (domain.compareTo(MIN_DOMAIN) < 0) {
            throw new IllegalArgumentException("FPE domain must contain at least 1,000,000 values");
        }
        if (algorithm == Algorithm.FF3_1) {
            // NIST SP 800-38G limits FF3-1 to n <= 2*floor(96/log2(radix)).
            // This equivalent integer test avoids floating-point rounding.
            int half = (input.length() + 1) / 2;
            if (BigInteger.valueOf(alphabet.length()).pow(half)
                    .compareTo(BigInteger.ONE.shiftLeft(96)) > 0) {
                throw new IllegalArgumentException("Input is too long for FF3-1 at this radix");
            }
        }
        for (int i = 0; i < input.length(); i++) {
            if (!symbols.containsKey(input.charAt(i))) {
                throw new IllegalArgumentException("Input contains a symbol outside the alphabet at index " + i);
            }
        }
    }

    private static String crypt(boolean encrypt, Algorithm algorithm, String input, byte[] key,
            String alphabet, byte[] tweak) {
        validate(algorithm, input, key, alphabet, tweak);
        Map<Character, Integer> symbols = new HashMap<>();
        for (int i = 0; i < alphabet.length(); i++) {
            symbols.put(alphabet.charAt(i), i);
        }
        byte[] digits = new byte[input.length()];
        for (int i = 0; i < digits.length; i++) {
            digits[i] = (byte) symbols.get(input.charAt(i)).intValue();
        }
        org.bouncycastle.crypto.fpe.FPEEngine engine = algorithm == Algorithm.FF1
                ? new FPEFF1Engine() : new FPEFF3_1Engine();
        // FF1 uses the standard AES primitive; explicitly disable BC's
        // inverse-function variant so encryption/decryption interoperate with
        // the NIST construction and other implementations.
        engine.init(encrypt, new FPEParameters(new KeyParameter(Arrays.copyOf(key, key.length)),
                alphabet.length(), Arrays.copyOf(tweak, tweak.length), false));
        byte[] result = new byte[digits.length];
        engine.processBlock(digits, 0, digits.length, result, 0);
        StringBuilder output = new StringBuilder(input.length());
        for (byte digit : result) {
            output.append(alphabet.charAt(digit & 0xFF));
        }
        return output.toString();
    }
}
