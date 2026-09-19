package com.cryptocarver.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Thales payShield Variant LMK: keys protected under a Local Master Key, the
 * way a Racal/Thales payment HSM has done it since before there was a standard
 * for it.
 *
 * <p>Source: <em>payShield 10K Host Programmer's Manual</em> 007-001518-023
 * v2.3a, chapter 7 "Variant LMK Key Scheme". Clause numbers below are that
 * manual's.</p>
 *
 * <h2>Two different things both called a variant</h2>
 *
 * <p>This is the part that costs people an afternoon, because the same word
 * covers two independent XORs applied to two different bytes:</p>
 *
 * <ol>
 *   <li><b>The LMK variant</b> (clause 7.2) enforces <em>key separation</em>:
 *       which kind of key this is. It comes from the second digit of the key
 *       type code and is XORed into the <b>first byte of the LMK</b> — byte 0,
 *       the first byte of the left half.</li>
 *   <li><b>The key scheme variant</b> (clause 7.2.3) enforces <em>key order and
 *       length</em>: that the left half of a double-length key cannot be used
 *       as the right. It comes from the scheme tag and is XORed into the
 *       <b>first byte of the LMK's right half</b> — byte 8 — or, under a
 *       triple-length LMK, the first byte of the middle part.</li>
 * </ol>
 *
 * <p>Both are applied to the encrypting key, never to the key being encrypted,
 * and they compose: a double-length MK-SMI is encrypted under an LMK that has
 * been touched in byte 0 by the key type and in byte 8 by the half being
 * encrypted. Each 8-byte part is then encrypted separately in ECB (clause 7.1),
 * so this is not CBC and not a single 3DES pass over 16 bytes.</p>
 *
 * <h2>Where the published descriptions disagree, and who is right</h2>
 *
 * <p>Two widely cited secondary sources get this wrong in opposite directions —
 * one puts the LMK variant in the <em>last</em> byte of the LMK, the other puts
 * the scheme variant in the last byte of each half. The manual's own worked
 * example in clause 7.2.3 settles it, and that example is reproduced verbatim
 * as a test. The manual itself has a typo: clause 7.1 lists the double-length
 * left part as {@code 6A}, while its own Example 1 and clause 7.2.3 both say
 * {@code A6}. {@code A6} is correct, and the worked example proves it.</p>
 *
 * <h2>Scope</h2>
 *
 * <p>Variant LMKs protect TDES keys only. Clause 7 states they cannot protect
 * AES keys, RSA keys longer than 2048 bits, or ECC keys, so neither can this.
 * Key Blocks — the {@code S} scheme — are a different format and are not here.</p>
 *
 * <p>This is a bench for test keys, handled in the clear.</p>
 */
public final class ThalesLmkOperations {

    /** Clause 7.2 — the LMK variants, XORed into the first byte of the LMK. */
    private static final Map<Integer, Integer> LMK_VARIANTS = Map.of(
            1, 0xA6, 2, 0x5A, 3, 0x6A, 4, 0xDE, 5, 0x2B,
            6, 0x50, 7, 0x74, 8, 0x9C, 9, 0xFA);

    /** Clause 7.2.3 — the scheme variants for a double-length key, in part order. */
    private static final int[] SCHEME_U_VARIANTS = {0xA6, 0x5A};
    /** Clause 7.2.3 — the scheme variants for a triple-length key, in part order. */
    private static final int[] SCHEME_T_VARIANTS = {0x6A, 0xDE, 0x2B};

    /**
     * Clause 7.2.3.1 — the extra variant applied to the first byte of the LMK
     * when what is being encrypted is a key <em>component</em> rather than a
     * whole key, so that one component cannot masquerade as a finished key.
     */
    public static final int COMPONENT_VARIANT = 0xFF;

    /** Clause 7.5 — the two-digit key type code maps to an LMK pair by table, not by arithmetic. */
    private static final Map<String, String> PAIR_BY_CODE = new LinkedHashMap<>();

    static {
        PAIR_BY_CODE.put("00", "04-05");
        PAIR_BY_CODE.put("01", "06-07");
        PAIR_BY_CODE.put("02", "14-15");
        PAIR_BY_CODE.put("03", "16-17");
        PAIR_BY_CODE.put("04", "18-19");
        PAIR_BY_CODE.put("05", "20-21");
        PAIR_BY_CODE.put("06", "22-23");
        PAIR_BY_CODE.put("07", "24-25");
        PAIR_BY_CODE.put("08", "26-27");
        PAIR_BY_CODE.put("09", "28-29");
        PAIR_BY_CODE.put("0A", "30-31");
        PAIR_BY_CODE.put("0B", "32-33");
        PAIR_BY_CODE.put("0C", "34-35");
        PAIR_BY_CODE.put("0D", "36-37");
        PAIR_BY_CODE.put("0E", "38-39");
    }

    /**
     * A key type as the Key Type Table defines it.
     *
     * @param code    the three-digit code, {@code [variant][pair code]} — {@code 209}
     *                is variant 2 of pair code {@code 09}
     * @param name    the name the manual gives it
     * @param lmkPair the LMK pair label, such as {@code 28-29}
     * @param variant 0 to 9
     */
    public record KeyType(String code, String name, String lmkPair, int variant) {
    }

    /** Clause 7.5, Key Type Table 1 — key separation not enforced for PCI HSM. */
    private static final Map<String, KeyType> KEY_TYPES = new LinkedHashMap<>();

    private static void keyType(String pairCode, int variant, String name) {
        String code = Integer.toString(variant) + pairCode;
        KEY_TYPES.put(code, new KeyType(code, name, PAIR_BY_CODE.get(pairCode), variant));
    }

    static {
        keyType("00", 0, "ZMK");
        keyType("00", 1, "ZMK (component)");
        keyType("00", 2, "KML");
        keyType("00", 3, "KEKr");
        keyType("00", 4, "KEKs");

        keyType("01", 0, "ZPK / PEK");
        keyType("01", 1, "Auth Para");

        keyType("02", 0, "PVK / PVVK");
        keyType("02", 1, "TMK1");
        keyType("02", 2, "TMK2");
        keyType("02", 3, "IKEY");
        keyType("02", 4, "CVK / CSCK");
        keyType("02", 6, "KIA");
        keyType("02", 7, "PPASN");

        keyType("03", 0, "TAK");
        keyType("03", 1, "TAKs");
        keyType("03", 2, "TAKr");

        keyType("04", 1, "DTAB");
        keyType("04", 2, "IPB");

        keyType("05", 1, "KML / KMLISS");
        keyType("05", 2, "KMX / KMXISS");
        keyType("05", 3, "KMP / KMPISS");
        keyType("05", 4, "KIS.5");
        keyType("05", 5, "KM3L / KM3LISS");
        keyType("05", 6, "KM3X / KM3XISS");
        keyType("05", 7, "KMACS4");
        keyType("05", 8, "KMACS5");
        keyType("05", 9, "KMACACQ / KMACACK");

        keyType("06", 0, "WWK");
        keyType("06", 1, "KMACUPD");
        keyType("06", 2, "KMACMA");
        keyType("06", 3, "KMACCI / KMACISS");
        keyType("06", 4, "KMSCISS");
        keyType("06", 5, "BKEM");
        keyType("06", 6, "BKAM");

        keyType("07", 1, "KEK");
        keyType("07", 2, "KMC");
        keyType("07", 3, "SK-ENC");
        keyType("07", 4, "SK-MAC");
        keyType("07", 5, "SK-DEK / KD-PERSO");
        keyType("07", 6, "ZKA MK");
        keyType("07", 8, "MK-KE");
        keyType("07", 9, "MK-AS");

        keyType("08", 0, "ZAK");
        keyType("08", 1, "ZAKs");
        keyType("08", 2, "ZAKr");

        keyType("09", 0, "BDK-1");
        keyType("09", 1, "MK-AC");
        keyType("09", 2, "MK-SMI");
        keyType("09", 3, "MK-SMC");
        keyType("09", 4, "MK-DAC");
        keyType("09", 5, "MK-DN");
        keyType("09", 6, "BDK-2");
        keyType("09", 7, "MK-CVC3 / MK-DCVV");
        keyType("09", 8, "BDK-3");
        keyType("09", 9, "BDK-4");

        keyType("0A", 0, "ZEK");
        keyType("0A", 1, "ZEKs");
        keyType("0A", 2, "ZEKr");

        keyType("0B", 0, "DEK / TEK");
        keyType("0B", 1, "TEKs");
        keyType("0B", 2, "TEKr");
        keyType("0B", 3, "TEK");

        keyType("0C", 0, "RSA-SK");
        keyType("0C", 1, "HMAC");

        keyType("0D", 0, "RSA-PK");
        keyType("0D", 3, "CK-ENC");
        keyType("0D", 4, "CK-MAC");
        keyType("0D", 5, "CK-DEK");
        keyType("0D", 6, "DbTAB1");
        keyType("0D", 7, "TPK / KEYVAL / PEK");
        keyType("0D", 8, "TMK / KT / KCA / KMA / KI / T");
        keyType("0D", 9, "TKR");
    }

    /**
     * The key scheme tag, which says how a key is wrapped and travels in front
     * of the ciphertext.
     */
    public enum Scheme {
        /** Single-length DES, no scheme tag in the output and no scheme variant. */
        Z('Z', 1),
        /** Clause 7.2.3 — double-length TDES under the variant scheme. */
        U('U', 2),
        /** Clause 7.2.3 — triple-length TDES under the variant scheme. */
        T('T', 3);

        private final char tag;
        private final int parts;

        Scheme(char tag, int parts) {
            this.tag = tag;
            this.parts = parts;
        }

        public char tag() {
            return tag;
        }

        /** How many 8-byte parts a key of this scheme has. */
        public int parts() {
            return parts;
        }

        public int keyLength() {
            return parts * 8;
        }

        public static Scheme of(char tag) {
            char upper = Character.toUpperCase(tag);
            for (Scheme scheme : values()) {
                if (scheme.tag == upper) {
                    return scheme;
                }
            }
            throw new IllegalArgumentException("Unknown key scheme tag '" + tag
                    + "'. This bench implements the variant schemes Z, U and T; the ANSI schemes "
                    + "X and Y and the Key Block scheme S are different formats");
        }

        /** Clause 7.2.3 — the per-part variants, or none for a single-length key. */
        int[] variants() {
            return switch (this) {
                case Z -> new int[] {-1};
                case U -> SCHEME_U_VARIANTS;
                case T -> SCHEME_T_VARIANTS;
            };
        }
    }

    /**
     * An LMK pair: two halves for a double-length LMK, three parts for a
     * triple-length one. The manual keeps calling it a "pair" either way, for
     * historical reasons it admits to in a note.
     */
    public record Lmk(String hex) {

        public Lmk {
            hex = normalizeHex(hex, "LMK");
            int length = hex.length() / 2;
            if (length != 16 && length != 24) {
                throw new IllegalArgumentException("An LMK pair is a double-length (16 byte) or "
                        + "triple-length (24 byte) TDES key, not " + length + " bytes");
            }
        }

        public static Lmk of(String hex) {
            return new Lmk(hex);
        }

        public int length() {
            return hex.length() / 2;
        }

        /** True when this is a triple-length LMK, whose scheme variant lands on the middle part. */
        public boolean tripleLength() {
            return length() == 24;
        }
    }

    /** A key wrapped under an LMK, as the HSM would report it. */
    public record WrappedKey(String keyType, String keyTypeName, String lmkPair, int variant,
                             Scheme scheme, String cryptogram, String checkValue) {

        /** The form a payShield prints and a host stores: the tag then the cryptogram. */
        public String tagged() {
            return scheme == Scheme.Z ? cryptogram : scheme.tag() + cryptogram;
        }
    }

    private ThalesLmkOperations() {
    }

    // =====================================================================
    // The tables
    // =====================================================================

    public static Map<String, KeyType> keyTypes() {
        return Map.copyOf(KEY_TYPES);
    }

    public static KeyType keyType(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        KeyType type = KEY_TYPES.get(normalized);
        if (type == null) {
            throw new IllegalArgumentException("Unknown key type code '" + normalized
                    + "'. A code is three characters, [variant][pair code], such as 209 for MK-SMI "
                    + "(variant 2 of pair code 09, LMK 28-29)");
        }
        return type;
    }

    /** Clause 7.2 — the variant byte for 1 to 9, or 0 for the untouched LMK. */
    public static int variantByte(int variant) {
        if (variant == 0) {
            return 0x00;
        }
        Integer value = LMK_VARIANTS.get(variant);
        if (value == null) {
            throw new IllegalArgumentException("The LMK variants are 0 to 9, not " + variant);
        }
        return value;
    }

    // =====================================================================
    // The two variants
    // =====================================================================

    /**
     * Clause 7.2 — applies the key type's variant to the first byte of the LMK.
     *
     * @param component whether what will be encrypted is a key component, which
     *                  clause 7.2.3.1 marks with a further {@code FF}
     */
    public static String applyLmkVariant(Lmk lmk, int variant, boolean component) {
        byte[] bytes = bytes(lmk.hex());
        bytes[0] ^= (byte) variantByte(variant);
        if (component) {
            bytes[0] ^= (byte) COMPONENT_VARIANT;
        }
        return hex(bytes);
    }

    /**
     * Clause 7.2.3 — applies a scheme variant to the first byte of the right
     * half, or of the middle part when the LMK is triple-length.
     */
    private static byte[] applySchemeVariant(byte[] lmk, int variant) {
        byte[] copy = lmk.clone();
        if (variant >= 0) {
            copy[8] ^= (byte) variant;
        }
        return copy;
    }

    // =====================================================================
    // Wrapping
    // =====================================================================

    /** Encrypts a clear key under the LMK that its key type selects. */
    public static WrappedKey encrypt(String clearKey, String keyTypeCode, Scheme scheme, Lmk lmk) {
        return encrypt(clearKey, keyTypeCode, scheme, lmk, false);
    }

    public static WrappedKey encrypt(String clearKey, String keyTypeCode, Scheme scheme, Lmk lmk,
                                     boolean component) {
        KeyType type = keyType(keyTypeCode);
        byte[] key = keyBytes(clearKey, scheme, "clear key");
        byte[] variantLmk = bytes(applyLmkVariant(lmk, type.variant(), component));

        byte[] wrapped = new byte[key.length];
        int[] variants = scheme.variants();
        for (int part = 0; part < scheme.parts(); part++) {
            byte[] partKey = applySchemeVariant(variantLmk, variants[part]);
            byte[] block = tripleDes(partKey, Arrays.copyOfRange(key, part * 8, part * 8 + 8), true);
            System.arraycopy(block, 0, wrapped, part * 8, 8);
        }

        return new WrappedKey(type.code(), type.name(), type.lmkPair(), type.variant(), scheme,
                hex(wrapped), checkValue(clearKey));
    }

    /** Decrypts a key that was wrapped under the LMK its key type selects. */
    public static WrappedKey decrypt(String cryptogram, String keyTypeCode, Scheme scheme, Lmk lmk) {
        return decrypt(cryptogram, keyTypeCode, scheme, lmk, false);
    }

    public static WrappedKey decrypt(String cryptogram, String keyTypeCode, Scheme scheme, Lmk lmk,
                                     boolean component) {
        KeyType type = keyType(keyTypeCode);
        byte[] wrapped = keyBytes(stripTag(cryptogram, scheme), scheme, "cryptogram");
        byte[] variantLmk = bytes(applyLmkVariant(lmk, type.variant(), component));

        byte[] clear = new byte[wrapped.length];
        int[] variants = scheme.variants();
        for (int part = 0; part < scheme.parts(); part++) {
            byte[] partKey = applySchemeVariant(variantLmk, variants[part]);
            byte[] block = tripleDes(partKey, Arrays.copyOfRange(wrapped, part * 8, part * 8 + 8), false);
            System.arraycopy(block, 0, clear, part * 8, 8);
        }

        String clearHex = hex(clear);
        return new WrappedKey(type.code(), type.name(), type.lmkPair(), type.variant(), scheme,
                clearHex, checkValue(clearHex));
    }

    /**
     * A key scheme tag travels in front of the cryptogram, so a pasted value
     * usually carries one. Accepting it saves a class of mistake that otherwise
     * shows up as a length error about a key nobody mistyped.
     */
    private static String stripTag(String value, Scheme scheme) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        char first = Character.toUpperCase(trimmed.charAt(0));
        if (first == 'U' || first == 'T' || first == 'Z' || first == 'X' || first == 'Y' || first == 'S') {
            if (first != scheme.tag() && first != 'Z') {
                throw new IllegalArgumentException("The cryptogram is tagged '" + first
                        + "' but was read as scheme " + scheme.tag());
            }
            return trimmed.substring(1);
        }
        return trimmed;
    }

    /**
     * The key check value: the key encrypting eight zero bytes, leftmost three
     * reported. Thales prints six hexadecimal characters of it.
     */
    public static String checkValue(String clearKey) {
        byte[] key = bytes(normalizeHex(clearKey, "clear key"));
        byte[] check = tripleDes(key, new byte[8], true);
        return hex(Arrays.copyOfRange(check, 0, 3));
    }

    // =====================================================================
    // Key lookup
    // =====================================================================

    /** One key type and scheme under which a cryptogram decrypts to a matching check value. */
    public record Match(KeyType keyType, Scheme scheme, String clearKey, String checkValue) {
    }

    /**
     * Finds which key type a cryptogram was wrapped under, by trying every one
     * the table knows and keeping those whose recovered key has the expected
     * check value.
     *
     * <p>Only the variant reaches the cipher, so key types that share a variant
     * on the same LMK pair are indistinguishable and all of them come back. That
     * is not a defect in the search: it is what key separation does and does not
     * buy you. A ZMK and a KEKs under the same pair differ; two names sharing
     * one variant do not.</p>
     *
     * @param expectedCheckValue the check value the key should have, 4 or 6
     *                           hexadecimal characters
     */
    public static List<Match> lookup(String cryptogram, String expectedCheckValue, Lmk lmk) {
        String wanted = normalizeHex(expectedCheckValue, "expected check value");
        if (wanted.length() != 4 && wanted.length() != 6) {
            throw new IllegalArgumentException("A key check value is 4 or 6 hexadecimal characters, not "
                    + wanted.length());
        }
        String trimmed = cryptogram == null ? "" : cryptogram.trim();
        int length = normalizeHex(stripTag(trimmed, schemeFor(trimmed)), "cryptogram").length() / 2;

        List<Match> matches = new ArrayList<>();
        for (KeyType type : KEY_TYPES.values()) {
            for (Scheme scheme : Scheme.values()) {
                if (scheme.keyLength() != length) {
                    continue;
                }
                try {
                    WrappedKey recovered = decrypt(trimmed, type.code(), scheme, lmk);
                    if (recovered.checkValue().startsWith(wanted)) {
                        matches.add(new Match(type, scheme, recovered.cryptogram(), recovered.checkValue()));
                    }
                } catch (RuntimeException notThisOne) {
                    // A key type that cannot read this cryptogram is simply not the answer.
                }
            }
        }
        return List.copyOf(matches);
    }

    /** Reads the tag a pasted cryptogram carries, defaulting to single length. */
    private static Scheme schemeFor(String cryptogram) {
        if (cryptogram == null || cryptogram.isEmpty()) {
            return Scheme.Z;
        }
        char first = Character.toUpperCase(cryptogram.charAt(0));
        return switch (first) {
            case 'U' -> Scheme.U;
            case 'T' -> Scheme.T;
            default -> Scheme.Z;
        };
    }

    // =====================================================================
    // Reporting
    // =====================================================================

    public static String describe(WrappedKey key, Lmk lmk) {
        KeyType type = keyType(key.keyType());
        StringBuilder report = new StringBuilder();
        report.append("Thales Variant LMK\n");
        report.append("  key type   : ").append(type.code()).append(" — ").append(type.name()).append('\n');
        report.append("  LMK pair   : ").append(type.lmkPair()).append(", variant ").append(type.variant());
        if (type.variant() > 0) {
            report.append(" (XOR ").append(byteHex(variantByte(type.variant())))
                    .append(" into the first byte of the LMK)");
        }
        report.append('\n');
        report.append("  LMK variant: ").append(applyLmkVariant(lmk, type.variant(), false)).append('\n');
        report.append("  scheme     : ").append(key.scheme().tag()).append(" — ")
                .append(key.scheme().parts()).append(" part(s) of 8 bytes, each encrypted separately in ECB\n");
        int[] variants = key.scheme().variants();
        for (int part = 0; part < key.scheme().parts(); part++) {
            if (variants[part] < 0) {
                continue;
            }
            report.append("    part ").append(part + 1).append(": XOR ").append(byteHex(variants[part]))
                    .append(" into the first byte of the ")
                    .append(lmk.tripleLength() ? "middle part" : "right half").append('\n');
        }
        report.append("  result     : ").append(key.tagged()).append('\n');
        report.append("  check value: ").append(key.checkValue()).append('\n');
        return report.toString();
    }

    public static String describe(List<Match> matches) {
        if (matches.isEmpty()) {
            return "No key type in the table produces a key with that check value.\n"
                    + "Either the LMK is not the one it was wrapped under, or the scheme is not a variant scheme.\n";
        }
        StringBuilder report = new StringBuilder();
        report.append(matches.size()).append(matches.size() == 1 ? " key type matches" : " key types match")
                .append(":\n");
        for (Match match : matches) {
            report.append("  ").append(match.keyType().code()).append(" — ").append(match.keyType().name())
                    .append(" (LMK ").append(match.keyType().lmkPair())
                    .append(", variant ").append(match.keyType().variant())
                    .append(", scheme ").append(match.scheme().tag()).append(")\n");
        }
        if (matches.size() > 1) {
            report.append("\nSeveral key types share one LMK pair and variant, and only the variant reaches\n");
            report.append("the cipher, so they cannot be told apart by decrypting. The name is a matter of\n");
            report.append("what the key is for, not of what the bytes say.\n");
        }
        return report.toString();
    }

    // =====================================================================
    // Bytes
    // =====================================================================

    /**
     * Triple-DES ECB on one 8-byte block.
     *
     * <p>A 16-byte key is expanded to K1K2K1, which is what the manual means by
     * encrypting under a double-length LMK.</p>
     */
    private static byte[] tripleDes(byte[] key, byte[] block, boolean encrypt) {
        byte[] full = new byte[24];
        if (key.length == 16) {
            System.arraycopy(key, 0, full, 0, 16);
            System.arraycopy(key, 0, full, 16, 8);
        } else if (key.length == 24) {
            full = key.clone();
        } else if (key.length == 8) {
            System.arraycopy(key, 0, full, 0, 8);
            System.arraycopy(key, 0, full, 8, 8);
            System.arraycopy(key, 0, full, 16, 8);
        } else {
            throw new IllegalArgumentException("A TDES key is 8, 16 or 24 bytes, not " + key.length);
        }
        try {
            Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(full, "DESede"));
            return cipher.doFinal(block);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Triple DES is unavailable", e);
        }
    }

    private static byte[] keyBytes(String value, Scheme scheme, String label) {
        String normalized = normalizeHex(value, label);
        int expected = scheme.keyLength();
        if (normalized.length() / 2 != expected) {
            throw new IllegalArgumentException("Scheme " + scheme.tag() + " is a " + expected
                    + "-byte key, but the " + label + " is " + (normalized.length() / 2) + " bytes");
        }
        return bytes(normalized);
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
        return hex.isEmpty() ? new byte[0] : HexFormat.of().parseHex(hex);
    }

    private static String hex(byte[] data) {
        return HexFormat.of().formatHex(data).toUpperCase(Locale.ROOT);
    }

    private static String byteHex(int value) {
        return String.format("%02X", value & 0xFF);
    }

    static {
        Objects.requireNonNull(PAIR_BY_CODE);
    }
}
