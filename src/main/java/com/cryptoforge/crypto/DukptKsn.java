package com.cryptoforge.crypto;

/** Strict parser for the 10-byte TDES DUKPT Key Serial Number (ANSI X9.24). */
public final class DukptKsn {
    public static final int MAX_TRANSACTION_COUNTER = 0x1F_FFFF;
    private DukptKsn() { }

    public record Parsed(String ksnHex, String baseKsnHex, String deviceIdentifierHex, int transactionCounter) { }

    public static Parsed parseTdes(String hex) {
        String normalized = hex == null ? "" : hex.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9A-F]{20}")) {
            throw new IllegalArgumentException("A TDES DUKPT KSN must contain exactly 10 bytes (20 hexadecimal characters)");
        }
        byte[] value = new byte[10];
        for (int i = 0; i < value.length; i++) value[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        int counter = ((value[7] & 0x1F) << 16) | ((value[8] & 0xFF) << 8) | (value[9] & 0xFF);
        byte[] base = value.clone();
        base[7] &= (byte) 0xE0;
        base[8] = 0;
        base[9] = 0;
        String baseHex = toHex(base);
        return new Parsed(normalized, baseHex, baseHex.substring(0, 14), counter);
    }

    /** Derives the 16-byte TDES IPEK from a double/triple-length BDK and a 10-byte KSN. */
    public static String deriveIpek(String bdkHex, String ksnHex) throws Exception {
        byte[] bdk = decodeKey(bdkHex, "BDK");
        Parsed ksn = parseTdes(ksnHex);
        byte[] ksnBase = decodeHex(ksn.baseKsnHex());
        byte[] data = java.util.Arrays.copyOf(ksnBase, 8);
        byte[] left = tdesEncrypt(bdk, data);
        byte[] masked = bdk.clone();
        byte[] mask = decodeHex("C0C0C0C000000000C0C0C0C000000000");
        for (int i = 0; i < 16; i++) masked[i] ^= mask[i];
        byte[] right = tdesEncrypt(masked, data);
        return toHex(concat(left, right));
    }

    /** Returns the next KSN counter value and refuses unsafe wrap-around/reuse. */
    public static String nextTdesKsn(String ksnHex) {
        Parsed parsed = parseTdes(ksnHex);
        if (parsed.transactionCounter() >= MAX_TRANSACTION_COUNTER) {
            throw new IllegalStateException("DUKPT transaction counter exhausted; inject a new initial key/KSN before continuing");
        }
        byte[] value = decodeHex(parsed.ksnHex());
        int next = parsed.transactionCounter() + 1;
        value[7] = (byte) ((value[7] & 0xE0) | ((next >>> 16) & 0x1F));
        value[8] = (byte) (next >>> 8);
        value[9] = (byte) next;
        return toHex(value);
    }

    public static boolean isTdesCounterExhausted(String ksnHex) {
        return parseTdes(ksnHex).transactionCounter() >= MAX_TRANSACTION_COUNTER;
    }

    private static byte[] tdesEncrypt(byte[] key, byte[] data) throws Exception {
        byte[] key24 = key.length == 16 ? concat(key, java.util.Arrays.copyOf(key, 8)) : key;
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key24, "DESede"));
        return cipher.doFinal(data);
    }

    private static byte[] decodeKey(String hex, String label) {
        byte[] key = decodeHex(hex);
        if (key.length != 16 && key.length != 24) throw new IllegalArgumentException(label + " must be 16 or 24 bytes of hexadecimal");
        return key;
    }

    private static byte[] decodeHex(String hex) {
        String value = hex == null ? "" : hex.replaceAll("\\s+", "");
        if (!value.matches("(?i)[0-9a-f]+") || value.length() % 2 != 0) throw new IllegalArgumentException("Invalid hexadecimal value");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02X", b));
        return out.toString();
    }
}
