package com.cryptocarver.crypto.icsf.keywrap;

import java.util.ArrayList;
import java.util.List;

/**
 * DES parity, and the two verification numbers that get confused for each other.
 *
 * <p>When a key does not match across two systems the first thing to establish is which
 * number each side is looking at. The industry KCV and CSNBKYT's verification pattern are
 * different algorithms over the same key, so quoting one against the other never agrees.</p>
 */
public final class DesKeyCheck {

    private DesKeyCheck() { }

    /** Uniform parity across every byte of a key, when there is one. */
    public enum Parity { ODD, EVEN, MIXED }

    /** Indices of the bytes that do not carry odd parity. */
    public static List<Integer> bytesWithoutOddParity(byte[] data) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            if (Integer.bitCount(data[i] & 0xFF) % 2 == 0) out.add(i);
        }
        return out;
    }

    /**
     * Whether all bytes share a parity, and which.
     *
     * <p>Sharing one is not chance: over random material the odds are 2/2^n, one in 32768
     * for a 16-byte key. So "all even" identifies a hand-entered key whose parity nobody
     * adjusted just as surely as "all odd" identifies an adjusted one, which is what makes
     * it worth reporting rather than silently fixing.</p>
     */
    public static Parity uniformParity(byte[] data) {
        if (data.length == 0) return Parity.MIXED;
        int wrong = bytesWithoutOddParity(data).size();
        if (wrong == 0) return Parity.ODD;
        if (wrong == data.length) return Parity.EVEN;
        return Parity.MIXED;
    }

    /** Forces odd parity on every byte, touching only bit 0. */
    public static byte[] adjustToOddParity(byte[] data) {
        byte[] out = data.clone();
        for (int i = 0; i < out.length; i++) {
            if (Integer.bitCount(out[i] & 0xFF) % 2 == 0) out[i] ^= 0x01;
        }
        return out;
    }

    /**
     * KCV / ENC-ZERO: the leading bytes of the key encrypting eight zero bytes.
     *
     * <p>CSNBKYT's "encrypted zeros" takes 4 bytes (3 on compliant-tagged tokens); the
     * industry KCV that non-CCA systems ask for takes 3.</p>
     */
    public static byte[] encZero(byte[] key, int length) {
        byte[] full = Des.tdesEncryptBlock(key, new byte[8]);
        byte[] out = new byte[length];
        System.arraycopy(full, 0, out, 0, length);
        return out;
    }

    public static byte[] encZero(byte[] key) {
        return encZero(key, 3);
    }

    /**
     * Verification pattern of Key Test (CSNBKYT), DES algorithm, APG p. 1720.
     *
     * <pre>
     *   KK = eC(KL) XOR KL          with C = X'4545454545454545'
     *   VP = eKK(KR XOR RN) XOR KR XOR RN
     * </pre>
     *
     * <p>KR is all zeroes on a single-length key. This is not the KCV.</p>
     */
    public static byte[] ibmVerificationPattern(byte[] key, byte[] randomNumber) {
        byte[] kl = new byte[8];
        System.arraycopy(key, 0, kl, 0, 8);
        byte[] kr = new byte[8];
        if (key.length >= 16) System.arraycopy(key, 8, kr, 0, 8);
        byte[] c = new byte[8];
        java.util.Arrays.fill(c, (byte) 0x45);
        byte[] kk = Des.xor(Des.encryptBlock(c, kl), kl);
        byte[] t = Des.xor(kr, randomNumber);
        return Des.xor(Des.encryptBlock(kk, t), t);
    }

    public static byte[] ibmVerificationPattern(byte[] key) {
        return ibmVerificationPattern(key, new byte[8]);
    }

    /** Effective strength of cleartext key material, from how its parts compare. */
    public static String componentAnalysis(byte[] key) {
        int parts = key.length / 8;
        byte[][] p = new byte[parts][];
        for (int i = 0; i < parts; i++) {
            p[i] = new byte[8];
            System.arraycopy(key, i * 8, p[i], 0, 8);
        }
        if (parts == 1) return "SINGLE_DES";
        if (parts == 2) {
            return java.util.Arrays.equals(p[0], p[1]) ? "COLLAPSES_TO_SINGLE" : "DOUBLE_REAL";
        }
        if (java.util.Arrays.equals(p[0], p[2]) && !java.util.Arrays.equals(p[0], p[1])) {
            return "DOUBLE_REAL_K1_EQ_K3";
        }
        boolean allDistinct = !java.util.Arrays.equals(p[0], p[1])
                && !java.util.Arrays.equals(p[1], p[2])
                && !java.util.Arrays.equals(p[0], p[2]);
        return allDistinct ? "TRIPLE_REAL" : "COLLAPSES_TO_SINGLE";
    }
}
