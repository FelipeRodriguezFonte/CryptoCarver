package com.cryptocarver.crypto;

import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * SafeNet keys under a KM (master key).
 *
 * <p>Two things vary independently, and confusing them is the whole difficulty:</p>
 *
 * <ul>
 *   <li><b>The key format</b> says how the key is enciphered. {@code 11} is a
 *       double-length TDES key in ECB; {@code 13} is the same key in CBC with a
 *       zero IV. Nothing else about them differs, which means a key stored
 *       under the wrong one of the two decrypts to a first block that is
 *       <em>correct</em> and a second that is not, because CBC's first block
 *       with a zero IV is exactly ECB's. A hand check of the first eight bytes
 *       cannot tell the two formats apart, and there is a test saying so.</li>
 *   <li><b>The KM variant</b> says what the key is for. It is a single byte
 *       XORed across all 24 bytes of the KM, and the values are a table, not a
 *       formula: {@code 00} (DPK) leaves the KM alone, {@code 01} (PPK) is
 *       {@code 28}, {@code 07} (KPV/DT) is {@code 18}.</li>
 * </ul>
 *
 * <h2>Where this came from, and what is missing</h2>
 *
 * <p>From EFTLab's BP-Tools Cryptographic Calculator 21.06, captured
 * 2026-09-19. The tool prints the KM with the variant already applied beside
 * its result, which is what made the variant byte readable at all rather than
 * something to be inferred from ciphertext.</p>
 *
 * <p>Nine variants of the table are known (00 to 08) and the rest are not. There is no
 * arithmetic linking {@code 01 -> 28} to {@code 07 -> 18}, so the missing ones
 * cannot be derived — only read off the tool. {@link #variant} refuses a code
 * it has not seen rather than passing the KM through unchanged, because
 * "unchanged" is itself a valid variant here ({@code 00}) and a silent wrong
 * answer would be indistinguishable from a right one.</p>
 */
public final class SafeNetKmOperations {

    /** The KM variants read off the tool, by their two-digit code. */
    private static final Map<String, Variant> VARIANTS = new LinkedHashMap<>();

    static {
        variant("00", 0x00, "DPK");
        variant("01", 0x28, "PPK");
        variant("02", 0x24, "MPK");
        variant("03", 0x44, "KIS");
        variant("04", 0x88, "KIR");
        variant("05", 0x22, "KTM");
        variant("06", 0x20, "CSCK");
        variant("07", 0x18, "KPV, DT");
        variant("08", 0x14, "KPVV");
    }

    private static void variant(String code, int constant, String name) {
        VARIANTS.put(code, new Variant(code, constant, name));
    }

    private SafeNetKmOperations() {
    }

    /** One entry of the variant table. */
    public record Variant(String code, int constant, String name) {
    }

    /** How a key is enciphered under the KM. */
    public enum KeyFormat {
        /** {@code 11} — double-length TDES, ECB. */
        DOUBLE_ECB("11", "Double-length DES3 (ECB Encrypted)", false, "11", 16),
        /** {@code 12} — triple-length TDES, ECB. */
        TRIPLE_ECB("12", "Triple-length DES3 (ECB Encrypted)", false, "19", 24),
        /** {@code 13} — double-length TDES, CBC with a zero IV. */
        DOUBLE_CBC("13", "Double-length DES3 (CBC Encrypted)", true, "11", 16),
        /** {@code 14} — triple-length TDES, CBC with a zero IV. */
        TRIPLE_CBC("14", "Triple-length DES3 (CBC Encrypted)", true, "19", 24);

        private final String code;
        private final String description;
        private final boolean chained;
        private final String hostPrefix;
        private final int keyBytes;

        KeyFormat(String code, String description, boolean chained, String hostPrefix, int keyBytes) {
            this.code = code;
            this.description = description;
            this.chained = chained;
            this.hostPrefix = hostPrefix;
            this.keyBytes = keyBytes;
        }

        /** The two characters a host stores in front of the format code. */
        public String hostPrefix() {
            return hostPrefix;
        }

        /** The clear key length this format is for. */
        public int keyBytes() {
            return keyBytes;
        }

        public String code() {
            return code;
        }

        public String description() {
            return description;
        }

        public boolean chained() {
            return chained;
        }

        public static KeyFormat of(String code) {
            for (KeyFormat format : values()) {
                if (format.code.equals(code)) {
                    return format;
                }
            }
            throw new IllegalArgumentException("Key format '" + code + "' has not been verified "
                    + "against a vector. The ones that have are 11 and 13 (double-length, ECB "
                    + "and CBC) and 12 and 14 (triple-length, ECB and CBC); the single-length "
                    + "formats need a capture of their own");
        }
    }

    /** A key under a KM, in both the forms the tool shows. */
    public record WrappedKey(KeyFormat format, Variant variant, String cryptogram,
                             String hostStoredKey, String checkValue) {
    }

    public static Map<String, Variant> variants() {
        return Map.copyOf(VARIANTS);
    }

    /** Looks up a variant, refusing one that has no vector behind it. */
    public static Variant variant(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        Variant found = VARIANTS.get(normalized);
        if (found == null) {
            throw new IllegalArgumentException("KM variant '" + normalized + "' is not in the "
                    + "table. The known ones are " + String.join(", ", VARIANTS.keySet())
                    + ". The variant constants are a table and not a formula — 01 is 28 and 07 "
                    + "is 18 — so an unknown one cannot be derived, only read off the tool");
        }
        return found;
    }

    /** Applies a KM variant: one byte XORed across the whole KM. */
    public static String applyVariant(String km, String variantCode) {
        byte[] key = masterKey(km);
        int constant = variant(variantCode).constant();
        byte[] out = new byte[key.length];
        for (int i = 0; i < key.length; i++) {
            out[i] = (byte) (key[i] ^ constant);
        }
        return hex(out);
    }

    /** Encrypts a clear key under the KM. */
    public static WrappedKey encrypt(String clearKey, String km, String formatCode, String variantCode) {
        KeyFormat format = KeyFormat.of(formatCode);
        Variant used = variant(variantCode);
        byte[] key = keyBytes(clearKey);
        requireKeyLength(format, key.length);
        byte[] applied = bytes(applyVariant(km, variantCode));

        byte[] wrapped = format.chained()
                ? cipher(applied, new byte[8], key, true)
                : cipher(applied, null, key, true);
        String cryptogram = hex(wrapped);
        return new WrappedKey(format, used, cryptogram, hostStored(format, cryptogram),
                checkValue(clearKey));
    }

    /** Decrypts a cryptogram under the KM. */
    public static WrappedKey decrypt(String cryptogram, String km, String formatCode, String variantCode) {
        KeyFormat format = KeyFormat.of(formatCode);
        Variant used = variant(variantCode);
        byte[] wrapped = keyBytes(cryptogram);
        requireKeyLength(format, wrapped.length);
        byte[] applied = bytes(applyVariant(km, variantCode));

        String clear = hex(format.chained()
                ? cipher(applied, new byte[8], wrapped, false)
                : cipher(applied, null, wrapped, false));
        return new WrappedKey(format, used, clear, hostStored(format, cryptogram), checkValue(clear));
    }

    /**
     * The form a host stores: two characters, then the format code, then the
     * cryptogram.
     *
     * <p>The leading two characters were {@code 11} on both double-length
     * formats and {@code 19} on both triple-length ones, which is how they are
     * read here: a property of the format. Whether they encode the key length
     * or something that merely correlates with it is not established, because
     * the two lengths never varied independently of the format. A host key
     * whose first two characters are neither should be treated as unrecognised
     * rather than misparsed.</p>
     *
     * <p>The variant is <b>not</b> in there. Whatever the key is for has to
     * travel some other way, which is a real interoperability hazard and not a
     * quirk of this bench.</p>
     */
    public static String hostStored(KeyFormat format, String cryptogram) {
        return format.hostPrefix() + format.code() + normalizeHex(cryptogram, "cryptogram");
    }

    public static String describe(WrappedKey wrapped, String km) {
        StringBuilder report = new StringBuilder("SafeNet key under KM\n");
        report.append("  key format    : ").append(wrapped.format().code()).append(" — ")
                .append(wrapped.format().description()).append('\n');
        report.append("  KM variant    : ").append(wrapped.variant().code()).append(" — ")
                .append(wrapped.variant().name()).append("   (XOR ")
                .append(String.format("%02X", wrapped.variant().constant())).append(")\n");
        report.append("  KM applied    : ").append(applyVariant(km, wrapped.variant().code())).append('\n');
        report.append("  cryptogram    : ").append(wrapped.cryptogram()).append('\n');
        report.append("  host-stored   : ").append(wrapped.hostStoredKey()).append('\n');
        report.append("  check value   : ").append(wrapped.checkValue()).append('\n');
        report.append("\nThe host-stored form carries the format but not the variant, so nothing\n");
        report.append("in a stored SafeNet key says what the key is for. There is no MAC either:\n");
        report.append("the wrong variant yields a wrong key, not an error. Compare check values.\n");
        return report.toString();
    }

    public static String checkValue(String clearKey) {
        return hex(cipher(keyBytes(clearKey), null, new byte[8], true)).substring(0, 6);
    }

    // =====================================================================

    private static byte[] masterKey(String km) {
        byte[] key = bytes(normalizeHex(km, "KM"));
        if (key.length != 16 && key.length != 24) {
            throw new IllegalArgumentException("A KM is a double- or triple-length TDES key "
                    + "(16 or 24 bytes), not " + key.length);
        }
        return key;
    }

    private static void requireKeyLength(KeyFormat format, int actual) {
        if (actual != format.keyBytes()) {
            throw new IllegalArgumentException("Format " + format.code() + " is for a "
                    + format.keyBytes() + "-byte key, not " + actual);
        }
    }

    private static byte[] keyBytes(String value) {
        byte[] key = bytes(normalizeHex(value, "key"));
        if (key.length == 0 || key.length % 8 != 0) {
            throw new IllegalArgumentException("A key is a whole number of 8-byte blocks, not "
                    + key.length + " bytes");
        }
        return key;
    }

    /** A null {@code iv} means ECB. */
    private static byte[] cipher(byte[] key, byte[] iv, byte[] data, boolean encrypt) {
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
            Cipher cipher = Cipher.getInstance(iv == null ? "DESede/ECB/NoPadding" : "DESede/CBC/NoPadding");
            int mode = encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
            SecretKeySpec spec = new SecretKeySpec(full, "DESede");
            if (iv == null) {
                cipher.init(mode, spec);
            } else {
                cipher.init(mode, spec, new IvParameterSpec(iv));
            }
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
