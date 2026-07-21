package com.cryptocarver.pin;

import java.util.Locale;

public final class CryptoTools {
    private CryptoTools() {
    }

    public static boolean asciiNumeric(String s) {
        if (s == null || s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9')
                return false;
        }
        return true;
    }

    public static boolean asciiHexChar(String s) {
        if (s == null || s.length() != 1)
            return false;
        char c = Character.toUpperCase(s.charAt(0));
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
    }

    public static byte[] xor(byte[] a, byte[] b) {
        if (a == null || b == null)
            throw new IllegalArgumentException("xor: null");
        if (a.length != b.length)
            throw new IllegalArgumentException("xor: length mismatch");
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++)
            out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null)
            throw new IllegalArgumentException("hex is null");
        String h = hex.trim();
        if ((h.length() & 1) != 0)
            throw new IllegalArgumentException("hex must have even length");
        int len = h.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(h.charAt(2 * i), 16);
            int lo = Character.digit(h.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0)
                throw new IllegalArgumentException("invalid hex: " + hex);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static String bytesToHexUpper(byte[] data) {
        if (data == null)
            return null;
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data)
            sb.append(String.format(Locale.ROOT, "%02X", b));
        return sb.toString();
    }
}
