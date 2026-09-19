package com.cryptocarver.crypto;

import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Futurex keys under a Master File Key.
 *
 * <p>The simplest of the manufacturer schemes in this bench, and the one whose
 * rule is prettiest: a key is encrypted in plain 3DES-ECB under the MFK, and
 * the <b>key modifier</b> — Futurex's equivalent of a Thales key type — is
 * applied by XORing {@code modifier × 8} into the <b>first byte of each 8-byte
 * part</b> of the MFK.</p>
 *
 * <pre>
 *   modifier 0 -> 00          modifier 3 -> 18
 *   modifier 1 -> 08          modifier 4 -> 20
 *   modifier 2 -> 10
 * </pre>
 *
 * <p>That is the modifier shifted left three bits, which is a pleasant thing to
 * discover and a dangerous thing to assume. It is verified for modifiers 0 to
 * 4 and no further, so {@link #encrypt} refuses a higher one by name rather
 * than extrapolating; see the note on that method.</p>
 *
 * <h2>Where this came from</h2>
 *
 * <p>Not from Futurex. The scheme was recovered from five input/output pairs
 * produced by EFTLab's BP-Tools Cryptographic Calculator 21.06, captured
 * 2026-09-19: one key under one MFK, once per modifier. Five cryptograms of the
 * same key under the same MFK isolate the modifier and nothing else, which is
 * why they were asked for that way.</p>
 *
 * <p>Note what a single capture would <em>not</em> have shown. With only
 * modifier 0 this looks like plain ECB with no key separation at all, which is
 * both wrong and the kind of wrong that round-trips perfectly against itself.</p>
 */
public final class FuturexMfkOperations {

    /** The highest modifier an actual vector exists for. */
    public static final int VERIFIED_MODIFIERS = 4;

    private FuturexMfkOperations() {
    }

    /** A key under an MFK, as the tool reports it. */
    public record WrappedKey(int modifier, String cryptogram, String checkValue) {
    }

    /**
     * Applies a key modifier to an MFK: {@code modifier × 8} XORed into the
     * first byte of each 8-byte part.
     */
    public static String applyModifier(String mfk, int modifier) {
        byte[] key = masterFileKey(mfk);
        requireModifier(modifier);
        byte[] out = key.clone();
        for (int at = 0; at < out.length; at += 8) {
            out[at] ^= (byte) (modifier * 8);
        }
        return hex(out);
    }

    /**
     * Encrypts a clear key under the MFK that the modifier selects.
     *
     * <p>Modifiers above {@value #VERIFIED_MODIFIERS} are refused. The rule
     * above would extrapolate to them without complaint, and it may well be
     * right — but "it may well be right" is how this bench got the Thales
     * variant scheme wrong twice, and lifting the limit costs one screenshot
     * of the Cryptographic Calculator with the modifier dropdown moved one
     * notch further.</p>
     */
    public static WrappedKey encrypt(String clearKey, String mfk, int modifier) {
        byte[] key = keyBytes(clearKey);
        byte[] modified = bytes(applyModifier(mfk, modifier));
        return new WrappedKey(modifier, hex(ecb(modified, key, true)), checkValue(clearKey));
    }

    /** Decrypts a cryptogram under the MFK that the modifier selects. */
    public static WrappedKey decrypt(String cryptogram, String mfk, int modifier) {
        byte[] wrapped = keyBytes(cryptogram);
        byte[] modified = bytes(applyModifier(mfk, modifier));
        String clear = hex(ecb(modified, wrapped, false));
        return new WrappedKey(modifier, clear, checkValue(clear));
    }

    public static String describe(WrappedKey wrapped, String mfk) {
        StringBuilder report = new StringBuilder("Futurex key under MFK\n");
        report.append("  key modifier  : ").append(wrapped.modifier()).append('\n');
        report.append("  MFK modified  : ").append(applyModifier(mfk, wrapped.modifier()))
                .append("   (modifier × 8 into the first byte of each part)\n");
        report.append("  cryptogram    : ").append(wrapped.cryptogram()).append('\n');
        report.append("  check value   : ").append(wrapped.checkValue()).append('\n');
        report.append("\nThe cipher is 3DES-ECB. There is no authentication here: a Futurex\n");
        report.append("cryptogram carries no MAC and nothing binds it to its modifier, so a key\n");
        report.append("decrypted under the wrong modifier comes out as plausible-looking rubbish\n");
        report.append("rather than as an error. Compare the check value.\n");
        return report.toString();
    }

    /** The first three bytes of the all-zero block under the key. */
    public static String checkValue(String clearKey) {
        return hex(ecb(keyBytes(clearKey), new byte[8], true)).substring(0, 6);
    }

    // =====================================================================

    private static void requireModifier(int modifier) {
        if (modifier < 0) {
            throw new IllegalArgumentException("A key modifier is not negative");
        }
        if (modifier > VERIFIED_MODIFIERS) {
            throw new IllegalArgumentException("Only modifiers 0 to " + VERIFIED_MODIFIERS
                    + " have been verified against a vector, and " + modifier + " has not. The "
                    + "rule looks like modifier × 8 and would extrapolate silently, which is "
                    + "exactly what this bench does not do with key separation");
        }
    }

    private static byte[] masterFileKey(String mfk) {
        byte[] key = bytes(normalizeHex(mfk, "MFK"));
        if (key.length != 16 && key.length != 24) {
            throw new IllegalArgumentException("An MFK is a double- or triple-length TDES key "
                    + "(16 or 24 bytes), not " + key.length);
        }
        return key;
    }

    private static byte[] keyBytes(String value) {
        byte[] key = bytes(normalizeHex(value, "key"));
        if (key.length == 0 || key.length % 8 != 0) {
            throw new IllegalArgumentException("A key is a whole number of 8-byte blocks, not "
                    + key.length + " bytes");
        }
        return key;
    }

    private static byte[] ecb(byte[] key, byte[] data, boolean encrypt) {
        byte[] full = new byte[24];
        if (key.length == 16) {
            System.arraycopy(key, 0, full, 0, 16);
            System.arraycopy(key, 0, full, 16, 8);
        } else if (key.length == 8) {
            for (int i = 0; i < 3; i++) {
                System.arraycopy(key, 0, full, i * 8, 8);
            }
        } else {
            full = key.clone();
        }
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(full, "DESede"));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Triple DES is unavailable", e);
        }
    }

    static String normalizeHex(String value, String label) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (cleaned.isEmpty()) {
            return "";
        }
        if (!cleaned.matches("[0-9A-F]+") || (cleaned.length() % 2) != 0) {
            throw new IllegalArgumentException(label + " must be even-length hexadecimal");
        }
        return cleaned;
    }

    private static byte[] bytes(String hex) {
        String normalized = normalizeHex(hex, "hexadecimal value");
        return normalized.isEmpty() ? new byte[0] : HexFormat.of().parseHex(normalized);
    }

    private static String hex(byte[] data) {
        return HexFormat.of().formatHex(data).toUpperCase(Locale.ROOT);
    }
}
