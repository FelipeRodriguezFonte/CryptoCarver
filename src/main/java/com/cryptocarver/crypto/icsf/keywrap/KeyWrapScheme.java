package com.cryptocarver.crypto.icsf.keywrap;

import java.util.ArrayList;
import java.util.List;

/**
 * How a KEK protects key material: which variant of the KEK, and which cipher mode.
 *
 * <p>Two independent choices, and getting either wrong produces a token the other side
 * cannot open. Between them they cover the mismatches that actually happen when a host
 * hands a key to something that is not a CCA box.</p>
 */
public final class KeyWrapScheme {

    private KeyWrapScheme() { }

    /** What the KEK is XORed with before it enciphers the key. */
    public enum Variant {
        /** KEK XOR Control Vector, the normal CCA method (APG p. 1669). */
        CV,
        /** The KEK as-is: an EXPORTER/IMPORTER carrying the NOCV bit (APG p. 207). */
        PLAIN,
        /** KEK XOR CV with the CV halves swapped -- a common implementation slip. */
        CV_SWAPPED
    }

    /** How the 8-byte parts are chained. */
    public enum Mode {
        /** Each part enciphered on its own, TDES ECB. This is WRAP-ECB (APG p. 1713). */
        ECB,
        /** Parts chained in TDES CBC under a zero IV, as some non-CCA systems do. */
        CBC
    }

    /**
     * KEK XOR CV, with the same CV half applied to every half of the KEK.
     *
     * <p>APG p. 1669: "Before a master key or transport key enciphers a key, ICSF
     * exclusive ORs BOTH halves of the master key or transport key with a control vector.
     * The same control vector is exclusive ORed to the left and right half".</p>
     */
    public static byte[] kekVariant(byte[] kek, byte[] cvHalf) {
        byte[] out = new byte[kek.length];
        for (int i = 0; i < kek.length; i++) {
            out[i] = (byte) (kek[i] ^ cvHalf[i % 8]);
        }
        return out;
    }

    /** Which CV half applies to each 8-byte part of the key. */
    private static List<byte[]> cvPerPart(int partCount, byte[] cvLeft, byte[] cvRight, Variant variant) {
        List<byte[]> out = new ArrayList<>();
        if (variant == Variant.PLAIN) {
            for (int i = 0; i < partCount; i++) out.add(new byte[8]);
            return out;
        }
        byte[] left = variant == Variant.CV_SWAPPED ? cvRight : cvLeft;
        byte[] right = variant == Variant.CV_SWAPPED ? cvLeft : cvRight;
        if (partCount == 1) {
            out.add(left);
            return out;
        }
        out.add(left);
        out.add(right);
        if (partCount > 2) {
            // APG p. 20: "For triple-length keys, the two control vectors are the same",
            // so the third part goes back to the left-hand variant.
            out.add(left);
        }
        return out;
    }

    /** One step of the wrap, kept so the report can show the KEK variant per part. */
    public record Step(String partName, byte[] effectiveKek) { }

    /** A wrap result: the cryptogram, and how each part got there. */
    public record Wrapped(byte[] cryptogram, List<Step> steps) { }

    private static final String[] PART_NAMES = {"A", "B", "C"};

    private static void requireKeyLength(byte[] material, String what) {
        if (material.length != 8 && material.length != 16 && material.length != 24) {
            throw new IllegalArgumentException(
                    "The " + what + " must be 8, 16 or 24 bytes (got " + material.length + ").");
        }
    }

    /** Enciphers the key under the KEK, and reports the variant used for each part. */
    public static Wrapped wrap(byte[] key, byte[] kek, byte[] cvLeft, byte[] cvRight,
                               Variant variant, Mode mode) {
        requireKeyLength(key, "key");
        int parts = key.length / 8;
        List<byte[]> cvs = cvPerPart(parts, cvLeft, cvRight, variant);
        List<Step> steps = new ArrayList<>();
        if (mode == Mode.CBC) {
            byte[] effective = kekVariant(kek, cvs.get(0));
            steps.add(new Step("*", effective));
            return new Wrapped(Des.tdesCbcEncrypt(effective, key), steps);
        }
        byte[] out = new byte[key.length];
        for (int i = 0; i < parts; i++) {
            byte[] effective = kekVariant(kek, cvs.get(i));
            byte[] part = new byte[8];
            System.arraycopy(key, i * 8, part, 0, 8);
            System.arraycopy(Des.tdesEncryptBlock(effective, part), 0, out, i * 8, 8);
            steps.add(new Step(PART_NAMES[i], effective));
        }
        return new Wrapped(out, steps);
    }

    /** Deciphers the cryptogram under the KEK, giving back the cleartext key. */
    public static byte[] unwrap(byte[] cryptogram, byte[] kek, byte[] cvLeft, byte[] cvRight,
                                Variant variant, Mode mode) {
        requireKeyLength(cryptogram, "cryptogram");
        int parts = cryptogram.length / 8;
        List<byte[]> cvs = cvPerPart(parts, cvLeft, cvRight, variant);
        if (mode == Mode.CBC) {
            return Des.tdesCbcDecrypt(kekVariant(kek, cvs.get(0)), cryptogram);
        }
        byte[] out = new byte[cryptogram.length];
        for (int i = 0; i < parts; i++) {
            byte[] part = new byte[8];
            System.arraycopy(cryptogram, i * 8, part, 0, 8);
            byte[] plain = Des.tdesDecryptBlock(kekVariant(kek, cvs.get(i)), part);
            System.arraycopy(plain, 0, out, i * 8, 8);
        }
        return out;
    }
}
