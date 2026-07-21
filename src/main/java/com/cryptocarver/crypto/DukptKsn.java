package com.cryptocarver.crypto;

/** Strict parser for the 10-byte TDES DUKPT Key Serial Number (ANSI X9.24). */
public final class DukptKsn {
    public static final int MAX_TRANSACTION_COUNTER = 0x1F_FFFF;
    private DukptKsn() { }

    public record Parsed(String ksnHex, String baseKsnHex, String deviceIdentifierHex, int transactionCounter) { }

    public record TdesDerivedKey(String ipekHex, java.util.List<String> derivationSteps, String workingKeyHex) { }

    public enum TdesKeyUsage {
        PIN_ENCRYPTION("PIN Encryption"),
        MAC_REQUEST("MAC Request"),
        MAC_RESPONSE("MAC Response"),
        DATA_ENCRYPTION("Data Encryption");
        private final String label;
        TdesKeyUsage(String label) { this.label = label; }
        public String label() { return label; }
    }

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

    /** Derives the terminal key and requested working key, generating the derivation steps. */
    public static TdesDerivedKey deriveWorkingKey(String ipekHex, String ksnHex, TdesKeyUsage usage) throws Exception {
        byte[] ipek = decodeKey(ipekHex, "IPEK");
        Parsed parsedKsn = parseTdes(ksnHex);
        byte[] ksnBytes = decodeHex(parsedKsn.ksnHex());
        byte[] baseKsn = decodeHex(parsedKsn.baseKsnHex());
        byte[] currentKsn = java.util.Arrays.copyOfRange(baseKsn, 2, 10);
        int counter = parsedKsn.transactionCounter();

        byte[] currentKey = ipek.clone();
        int shifter = 0x100000;
        java.util.List<String> steps = new java.util.ArrayList<>();

        while (shifter > 0) {
            if ((counter & shifter) != 0) {
                currentKsn[5] |= (byte) ((shifter >> 16) & 0xFF);
                currentKsn[6] |= (byte) ((shifter >> 8) & 0xFF);
                currentKsn[7] |= (byte) (shifter & 0xFF);

                currentKey = nonReversibleKeyGen(currentKey, currentKsn);
                steps.add(toHex(currentKey));
            }
            shifter >>= 1;
        }

        byte[] mask = new byte[16];
        boolean tdesVariant = false;
        switch (usage) {
            case PIN_ENCRYPTION: mask = decodeHex("00000000000000FF00000000000000FF"); break;
            case MAC_REQUEST: mask = decodeHex("000000000000FF00000000000000FF00"); break;
            case MAC_RESPONSE: mask = decodeHex("0000000000FF00000000000000FF0000"); break;
            case DATA_ENCRYPTION:
                mask = decodeHex("0000000000FF00000000000000FF0000");
                tdesVariant = true;
                break;
        }

        byte[] workingKey = currentKey.clone();
        for (int i = 0; i < 16; i++) workingKey[i] ^= mask[i];

        if (tdesVariant) {
            // Data encryption variant encrypts the masked key with the terminal key
            // Left half = TDES(TerminalKey, masked_left)
            byte[] left = tdesEncrypt(currentKey, java.util.Arrays.copyOfRange(workingKey, 0, 8));
            byte[] right = tdesEncrypt(currentKey, java.util.Arrays.copyOfRange(workingKey, 8, 16));
            workingKey = concat(left, right);
        }

        return new TdesDerivedKey(ipekHex, steps, toHex(workingKey));
    }

    private static byte[] nonReversibleKeyGen(byte[] key, byte[] reg) throws Exception {
        byte[] kl = java.util.Arrays.copyOfRange(key, 0, 8);
        byte[] kr = java.util.Arrays.copyOfRange(key, 8, 16);

        byte[] msg = reg.clone();
        for (int i = 0; i < 8; i++) msg[i] ^= kr[i];
        byte[] right = desEncrypt(kl, msg);
        for (int i = 0; i < 8; i++) right[i] ^= kr[i];

        byte[] mask = decodeHex("C0C0C0C000000000C0C0C0C000000000");
        byte[] kPrime = key.clone();
        for (int i = 0; i < 16; i++) kPrime[i] ^= mask[i];
        byte[] kpl = java.util.Arrays.copyOfRange(kPrime, 0, 8);
        byte[] kpr = java.util.Arrays.copyOfRange(kPrime, 8, 16);

        byte[] msg2 = reg.clone();
        for (int i = 0; i < 8; i++) msg2[i] ^= kpr[i];
        byte[] left = desEncrypt(kpl, msg2);
        for (int i = 0; i < 8; i++) left[i] ^= kpr[i];

        return concat(left, right);
    }

    private static byte[] desEncrypt(byte[] key, byte[] data) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DES/ECB/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "DES"));
        return cipher.doFinal(data);
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

    public static byte[] hexToBytes(String hex) {
        String value = hex == null ? "" : hex.replaceAll("\\s+", "");
        if (!value.matches("(?i)[0-9a-f]+") || value.length() % 2 != 0) throw new IllegalArgumentException("Invalid hexadecimal value");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    private static byte[] decodeHex(String hex) {
        return hexToBytes(hex);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02X", b));
        return out.toString();
    }

    private static String toHex(byte[] bytes) {
        return bytesToHex(bytes);
    }

    public static boolean isTdesCounterExhausted(String ksnHex) {
        return parseTdes(ksnHex).transactionCounter() >= MAX_TRANSACTION_COUNTER;
    }

    public static String nextTdesKsn(String ksnHex) {
        Parsed parsed = parseTdes(ksnHex);
        if (parsed.transactionCounter() >= MAX_TRANSACTION_COUNTER) throw new IllegalStateException("Counter exhausted");
        byte[] ksn = hexToBytes(ksnHex);
        int counter = parsed.transactionCounter() + 1;
        ksn[7] = (byte) ((ksn[7] & 0xE0) | ((counter >> 16) & 0x1F));
        ksn[8] = (byte) ((counter >> 8) & 0xFF);
        ksn[9] = (byte) (counter & 0xFF);
        return bytesToHex(ksn);
    }
}
