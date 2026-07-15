package com.cryptoforge.crypto;

import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** AES DUKPT host-side derivation per ANSI X9.24-3 (AES DUKPT). */
public final class AesDukpt {
    public enum KeyType {
        AES128(16, 0x0002), AES192(24, 0x0003), AES256(32, 0x0004);
        final int bytes, algorithmIndicator;
        KeyType(int bytes, int algorithmIndicator) { this.bytes = bytes; this.algorithmIndicator = algorithmIndicator; }
        public static KeyType fromBytes(int bytes) {
            return switch (bytes) { case 16 -> AES128; case 24 -> AES192; case 32 -> AES256; default -> throw new IllegalArgumentException("AES DUKPT BDK must be 16, 24, or 32 bytes"); };
        }
    }
    public enum KeyUsage {
        PIN_ENCRYPTION(0x1000, "PIN encryption"),
        MAC_GENERATION(0x2000, "MAC generation"),
        MAC_VERIFICATION(0x2001, "MAC verification"),
        MAC_BOTH_WAYS(0x2002, "MAC both ways"),
        DATA_ENCRYPTION_ENCRYPT(0x3000, "Data encryption (encrypt)"),
        DATA_ENCRYPTION_DECRYPT(0x3001, "Data encryption (decrypt)"),
        DATA_ENCRYPTION_BOTH_WAYS(0x3002, "Data encryption (both ways)"),
        KEY_ENCRYPTION(0x0002, "Key encryption"),
        KEY_DERIVATION(0x8000, "Key derivation");
        final int code; final String label;
        KeyUsage(int code, String label) { this.code = code; this.label = label; }
        public String label() { return label; }
    }
    public record ParsedKsn(String ksnHex, String initialKeyIdHex, long transactionCounter, String baseKsnHex) { }
    public record DerivedKey(String initialKeyHex, String intermediateKeyHex, String derivationDataHex, String workingKeyHex) { }
    private AesDukpt() { }

    public static ParsedKsn parseKsn(String input) {
        byte[] ksn = decode(input, "AES DUKPT KSN");
        if (ksn.length != 12) throw new IllegalArgumentException("AES DUKPT KSN must be exactly 12 bytes (24 hexadecimal characters)");
        long counter = ((long) (ksn[8] & 0xff) << 24) | ((long) (ksn[9] & 0xff) << 16) | ((long) (ksn[10] & 0xff) << 8) | (ksn[11] & 0xffL);
        byte[] base = ksn.clone(); Arrays.fill(base, 8, 12, (byte) 0);
        return new ParsedKsn(hex(ksn), hex(Arrays.copyOf(ksn, 8)), counter, hex(base));
    }

    public static boolean isCounterExhausted(String ksn) { return parseKsn(ksn).transactionCounter() == 0xffffffffL; }
    public static String nextKsn(String ksn) {
        ParsedKsn parsed = parseKsn(ksn);
        if (parsed.transactionCounter() == 0xffffffffL) throw new IllegalStateException("AES DUKPT transaction counter is exhausted");
        byte[] result = decode(parsed.ksnHex(), "AES DUKPT KSN"); long next = parsed.transactionCounter() + 1;
        result[8] = (byte) (next >>> 24); result[9] = (byte) (next >>> 16); result[10] = (byte) (next >>> 8); result[11] = (byte) next;
        return hex(result);
    }

    public static String deriveInitialKey(String bdkHex, String ksnHex) throws Exception {
        byte[] bdk = decode(bdkHex, "AES DUKPT BDK"); KeyType type = KeyType.fromBytes(bdk.length);
        ParsedKsn ksn = parseKsn(ksnHex);
        return hex(deriveKey(bdk, type, initialDerivationData(type, decode(ksn.initialKeyIdHex(), "Initial Key ID"))));
    }

    public static DerivedKey deriveWorkingKey(String bdkHex, String ksnHex, KeyUsage usage, KeyType outputType) throws Exception {
        if (usage == null || outputType == null) throw new IllegalArgumentException("AES DUKPT key usage and output type are required");
        byte[] bdk = decode(bdkHex, "AES DUKPT BDK"); KeyType deriveType = KeyType.fromBytes(bdk.length);
        ParsedKsn parsed = parseKsn(ksnHex); byte[] initialId = decode(parsed.initialKeyIdHex(), "Initial Key ID");
        byte[] initialKey = deriveKey(bdk, deriveType, initialDerivationData(deriveType, initialId));
        byte[] intermediate = initialKey;
        for (long mask = 0x80000000L, counter = 0; mask != 0; mask >>>= 1) {
            if ((parsed.transactionCounter() & mask) != 0) {
                counter |= mask;
                intermediate = deriveKey(intermediate, deriveType, workingDerivationData(KeyUsage.KEY_DERIVATION, deriveType, initialId, counter));
            }
        }
        byte[] finalData = workingDerivationData(usage, outputType, initialId, parsed.transactionCounter());
        byte[] working = deriveKey(intermediate, outputType, finalData);
        return new DerivedKey(hex(initialKey), hex(intermediate), hex(finalData), hex(working));
    }

    /**
     * Encrypts or decrypts one ISO 9564-4 PIN block with an AES-DUKPT PIN key.
     * The caller supplies a fully formatted 16-byte PIN block; formatting a PIN
     * block is deliberately kept separate from its cryptographic protection.
     */
    public static String cryptPinBlock(String bdkHex, String ksnHex, KeyType outputType,
            String pinBlockHex, boolean decrypt) throws Exception {
        byte[] pinBlock = decode(pinBlockHex, "AES PIN block");
        if (pinBlock.length != 16) {
            throw new IllegalArgumentException("AES PIN block must be exactly 16 bytes (32 hexadecimal characters)");
        }
        DerivedKey derived = deriveWorkingKey(bdkHex, ksnHex, KeyUsage.PIN_ENCRYPTION, outputType);
        byte[] key = decode(derived.workingKeyHex(), "AES DUKPT PIN key");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(decrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return hex(cipher.doFinal(pinBlock));
    }

    private static byte[] initialDerivationData(KeyType type, byte[] initialKeyId) {
        byte[] data = new byte[16]; data[0] = 0x01; data[1] = 0x01; put16(data, 2, 0x8001); put16(data, 4, type.algorithmIndicator); put16(data, 6, type.bytes * 8);
        System.arraycopy(initialKeyId, 0, data, 8, 8); return data;
    }
    private static byte[] workingDerivationData(KeyUsage usage, KeyType type, byte[] initialKeyId, long counter) {
        byte[] data = new byte[16]; data[0] = 0x01; data[1] = 0x01; put16(data, 2, usage.code); put16(data, 4, type.algorithmIndicator); put16(data, 6, type.bytes * 8);
        System.arraycopy(initialKeyId, 4, data, 8, 4); put32(data, 12, counter); return data;
    }
    private static byte[] deriveKey(byte[] derivationKey, KeyType outputType, byte[] derivationData) throws Exception {
        byte[] output = new byte[((outputType.bytes + 15) / 16) * 16];
        for (int block = 0; block < output.length / 16; block++) {
            derivationData[1] = (byte) (block + 1);
            System.arraycopy(aesEcb(derivationKey, derivationData), 0, output, block * 16, 16);
        }
        return Arrays.copyOf(output, outputType.bytes);
    }
    private static byte[] aesEcb(byte[] key, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES")); return cipher.doFinal(data);
    }
    private static void put16(byte[] output, int offset, int value) { output[offset] = (byte) (value >>> 8); output[offset + 1] = (byte) value; }
    private static void put32(byte[] output, int offset, long value) { output[offset] = (byte) (value >>> 24); output[offset + 1] = (byte) (value >>> 16); output[offset + 2] = (byte) (value >>> 8); output[offset + 3] = (byte) value; }
    private static byte[] decode(String input, String label) {
        String clean = input == null ? "" : input.replaceAll("\\s+", "");
        if (clean.isEmpty() || (clean.length() & 1) != 0 || !clean.matches("[0-9a-fA-F]+")) throw new IllegalArgumentException(label + " must be an even-length hexadecimal value");
        return java.util.HexFormat.of().parseHex(clean);
    }
    private static String hex(byte[] input) { return java.util.HexFormat.of().withUpperCase().formatHex(input); }
}
