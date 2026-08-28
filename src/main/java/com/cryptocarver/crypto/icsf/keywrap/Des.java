package com.cryptocarver.crypto.icsf.keywrap;

/**
 * DES and TDES, implemented here rather than taken from a provider.
 *
 * <p>This is a test bench for host key material, so it has to process exactly what the
 * host produced: keys whose parity was never adjusted, keys that collapse to single DES
 * because K1 equals K2, and the DES weak keys themselves. A JCE or Bouncy Castle engine
 * rejects several of those outright, which would turn "your host sent something odd" into
 * an exception instead of the finding it should be. FIPS 46-3 with no key screening keeps
 * every input analysable.</p>
 *
 * <p>The tables are the standard ones and are verified against the classic vector in
 * {@code DesEngineSmokeTest}: key 133457799BBCDFF1 over 0123456789ABCDEF gives 85E813540F0AB405.</p>
 */
public final class Des {

    private Des() { }

    public static final int BLOCK = 8;

    private static final int[] IP = {
            58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4,
            62, 54, 46, 38, 30, 22, 14, 6, 64, 56, 48, 40, 32, 24, 16, 8,
            57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
            61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7
    };

    private static final int[] FP = {
            40, 8, 48, 16, 56, 24, 64, 32, 39, 7, 47, 15, 55, 23, 63, 31,
            38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29,
            36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27,
            34, 2, 42, 10, 50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25
    };

    private static final int[] E = {
            32, 1, 2, 3, 4, 5, 4, 5, 6, 7, 8, 9, 8, 9, 10, 11,
            12, 13, 12, 13, 14, 15, 16, 17, 16, 17, 18, 19, 20, 21, 20, 21,
            22, 23, 24, 25, 24, 25, 26, 27, 28, 29, 28, 29, 30, 31, 32, 1
    };

    private static final int[] P = {
            16, 7, 20, 21, 29, 12, 28, 17, 1, 15, 23, 26, 5, 18, 31, 10,
            2, 8, 24, 14, 32, 27, 3, 9, 19, 13, 30, 6, 22, 11, 4, 25
    };

    private static final int[] PC1 = {
            57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2,
            59, 51, 43, 35, 27, 19, 11, 3, 60, 52, 44, 36, 63, 55, 47, 39,
            31, 23, 15, 7, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37,
            29, 21, 13, 5, 28, 20, 12, 4
    };

    private static final int[] PC2 = {
            14, 17, 11, 24, 1, 5, 3, 28, 15, 6, 21, 10, 23, 19, 12, 4,
            26, 8, 16, 7, 27, 20, 13, 2, 41, 52, 31, 37, 47, 55, 30, 40,
            51, 45, 33, 48, 44, 49, 39, 56, 34, 53, 46, 42, 50, 36, 29, 32
    };

    private static final int[] SHIFTS = {
            1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1
    };

    private static final int[] SBOX = {
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11
    };

    /** Permutes an {@code inBits}-wide value through a 1-indexed table. */
    private static long permute(long value, int[] table, int inBits) {
        long out = 0;
        for (int pos : table) {
            out = (out << 1) | ((value >>> (inBits - pos)) & 1L);
        }
        return out;
    }

    private static long toLong(byte[] data, int offset) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (data[offset + i] & 0xFFL);
        return v;
    }

    private static byte[] toBytes(long value) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        return out;
    }

    /** The sixteen 48-bit subkeys of one 8-byte DES key. */
    private static long[] subkeys(byte[] key8, int offset) {
        long cd = permute(toLong(key8, offset), PC1, 64);
        long c = cd >>> 28;
        long d = cd & 0x0FFFFFFFL;
        long[] out = new long[SHIFTS.length];
        for (int i = 0; i < SHIFTS.length; i++) {
            int shift = SHIFTS[i];
            c = ((c << shift) | (c >>> (28 - shift))) & 0x0FFFFFFFL;
            d = ((d << shift) | (d >>> (28 - shift))) & 0x0FFFFFFFL;
            out[i] = permute((c << 28) | d, PC2, 56);
        }
        return out;
    }

    private static byte[] block(byte[] key8, int keyOffset, byte[] in, int inOffset, boolean decrypt) {
        long[] ks = subkeys(key8, keyOffset);
        long v = permute(toLong(in, inOffset), IP, 64);
        long left = v >>> 32;
        long right = v & 0xFFFFFFFFL;
        for (int round = 0; round < ks.length; round++) {
            long sk = ks[decrypt ? ks.length - 1 - round : round];
            long x = permute(right, E, 32) ^ sk;
            long out = 0;
            for (int i = 0; i < 8; i++) {
                int six = (int) ((x >>> (42 - 6 * i)) & 0x3F);
                int row = ((six >> 4) & 0b10) | (six & 1);
                int col = (six >> 1) & 0xF;
                out = (out << 4) | SBOX[i * 64 + row * 16 + col];
            }
            long next = left ^ permute(out, P, 32);
            left = right;
            right = next;
        }
        return toBytes(permute((right << 32) | left, FP, 64));
    }

    public static byte[] encryptBlock(byte[] key8, byte[] block8) {
        require(key8.length == 8 && block8.length == 8, "DES works on 8-byte keys and blocks");
        return block(key8, 0, block8, 0, false);
    }

    public static byte[] decryptBlock(byte[] key8, byte[] block8) {
        require(key8.length == 8 && block8.length == 8, "DES works on 8-byte keys and blocks");
        return block(key8, 0, block8, 0, true);
    }

    /** K1, K2, K3 of an 8/16/24-byte key, in EDE order. */
    private static int[] partOffsets(byte[] key) {
        return switch (key.length) {
            case 8 -> new int[] {0, 0, 0};
            case 16 -> new int[] {0, 8, 0};
            case 24 -> new int[] {0, 8, 16};
            default -> throw new IllegalArgumentException(
                    "a DES/TDES key is 8, 16 or 24 bytes (got " + key.length + ")");
        };
    }

    /** E_K3(D_K2(E_K1(block))) -- one TDES EDE block, unchained. */
    public static byte[] tdesEncryptBlock(byte[] key, byte[] block8) {
        int[] o = partOffsets(key);
        byte[] a = block(key, o[0], block8, 0, false);
        byte[] b = block(key, o[1], a, 0, true);
        return block(key, o[2], b, 0, false);
    }

    public static byte[] tdesDecryptBlock(byte[] key, byte[] block8) {
        int[] o = partOffsets(key);
        byte[] a = block(key, o[2], block8, 0, true);
        byte[] b = block(key, o[1], a, 0, false);
        return block(key, o[0], b, 0, true);
    }

    public static byte[] tdesEcbEncrypt(byte[] key, byte[] data) {
        require(data.length % BLOCK == 0, "ECB needs a multiple of 8 bytes");
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i += BLOCK) {
            System.arraycopy(tdesEncryptBlock(key, slice(data, i)), 0, out, i, BLOCK);
        }
        return out;
    }

    public static byte[] tdesEcbDecrypt(byte[] key, byte[] data) {
        require(data.length % BLOCK == 0, "ECB needs a multiple of 8 bytes");
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i += BLOCK) {
            System.arraycopy(tdesDecryptBlock(key, slice(data, i)), 0, out, i, BLOCK);
        }
        return out;
    }

    public static byte[] tdesCbcEncrypt(byte[] key, byte[] data) {
        require(data.length % BLOCK == 0, "CBC needs a multiple of 8 bytes");
        byte[] out = new byte[data.length];
        byte[] prev = new byte[BLOCK];
        for (int i = 0; i < data.length; i += BLOCK) {
            prev = tdesEncryptBlock(key, xor(slice(data, i), prev));
            System.arraycopy(prev, 0, out, i, BLOCK);
        }
        return out;
    }

    public static byte[] tdesCbcDecrypt(byte[] key, byte[] data) {
        require(data.length % BLOCK == 0, "CBC needs a multiple of 8 bytes");
        byte[] out = new byte[data.length];
        byte[] prev = new byte[BLOCK];
        for (int i = 0; i < data.length; i += BLOCK) {
            byte[] cipher = slice(data, i);
            System.arraycopy(xor(tdesDecryptBlock(key, cipher), prev), 0, out, i, BLOCK);
            prev = cipher;
        }
        return out;
    }

    public static byte[] xor(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    private static byte[] slice(byte[] data, int from) {
        byte[] out = new byte[BLOCK];
        System.arraycopy(data, from, out, 0, BLOCK);
        return out;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
