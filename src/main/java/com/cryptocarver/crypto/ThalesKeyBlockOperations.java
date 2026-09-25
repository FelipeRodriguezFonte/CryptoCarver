package com.cryptocarver.crypto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Thales Key Blocks — key scheme {@code S}: reading one, checking it against
 * its own rules, and building a header that obeys them.
 *
 * <p>Source: <em>payShield 10K Host Programmer's Manual</em> 007-001518-023
 * v2.3a, chapter 8 "Key Block LMK Key Scheme". Clause numbers below are that
 * manual's.</p>
 *
 * <h2>What this is, next to TR-31</h2>
 *
 * <p>A Thales Key Block has the same shape as an ANSI X9.143 / TR-31 block —
 * clause 8.2 says so outright — and this repository already implements X9.143
 * in {@link TR31}. The differences are small and they are exactly what makes a
 * generic TR-31 parser refuse or mislabel a Thales block:</p>
 *
 * <ul>
 *   <li><b>The version ID is a digit, not a letter.</b> Byte 0 is {@code '0'}
 *       under a 3DES Key Block LMK and {@code '1'} under an AES one, where
 *       X9.143 has {@code 'A'}, {@code 'B'}, {@code 'C'} or {@code 'D'}. A
 *       TR-31 reader rejects the block on its first byte.</li>
 *   <li><b>Thales adds its own key usage codes.</b> {@code '13'} is a Visa CVV
 *       key, {@code '72'} a ZPK, {@code '42'} a BDK-3. Clause 8.5.1.2 gives
 *       each one an X9.143 equivalent to convert to on export, and several have
 *       none at all — {@code '57'}, the Italian MKPOS/MKSER, is not
 *       representable in X9.143 and the manual says so.</li>
 *   <li><b>Thales adds optional header blocks.</b> {@code KS}, {@code KV} and
 *       {@code PB} come from X9.143; {@code 00} to {@code 05} are proprietary.</li>
 * </ul>
 *
 * <h2>What the key usage table is for</h2>
 *
 * <p>It is not a list of names. Clause 8.5.1.2 states, per usage, which
 * algorithms and which modes of use are permitted, and that is what turns a
 * parser into something worth running: a block declaring usage {@code '42'}
 * (BDK-3) with algorithm {@code 'A'} is malformed, because Thales allows only
 * {@code 'T'} there. A header is a set of restrictions, and a restriction
 * nobody checks is a comment.</p>
 *
 * <h2>The cryptography, and where it came from</h2>
 *
 * <p>Chapter 8 states the algorithms but never states <b>how the encryption and
 * authentication keys come from the LMK</b>: clauses 8.6 and 8.7 both say only
 * "a variant of the LMK". It also leaves the authenticator's scope ambiguous,
 * saying "the Key Data" for 3DES where it says "the <em>clear</em> key block"
 * for AES. Neither gap can be closed by reasoning, and guessing produces blocks
 * that look perfect and that no HSM accepts.</p>
 *
 * <p>Both were settled by generating a key block with an external tool, under the 3DES Key Block test LMK that the
 * manual publishes in clause 8.8.1, and reading back what it derived:</p>
 *
 * <ul>
 *   <li>{@code KBEK = KBPK XOR 45..45} and {@code KBAK = KBPK XOR 4D..4D},
 *       across the whole key — the variant method of ANSI X9.143 versions A and
 *       C, with {@code 'E'} for encryption and {@code 'M'} for MAC.</li>
 *   <li>The authenticator covers the header and the <b>encrypted</b> key data.
 *       The manual's asymmetric wording was meaningful, not a slip: computing it
 *       over the clear data gives {@code C033654B} where the HSM gives
 *       {@code 31D00034}.</li>
 * </ul>
 *
 * <p>That vector is reproduced in the tests. Only the 3DES scheme is
 * implemented: the AES one derives its keys rather than varying them, and no
 * vector for it has been obtained, so it is refused by name instead of
 * guessed at.</p>
 */
public final class ThalesKeyBlockOperations {

    /** Clause 8.5 — the scheme tag that marks a Thales Key Block. */
    public static final char SCHEME_TAG = 'S';
    /** Clause 8.5.1 — the header is always this long, in ASCII characters. */
    public static final int HEADER_LENGTH = 16;
    /** Clause 8.5.2 — an optional block is at least an identifier and a length. */
    public static final int MINIMUM_OPTIONAL_BLOCK_LENGTH = 4;

    private static final String ERROR = "ERROR";
    private static final String WARNING = "WARNING";
    private static final String INFO = "INFO";

    private ThalesKeyBlockOperations() {
    }

    /** Clause 8.5.1 — byte 0, which says which kind of LMK protects the block. */
    public enum VersionId {
        /** {@code '0'} — protected by a 3DES Key Block LMK: 8-byte blocks, 4-byte authenticator. */
        DES('0', "3DES Key Block LMK", 8, 8),
        /** {@code '1'} — protected by an AES Key Block LMK: 16-byte blocks, 8-byte authenticator. */
        AES('1', "AES Key Block LMK", 16, 16);

        private final char tag;
        private final String description;
        private final int blockBytes;
        private final int authenticatorCharacters;

        VersionId(char tag, String description, int blockBytes, int authenticatorCharacters) {
            this.tag = tag;
            this.description = description;
            this.blockBytes = blockBytes;
            this.authenticatorCharacters = authenticatorCharacters;
        }

        public char tag() {
            return tag;
        }

        public String description() {
            return description;
        }

        /** The cipher block length, which every padded length here is a multiple of. */
        public int blockBytes() {
            return blockBytes;
        }

        /** Clause 8.7 — 4 bytes for 3DES, 8 for AES, each written as two hex characters. */
        public int authenticatorCharacters() {
            return authenticatorCharacters;
        }

        public static VersionId of(char tag) {
            for (VersionId version : values()) {
                if (version.tag == tag) {
                    return version;
                }
            }
            throw new IllegalArgumentException("Version ID '" + tag + "' is not a Thales Key Block. "
                    + "Clause 8.5.1 allows '0' for a 3DES Key Block LMK and '1' for an AES one; "
                    + "'A', 'B', 'C' and 'D' are ANSI X9.143 / TR-31 version IDs, which TR31 reads");
        }
    }

    /**
     * One row of the key usage table, clause 8.5.1.2.
     *
     * @param ansiEquivalent the X9.143 usage this converts to on export, or
     *                       {@code null} when the manual leaves it blank, which
     *                       means the usage has no X9.143 representation at all
     */
    public record KeyUsage(String code, String ansiEquivalent, List<UsageRule> rules, String description) {

        public Set<Character> algorithms() {
            Set<Character> all = new LinkedHashSet<>();
            rules.forEach(rule -> all.add(rule.algorithm()));
            return all;
        }

        /** The modes the manual permits for one algorithm under this usage. */
        public Set<Character> modesFor(char algorithm) {
            for (UsageRule rule : rules) {
                if (rule.algorithm() == algorithm) {
                    return rule.modes();
                }
            }
            return Set.of();
        }
    }

    /** Clause 8.5.1.2 — the modes permitted for one algorithm under one usage. */
    public record UsageRule(char algorithm, Set<Character> modes) {
    }

    /** Clause 8.5.1.3 — byte 7. */
    private static final Map<Character, String> ALGORITHMS = new LinkedHashMap<>();
    /** Clause 8.5.1.4 — byte 8. */
    private static final Map<Character, String> MODES_OF_USE = new LinkedHashMap<>();
    /** Clause 8.5.1.6 — byte 11. */
    private static final Map<Character, String> EXPORTABILITY = new LinkedHashMap<>();
    /** Clause 8.5.2.1 — the optional header block identifiers. */
    private static final Map<String, String> OPTIONAL_BLOCKS = new LinkedHashMap<>();
    /** Clause 8.5.2.1 — the key status values carried by optional block {@code 00}. */
    private static final Map<Character, String> KEY_STATUS = new LinkedHashMap<>();
    private static final Map<String, KeyUsage> KEY_USAGES = new LinkedHashMap<>();

    static {
        ALGORITHMS.put('A', "AES");
        ALGORITHMS.put('D', "DES");
        ALGORITHMS.put('E', "Elliptic curve");
        ALGORITHMS.put('H', "HMAC");
        ALGORITHMS.put('R', "RSA");
        ALGORITHMS.put('S', "DSA (reserved for future use)");
        ALGORITHMS.put('T', "3-DES");

        MODES_OF_USE.put('B', "both encrypt and decrypt");
        MODES_OF_USE.put('C', "MAC calculate, both generate and verify");
        MODES_OF_USE.put('D', "decrypt only");
        MODES_OF_USE.put('E', "encrypt only");
        MODES_OF_USE.put('G', "MAC generate only");
        MODES_OF_USE.put('N', "no special restrictions");
        MODES_OF_USE.put('S', "signature generation only");
        MODES_OF_USE.put('V', "verify only");
        MODES_OF_USE.put('X', "key derivation only");

        EXPORTABILITY.put('E', "exportable in a trusted key block, if the wrapping key is itself trusted");
        EXPORTABILITY.put('N', "no export permitted");
        EXPORTABILITY.put('S', "sensitive; other export is permitted where it has been enabled");

        OPTIONAL_BLOCKS.put("KS", "Key Set Identifier (ANSI X9.24-3)");
        OPTIONAL_BLOCKS.put("KV", "Key Block version (ANSI X9.143)");
        OPTIONAL_BLOCKS.put("PB", "Padding block (ANSI X9.143); must be last");
        OPTIONAL_BLOCKS.put("00", "Key Status");
        OPTIONAL_BLOCKS.put("01", "Key Block Encryption method");
        OPTIONAL_BLOCKS.put("02", "Key Block Authentication method");
        OPTIONAL_BLOCKS.put("03", "Start Date/Time (YYYY:MM:DD:HH)");
        OPTIONAL_BLOCKS.put("04", "End Date/Time (YYYY:MM:DD:HH)");
        OPTIONAL_BLOCKS.put("05", "Text");

        KEY_STATUS.put('E', "Expired");
        KEY_STATUS.put('L', "Live");
        KEY_STATUS.put('P', "Pending");
        KEY_STATUS.put('R', "Revoked");
        KEY_STATUS.put('T', "Test");

        // Clause 8.5.1.2, in the manual's order. A null ANSI equivalent is a
        // blank cell: the usage cannot be represented in X9.143 at all.
        usage("01", null, "WatchWord Key (WWK)", rule("DT", "CGNV"));
        // The manual prints "RSA Public Key" on both rows of usage 02, but the
        // second row's algorithm is 'E', elliptic curve. Reproduced as printed.
        usage("02", null, "RSA Public Key", rule("R", "ENV"), rule("E", "NVX"));
        usage("03", null, "RSA Private Key (signing / key management), ECC Private Key",
                rule("R", "DNS"), rule("E", "NSX"));
        usage("04", null, "RSA Private Key (for ICCs)", rule("R", "DNS"));
        usage("05", null, "RSA Private Key (for PIN translation)", rule("R", "DNS"));
        usage("06", null, "RSA Private Key (for TLS pre-master secret decryption)", rule("R", "DNS"));

        usage("B0", "B0", "Base Derivation Key (BDK-1)", rule("AT", "NX"));
        usage("41", "B0", "Base Derivation Key (BDK-2)", rule("AT", "NX"));
        usage("42", "B0", "Base Derivation Key (BDK-3)", rule("T", "NX"));
        usage("43", "B0", "Base Derivation Key (BDK-4)", rule("AT", "NX"));
        usage("44", "B0", "Base Derivation Key (BDK-5)", rule("T", "NX"));
        usage("B1", "B1", "DUKPT Initial Key (IKEY)", rule("AT", "NX"));

        usage("C0", "C0", "Card Verification Key", rule("AT", "CGNV"));
        usage("11", "C0", "Card Verification Key (American Express CSC)", rule("T", "CGNV"));
        usage("12", "C0", "Card Verification Key (Mastercard CVC)", rule("T", "CGNV"));
        usage("13", "C0", "Card Verification Key (Visa CVV)", rule("AT", "CGNV"));

        usage("D0", "D0", "Data Encryption Key (generic)", rule("ADT", "BDEN"));
        usage("21", "D0", "Data Encryption Key (DEK)", rule("ADT", "BDEN"));
        usage("22", "D0", "Data Encryption Key (ZEK)", rule("ADT", "BDEN"));
        usage("23", "D0", "Data Encryption Key (TEK)", rule("ADT", "BDEN"));
        usage("24", null, "Key Encryption Key (Transport Key)", rule("A", "BDEN"));
        usage("25", "D0", "CTR Data Encryption Key (CTRDEK)", rule("A", "BDEN"));

        usage("E0", "E0", "EMV Master Key: Application Cryptogram (MK-AC)", rule("AT", "NX"));
        usage("E1", "E1", "EMV Master Key: Secure Messaging for Confidentiality (MK-SMC)", rule("AT", "NX"));
        usage("E2", "E2", "EMV Master Key: Secure Messaging for Integrity (MK-SMI)", rule("AT", "NX"));
        usage("E3", "E3", "EMV Master Key: Data Authentication Code (MK-DAC)", rule("AT", "NX"));
        usage("E4", "E4", "EMV Master Key: Dynamic Numbers (MK-DN)", rule("T", "NX"));
        usage("E5", "E5", "EMV Master Key: Card Personalization", rule("T", "NX"));
        usage("E6", "E6", "EMV Master Key: other", rule("AT", "NX"));
        usage("E7", null, "EMV Master Personalization Key", rule("AT", "NX"));
        usage("32", "E6", "Dynamic CVV Master Key (MK-CVC3)", rule("AT", "NX"));
        usage("33", null, "Mobile Remote Management Master Key, confidentiality (M_KEY_CONF)", rule("A", "NX"));
        usage("34", null, "Mobile Remote Management Master Key, integrity (M_KEY_MAC)", rule("A", "NX"));
        usage("35", null, "Mobile Remote Management Session Key, confidentiality (MS_KEY_CONF)",
                rule("A", "BDEN"));
        usage("36", "M3", "Mobile Remote Management Session Key, integrity (MS_KEY_MAC)", rule("A", "CN"));
        usage("37", null, "EMV Card Key for cryptograms", rule("AT", "NX"));
        usage("38", null, "EMV Card Key for integrity", rule("AT", "NX"));
        usage("39", null, "EMV Card Key for encryption", rule("AT", "NX"));
        usage("40", null, "EMV Personalization System Key", rule("T", "N"));
        usage("47", null, "EMV Session Key for cryptograms", rule("AT", "BN"));
        usage("48", null, "EMV Session Key for integrity", rule("AT", "GVN"));
        usage("49", null, "EMV Session Key for encryption", rule("T", "BE"));

        usage("K0", "K0", "Key Encryption / Wrapping Key (generic)", rule("ADT", "BDEN"));
        usage("K1", "K1", "Key Block Protection Key", rule("AT", "BDEN"));
        usage("51", "K0", "Terminal Key Encryption (TMK)", rule("ADT", "BDEN"));
        usage("52", "K0", "Zone Key Encryption (ZMK)", rule("ADT", "BDEN"));
        usage("53", "11", "ZKA Master Key (German transactions)", rule("AT", "X"));
        usage("54", null, "Key Encryption Key (KEK)", rule("AT", "BDEN"));
        usage("55", null, "Key Encryption Key (Transport Key)", rule("A", "E"));
        usage("56", null, "MPKC (Italian transactions)", rule("DT", "NX"));
        usage("57", null, "MKPOS / MKSER (Italian transactions)", rule("DT", "NX"));

        usage("M0", "M0", "ISO 16609 MAC algorithm 1", rule("T", "CGNV"));
        usage("M1", "M1", "ISO 9797-1 MAC algorithm 1", rule("DT", "CGNV"));
        usage("M2", "M2", "ISO 9797-1 MAC algorithm 2", rule("DT", "CGNV"));
        usage("M3", "M3", "ISO 9797-1 MAC algorithm 3", rule("T", "CGNV"));
        usage("M4", "M4", "ISO 9797-1 MAC algorithm 4", rule("T", "CGNV"));
        usage("M5", "M5", "ISO 9797-1:1999 MAC algorithm 5", rule("AT", "CGNV"));
        usage("M6", "M6", "ISO 9797-1:2011 MAC algorithm 5 / CMAC", rule("A", "CGNV"));

        usage("61", null, "HMAC key (SHA-1)", rule("H", "CGNV"));
        usage("62", null, "HMAC key (SHA-224)", rule("H", "CGNV"));
        usage("63", null, "HMAC key (SHA-256)", rule("H", "CGNV"));
        usage("64", null, "HMAC key (SHA-384)", rule("H", "CGNV"));
        usage("65", null, "HMAC key (SHA-512)", rule("H", "CGNV"));

        usage("P0", "P0", "PIN Encryption Key (generic)", rule("ADT", "BDEN"));
        usage("71", "P0", "Terminal PIN Encryption Key (TPK)", rule("ADT", "BDEN"));
        usage("72", "P0", "Zone PIN Encryption Key (ZPK)", rule("ADT", "BDEN"));
        usage("73", "P0", "Transaction Key Scheme Terminal Key Register (TKR)", rule("DT", "N"));

        usage("V0", "V0", "PIN Verification Key (generic)", rule("DT", "CGNV"));
        usage("V1", "V1", "PIN Verification Key (IBM 3624)", rule("DT", "CGNV"));
        usage("V2", "V2", "PIN Verification Key (Visa PVV)", rule("ADT", "CGNV"));
    }

    private static UsageRule[] rule(String algorithms, String modes) {
        Set<Character> modeSet = new LinkedHashSet<>();
        for (char mode : modes.toCharArray()) {
            modeSet.add(mode);
        }
        UsageRule[] rules = new UsageRule[algorithms.length()];
        for (int i = 0; i < algorithms.length(); i++) {
            rules[i] = new UsageRule(algorithms.charAt(i), Set.copyOf(modeSet));
        }
        return rules;
    }

    private static void usage(String code, String ansi, String description, UsageRule[]... ruleGroups) {
        List<UsageRule> rules = new ArrayList<>();
        for (UsageRule[] group : ruleGroups) {
            rules.addAll(List.of(group));
        }
        KEY_USAGES.put(code, new KeyUsage(code, ansi, List.copyOf(rules), description));
    }

    public static Map<String, KeyUsage> keyUsages() {
        return Map.copyOf(KEY_USAGES);
    }

    public static KeyUsage keyUsage(String code) {
        return KEY_USAGES.get(code == null ? "" : code.toUpperCase(Locale.ROOT));
    }

    // =====================================================================
    // Structure
    // =====================================================================

    /** One observation, carrying the clause it comes from. */
    public record Finding(String severity, String clause, String message) {
    }

    /** Clause 8.5.1 — the sixteen clear characters that open every Thales Key Block. */
    public record Header(char versionId, int declaredLength, String keyUsage, char algorithm,
                         char modeOfUse, String keyVersionNumber, char exportability,
                         int optionalBlockCount, String lmkId, String raw) {

        /** Clause 8.5.1.5 — byte 9 of {@code 'c'} means this is a key component. */
        public boolean isComponent() {
            return keyVersionNumber.length() == 2 && keyVersionNumber.charAt(0) == 'c';
        }

        /** The component number, when this header carries one. */
        public String componentNumber() {
            return isComponent() ? keyVersionNumber.substring(1) : null;
        }
    }

    /** Clause 8.5.2 — an optional header block, whose length field is hexadecimal. */
    public record OptionalBlock(String id, int declaredLength, String data) {
    }

    /** A parsed block. The key data and authenticator are left as characters:
     *  nothing here decrypts them, for the reason the class comment gives. */
    public record KeyBlock(Header header, List<OptionalBlock> optionalBlocks,
                           String encryptedKeyData, String authenticator,
                           String raw, List<Finding> findings) {

        public boolean wellFormed() {
            return findings.stream().noneMatch(f -> ERROR.equals(f.severity()));
        }

        public VersionId version() {
            return VersionId.of(header.versionId());
        }
    }

    // =====================================================================
    // Parsing
    // =====================================================================

    /**
     * Reads a Thales Key Block and checks it against chapter 8.
     *
     * <p>The leading {@code 'S'} is accepted and stripped. Clause 8.5.1.1 counts
     * the length from the header onwards, and clause 8.7 excludes the tag from
     * the authenticated data, so the tag is not part of the block proper.</p>
     */
    public static KeyBlock parse(String input) {
        // Line breaks come from pasting a wrapped block and are not data.
        // Spaces are: clause 8.5.2.1 lets a text block hold "any combination of
        // printable characters", and a space is one, so stripping all
        // whitespace silently shortens the block and shifts every field after it.
        String text = input == null ? "" : input.replaceAll("[\\r\\n\\t]", "").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("There is no key block to read");
        }
        if (text.charAt(0) == SCHEME_TAG || text.charAt(0) == Character.toLowerCase(SCHEME_TAG)) {
            text = text.substring(1);
        }
        if (text.length() < HEADER_LENGTH) {
            throw new IllegalArgumentException("A Thales Key Block header is " + HEADER_LENGTH
                    + " characters (clause 8.5.1); this is " + text.length());
        }

        List<Finding> findings = new ArrayList<>();
        Header header = readHeader(text, findings);
        VersionId version = versionOrDefault(header.versionId(), findings);

        int at = HEADER_LENGTH;
        List<OptionalBlock> optionalBlocks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int optionalCharacters = 0;
        for (int index = 0; index < header.optionalBlockCount(); index++) {
            if (at + MINIMUM_OPTIONAL_BLOCK_LENGTH > text.length()) {
                findings.add(new Finding(ERROR, "Book 8.5.2", "The header declares "
                        + header.optionalBlockCount() + " optional blocks but the data runs out after "
                        + index));
                break;
            }
            String id = text.substring(at, at + 2);
            String lengthField = text.substring(at + 2, at + 4);
            int length;
            try {
                length = Integer.parseInt(lengthField, 16);
            } catch (NumberFormatException e) {
                findings.add(new Finding(ERROR, "Clause 8.5.2", "Optional block '" + id
                        + "' has length field '" + lengthField
                        + "', which is not hexadecimal. Clause 8.5.2 writes this length in hexadecimal, "
                        + "not decimal: a 24-byte block reads '18'"));
                break;
            }
            if (length < MINIMUM_OPTIONAL_BLOCK_LENGTH || at + length > text.length()) {
                findings.add(new Finding(ERROR, "Clause 8.5.2", "Optional block '" + id + "' declares "
                        + length + " bytes, which does not fit; the minimum is "
                        + MINIMUM_OPTIONAL_BLOCK_LENGTH));
                break;
            }
            String data = text.substring(at + 4, at + length);
            optionalBlocks.add(new OptionalBlock(id, length, data));
            checkOptionalBlock(id, data, index, header.optionalBlockCount(), findings);
            if (!seen.add(id)) {
                findings.add(new Finding(ERROR, "Clause 8.5.2", "Optional block '" + id
                        + "' appears more than once. An HSM answers 'BC — Repeated optional block' "
                        + "to any attempt to generate, import or export this"));
            }
            at += length;
            optionalCharacters += length;
        }

        if (optionalCharacters > 0 && optionalCharacters % version.blockBytes() != 0) {
            findings.add(new Finding(ERROR, "Clause 8.5.2", "The optional blocks total "
                    + optionalCharacters + " characters, which is not a multiple of "
                    + version.blockBytes() + ". A 'PB' padding block, last, is what makes it one"));
        }

        int authenticatorLength = version.authenticatorCharacters();
        String encryptedKeyData = "";
        String authenticator = "";
        int remaining = text.length() - at;
        if (remaining < authenticatorLength) {
            findings.add(new Finding(ERROR, "Clause 8.7", "There is no room for the authenticator: "
                    + version.description() + " needs " + authenticatorLength
                    + " characters and only " + Math.max(0, remaining) + " are left"));
        } else {
            encryptedKeyData = text.substring(at, text.length() - authenticatorLength);
            authenticator = text.substring(text.length() - authenticatorLength);
            checkKeyData(encryptedKeyData, version, findings);
        }

        if (header.declaredLength() != text.length()) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.1", "The header declares a length of "
                    + header.declaredLength() + " but the block is " + text.length()
                    + " characters. The length covers the header, the optional blocks, the key data "
                    + "and the authenticator, and excludes the 'S' tag"));
        }

        return new KeyBlock(header, List.copyOf(optionalBlocks), encryptedKeyData, authenticator,
                text, List.copyOf(findings));
    }

    private static Header readHeader(String text, List<Finding> findings) {
        char versionId = text.charAt(0);
        String lengthField = text.substring(1, 5);
        String keyUsage = text.substring(5, 7);
        char algorithm = text.charAt(7);
        char modeOfUse = text.charAt(8);
        String keyVersionNumber = text.substring(9, 11);
        char exportability = text.charAt(11);
        String optionalCountField = text.substring(12, 14);
        String lmkId = text.substring(14, 16);

        int declaredLength = numeric(lengthField, "Clause 8.5.1.1",
                "the key block length (bytes 1-4)", findings);
        int optionalCount = numeric(optionalCountField, "Clause 8.5.1.7",
                "the number of optional blocks (bytes 12-13)", findings);

        checkVersion(versionId, findings);
        checkUsageAlgorithmAndMode(keyUsage, algorithm, modeOfUse, findings);
        checkKeyVersionNumber(keyVersionNumber, findings);
        checkExportability(exportability, findings);
        checkLmkId(lmkId, findings);

        return new Header(versionId, declaredLength, keyUsage, algorithm, modeOfUse,
                keyVersionNumber, exportability, Math.max(0, optionalCount), lmkId,
                text.substring(0, HEADER_LENGTH));
    }

    private static int numeric(String field, String clause, String what, List<Finding> findings) {
        if (!field.chars().allMatch(c -> c >= '0' && c <= '9')) {
            findings.add(new Finding(ERROR, clause, "The value '" + field + "' in " + what
                    + " is not numeric"));
            return -1;
        }
        return Integer.parseInt(field);
    }

    private static VersionId versionOrDefault(char versionId, List<Finding> findings) {
        try {
            return VersionId.of(versionId);
        } catch (IllegalArgumentException unknown) {
            findings.add(new Finding(INFO, "Clause 8.5.1",
                    "Read as a 3DES Key Block so the rest of the block can still be shown"));
            return VersionId.DES;
        }
    }

    // =====================================================================
    // Checks
    // =====================================================================

    private static void checkVersion(char versionId, List<Finding> findings) {
        if (versionId == '0' || versionId == '1') {
            findings.add(new Finding(INFO, "Clause 8.5.1", "Version ID '" + versionId + "': "
                    + VersionId.of(versionId).description()));
            return;
        }
        String extra = "ABCD".indexOf(versionId) >= 0
                ? " That is an ANSI X9.143 / TR-31 version ID, not a Thales one; TR31 reads those."
                : "";
        findings.add(new Finding(ERROR, "Clause 8.5.1", "Version ID '" + versionId
                + "' is not '0' or '1'." + extra));
    }

    private static void checkUsageAlgorithmAndMode(String code, char algorithm, char modeOfUse,
                                                   List<Finding> findings) {
        if (!ALGORITHMS.containsKey(algorithm)) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.3", "Algorithm '" + algorithm
                    + "' is not one of " + ALGORITHMS.keySet()));
        }
        if (!MODES_OF_USE.containsKey(modeOfUse)) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.4", "Mode of use '" + modeOfUse
                    + "' is not one of " + MODES_OF_USE.keySet()));
        }

        KeyUsage usage = KEY_USAGES.get(code);
        if (usage == null) {
            findings.add(new Finding(WARNING, "Clause 8.5.1.2", "Key usage '" + code
                    + "' is not in the Thales key usage table. It may be a newer code, or a block "
                    + "from somewhere else"));
            return;
        }
        findings.add(new Finding(INFO, "Clause 8.5.1.2", "Key usage '" + code + "': "
                + usage.description()));
        if (usage.ansiEquivalent() == null) {
            findings.add(new Finding(INFO, "Clause 8.5.1.2", "This usage has no ANSI X9.143 "
                    + "equivalent, so the block cannot be converted to an X9.143 one without "
                    + "losing what the key is for"));
        } else if (!usage.ansiEquivalent().equals(code)) {
            findings.add(new Finding(INFO, "Clause 8.5.1.2", "Exporting to ANSI X9.143 converts "
                    + "this usage to '" + usage.ansiEquivalent() + "'"));
        }

        if (!ALGORITHMS.containsKey(algorithm)) {
            return;
        }
        if (!usage.algorithms().contains(algorithm)) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.2", "Key usage '" + code + "' ("
                    + usage.description() + ") does not permit algorithm '" + algorithm
                    + "'; the table allows " + usage.algorithms()));
            return;
        }
        Set<Character> permitted = usage.modesFor(algorithm);
        if (!permitted.contains(modeOfUse) && MODES_OF_USE.containsKey(modeOfUse)) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.2", "Key usage '" + code
                    + "' with algorithm '" + algorithm + "' does not permit mode of use '"
                    + modeOfUse + "'; the table allows " + permitted));
        }
    }

    private static void checkKeyVersionNumber(String value, List<Finding> findings) {
        if ("00".equals(value)) {
            findings.add(new Finding(INFO, "Clause 8.5.1.5", "Key versioning is not used for this key"));
            return;
        }
        if (value.charAt(0) == 'c') {
            findings.add(new Finding(INFO, "Clause 8.5.1.5", "This is key component number '"
                    + value.charAt(1) + "'. The header does not say how many components there are"));
            return;
        }
        if (!value.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.5", "The key version number '" + value
                    + "' is not printable ASCII"));
            return;
        }
        findings.add(new Finding(INFO, "Clause 8.5.1.5", "Key version '" + value + "'"));
    }

    private static void checkExportability(char value, List<Finding> findings) {
        String meaning = EXPORTABILITY.get(value);
        if (meaning == null) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.6", "Exportability '" + value
                    + "' is not one of " + EXPORTABILITY.keySet()));
            return;
        }
        findings.add(new Finding(INFO, "Clause 8.5.1.6", "Exportability '" + value + "': " + meaning));
    }

    /**
     * Clause 8.5.1.8 — numeric {@code '00'} to {@code '19'}, or {@code 'FF'}
     * when exporting, because the recipient's LMK may sit in another slot.
     *
     * <p>The manual's own example header in clause 8.5.1.8 uses {@code '33'},
     * which its own field definition on the same page does not allow. Reported
     * as a warning rather than an error for exactly that reason.</p>
     */
    private static void checkLmkId(String value, List<Finding> findings) {
        if ("FF".equals(value)) {
            findings.add(new Finding(INFO, "Clause 8.5.1.8", "LMK ID 'FF': this block is on its way "
                    + "out, so it names no slot"));
            return;
        }
        if (!value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            findings.add(new Finding(ERROR, "Clause 8.5.1.8", "LMK ID '" + value
                    + "' is neither numeric nor 'FF'"));
            return;
        }
        int id = Integer.parseInt(value);
        if (id > 19) {
            findings.add(new Finding(WARNING, "Clause 8.5.1.8", "LMK ID '" + value
                    + "' is outside the range '00' to '19' that clause 8.5.1.8 defines. "
                    + "The manual's own example on that page uses '33', so blocks like this exist"));
            return;
        }
        findings.add(new Finding(INFO, "Clause 8.5.1.8", "LMK ID '" + value + "'"));
    }

    private static void checkOptionalBlock(String id, String data, int index, int total,
                                           List<Finding> findings) {
        String known = OPTIONAL_BLOCKS.get(id);
        if (known == null) {
            findings.add(new Finding(WARNING, "Clause 8.5.2.1", "Optional block '" + id
                    + "' is not one the manual defines"));
            return;
        }
        findings.add(new Finding(INFO, "Clause 8.5.2.1", "Optional block '" + id + "': " + known));

        switch (id) {
            case "PB" -> {
                if (index != total - 1) {
                    findings.add(new Finding(ERROR, "Clause 8.5.2.1", "The 'PB' padding block must be "
                            + "the last optional block, and this one is block " + (index + 1) + " of "
                            + total));
                }
            }
            case "00" -> {
                if (data.length() != 1 || !KEY_STATUS.containsKey(data.charAt(0))) {
                    findings.add(new Finding(ERROR, "Clause 8.5.2.1", "Key status '" + data
                            + "' is not one of " + KEY_STATUS.keySet()));
                } else {
                    findings.add(new Finding(INFO, "Clause 8.5.2.1", "Key status: "
                            + KEY_STATUS.get(data.charAt(0))));
                }
            }
            case "01", "02" -> {
                if (!"00".equals(data)) {
                    findings.add(new Finding(ERROR, "Clause 8.5.2.1", "Optional block '" + id
                            + "' permits only '00', the current mechanism, and carries '" + data + "'"));
                }
            }
            case "03", "04" -> {
                if (!data.matches("\\d{4}:\\d{2}:\\d{2}:\\d{2}")) {
                    findings.add(new Finding(ERROR, "Clause 8.5.2.1", "Optional block '" + id
                            + "' must be YYYY:MM:DD:HH and carries '" + data + "'"));
                }
            }
            case "05" -> {
                if (data.isEmpty()) {
                    findings.add(new Finding(ERROR, "Clause 8.5.2.1",
                            "A text block with no data is not permitted"));
                }
            }
            case "KV" -> {
                if (data.length() != 4) {
                    findings.add(new Finding(ERROR, "Clause 8.5.2.1",
                            "'KV' must contain 4 printable ASCII characters, and carries " + data.length()));
                }
            }
            default -> {
                // KS has no length rule in clause 8.5.2.1.
            }
        }
    }

    private static void checkKeyData(String encryptedKeyData, VersionId version, List<Finding> findings) {
        if (encryptedKeyData.isEmpty()) {
            findings.add(new Finding(ERROR, "Clause 8.6", "There is no key data in this block"));
            return;
        }
        // The key data is hex-encoded ciphertext, so two characters to the byte.
        if (encryptedKeyData.length() % 2 != 0) {
            findings.add(new Finding(ERROR, "Clause 8.6", "The key data is "
                    + encryptedKeyData.length() + " characters, which is not a whole number of bytes"));
            return;
        }
        int bytes = encryptedKeyData.length() / 2;
        if (bytes % version.blockBytes() != 0) {
            findings.add(new Finding(ERROR, "Clause 8.6", "The key data is " + bytes
                    + " bytes, which is not a multiple of the " + version.blockBytes()
                    + "-byte cipher block. Clause 8.6 pads it with random bytes until it is"));
        }
    }

    // =====================================================================
    // Building
    // =====================================================================

    /**
     * Builds a header, computing the length field from the parts that will
     * follow it, and refusing combinations the key usage table forbids.
     *
     * @param payloadCharacters the optional blocks, key data and authenticator
     *                          that will follow, counted in characters
     */
    public static String buildHeader(VersionId version, String keyUsage, char algorithm, char modeOfUse,
                                     String keyVersionNumber, char exportability,
                                     int optionalBlockCount, String lmkId, int payloadCharacters) {
        KeyUsage usage = KEY_USAGES.get(keyUsage == null ? "" : keyUsage.toUpperCase(Locale.ROOT));
        if (usage == null) {
            throw new IllegalArgumentException("Key usage '" + keyUsage
                    + "' is not in the table of clause 8.5.1.2");
        }
        if (!usage.algorithms().contains(algorithm)) {
            throw new IllegalArgumentException("Key usage '" + keyUsage + "' (" + usage.description()
                    + ") permits algorithms " + usage.algorithms() + ", not '" + algorithm + "'");
        }
        if (!usage.modesFor(algorithm).contains(modeOfUse)) {
            throw new IllegalArgumentException("Key usage '" + keyUsage + "' with algorithm '"
                    + algorithm + "' permits modes " + usage.modesFor(algorithm) + ", not '"
                    + modeOfUse + "'");
        }
        if (keyVersionNumber == null || keyVersionNumber.length() != 2) {
            throw new IllegalArgumentException("The key version number is two characters (clause 8.5.1.5)");
        }
        if (!EXPORTABILITY.containsKey(exportability)) {
            throw new IllegalArgumentException("Exportability is one of " + EXPORTABILITY.keySet());
        }
        if (optionalBlockCount < 0 || optionalBlockCount > 99) {
            throw new IllegalArgumentException("A key block carries at most 99 optional blocks "
                    + "(clause 8.5.1.7)");
        }
        if (lmkId == null || lmkId.length() != 2) {
            throw new IllegalArgumentException("The LMK identifier is two characters (clause 8.5.1.8)");
        }

        int total = HEADER_LENGTH + payloadCharacters;
        if (total > 9999) {
            throw new IllegalArgumentException("The length field holds four digits, and this block "
                    + "would be " + total + " characters");
        }
        return String.valueOf(version.tag())
                + String.format("%04d", total)
                + keyUsage.toUpperCase(Locale.ROOT)
                + algorithm
                + modeOfUse
                + keyVersionNumber
                + exportability
                + String.format("%02d", optionalBlockCount)
                + lmkId;
    }

    /** Clause 8.5.2 — assembles one optional block, with its hexadecimal length. */
    public static String buildOptionalBlock(String id, String data) {
        if (id == null || id.length() != 2) {
            throw new IllegalArgumentException("An optional block identifier is two characters");
        }
        String value = data == null ? "" : data;
        int length = MINIMUM_OPTIONAL_BLOCK_LENGTH + value.length();
        if (length > 255) {
            throw new IllegalArgumentException("An optional block is at most 255 bytes, and this "
                    + "would be " + length);
        }
        return id.toUpperCase(Locale.ROOT) + String.format("%02X", length) + value;
    }

    // =====================================================================
    // Reporting
    // =====================================================================

    public static String describe(KeyBlock block) {
        Header header = block.header();
        StringBuilder report = new StringBuilder();
        report.append("Thales Key Block (key scheme 'S')\n");
        report.append("  header            : ").append(header.raw()).append('\n');
        report.append("  version ID        : ").append(header.versionId());
        if (header.versionId() == '0' || header.versionId() == '1') {
            report.append(" — ").append(VersionId.of(header.versionId()).description());
        }
        report.append('\n');
        report.append("  declared length   : ").append(header.declaredLength())
                .append(" (actual ").append(block.raw().length()).append(")\n");

        KeyUsage usage = KEY_USAGES.get(header.keyUsage());
        report.append("  key usage         : ").append(header.keyUsage());
        if (usage != null) {
            report.append(" — ").append(usage.description());
        }
        report.append('\n');
        report.append("  algorithm         : ").append(header.algorithm()).append(" — ")
                .append(ALGORITHMS.getOrDefault(header.algorithm(), "unknown")).append('\n');
        report.append("  mode of use       : ").append(header.modeOfUse()).append(" — ")
                .append(MODES_OF_USE.getOrDefault(header.modeOfUse(), "unknown")).append('\n');
        report.append("  key version       : ").append(header.keyVersionNumber());
        if (header.isComponent()) {
            report.append(" — component ").append(header.componentNumber());
        }
        report.append('\n');
        report.append("  exportability     : ").append(header.exportability()).append(" — ")
                .append(EXPORTABILITY.getOrDefault(header.exportability(), "unknown")).append('\n');
        report.append("  LMK ID            : ").append(header.lmkId()).append('\n');
        report.append("  optional blocks   : ").append(header.optionalBlockCount()).append('\n');
        for (OptionalBlock optional : block.optionalBlocks()) {
            report.append("    ").append(optional.id()).append(" (").append(optional.declaredLength())
                    .append(" bytes): ").append(optional.data()).append('\n');
        }
        report.append("  key data          : ").append(block.encryptedKeyData().length() / 2)
                .append(" bytes, encrypted\n");
        report.append("  authenticator     : ").append(block.authenticator()).append('\n');
        report.append("  verdict           : ").append(block.wellFormed() ? "WELL FORMED" : "MALFORMED")
                .append('\n');

        if (!block.findings().isEmpty()) {
            report.append("\nFindings\n");
            for (Finding finding : block.findings()) {
                report.append("  [").append(finding.severity()).append(']');
                if (finding.clause() != null) {
                    report.append(' ').append(finding.clause()).append(':');
                }
                report.append(' ').append(finding.message()).append('\n');
            }
        }

        report.append("\nThis reads the clear parts only. Supply the Key Block LMK to unwrap the key\n");
        report.append("data and check the authenticator.\n");
        return report.toString();
    }

    // =====================================================================
    // The cryptography — clauses 8.6 and 8.7
    // =====================================================================

    /** Clause 8.6 — what a key block gives back once the LMK opens it. */
    public record Unwrapped(KeyBlock block, String clearKey, int keyBits, String padding,
                            boolean authentic, String expectedAuthenticator) {
    }

    /** {@code KBPK XOR 45..45}: the 3DES encryption variant, 'E'. */
    public static String encryptionKey(String kbpk) {
        return variantOf(kbpk, (byte) 0x45);
    }

    /** {@code KBPK XOR 4D..4D}: the 3DES authentication variant, 'M'. */
    public static String authenticationKey(String kbpk) {
        return variantOf(kbpk, (byte) 0x4D);
    }

    /**
     * The AES scheme's encryption key. Version {@code '1'} does not vary its
     * LMK, it <b>derives</b> from it, by the CMAC construction of ANSI X9.143
     * version D. See {@link #deriveAes}.
     */
    public static String aesEncryptionKey(String kbpk) {
        return deriveAes(kbpk, 0x0000);
    }

    /** The AES scheme's authentication key, the same derivation with usage {@code 0001}. */
    public static String aesAuthenticationKey(String kbpk) {
        return deriveAes(kbpk, 0x0001);
    }

    /**
     * ANSI X9.143 version D key derivation: AES-CMAC over eight bytes of
     * derivation data, repeated with an incrementing counter until the output
     * is as long as the KBPK.
     *
     * <pre>
     *   counter | key usage (2) | separator 00 | algorithm (2) | length in bits (2)
     * </pre>
     *
     * <p>The trap is the width. That is <b>eight</b> bytes fed to a cipher
     * whose block is sixteen, so CMAC's own {@code 80 00..} padding applies and
     * the second subkey is used. Zero-padding it to a full block instead — the
     * obvious thing to do, and what this bench did first — produces a
     * plausible-looking key that is wrong, and nothing downstream complains
     * until an HSM rejects the block.</p>
     */
    private static String deriveAes(String kbpk, int usage) {
        byte[] key = aesKeyBlockLmk(kbpk);
        int bits = key.length * 8;
        int algorithm = switch (key.length) {
            case 16 -> 0x02;
            case 24 -> 0x03;
            default -> 0x04;
        };
        byte[] out = new byte[key.length];
        int blocks = (key.length + 15) / 16;
        for (int counter = 1; counter <= blocks; counter++) {
            byte[] data = {
                (byte) counter,
                (byte) (usage >> 8), (byte) usage,
                0x00,
                (byte) (algorithm >> 8), (byte) algorithm,
                (byte) (bits >> 8), (byte) bits,
            };
            byte[] block = cmac(key, data);
            int at = (counter - 1) * 16;
            System.arraycopy(block, 0, out, at, Math.min(16, out.length - at));
        }
        return hex(out);
    }

    private static byte[] aesKeyBlockLmk(String kbpk) {
        byte[] key = bytes(normalizeHex(kbpk, "AES Key Block LMK"));
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("An AES Key Block LMK is 16, 24 or 32 bytes, not "
                    + key.length);
        }
        return key;
    }

    private static String variantOf(String kbpk, byte variant) {
        byte[] key = keyBlockLmk(kbpk);
        byte[] out = new byte[key.length];
        for (int i = 0; i < key.length; i++) {
            out[i] = (byte) (key[i] ^ variant);
        }
        return hex(out);
    }

    private static byte[] keyBlockLmk(String kbpk) {
        byte[] key = bytes(normalizeHex(kbpk, "Key Block LMK"));
        if (key.length != 16 && key.length != 24) {
            throw new IllegalArgumentException("A 3DES Key Block LMK is a double- or triple-length "
                    + "TDES key (16 or 24 bytes), not " + key.length);
        }
        return key;
    }

    /**
     * Wraps a key under a 3DES Key Block LMK.
     *
     * @param padding the random padding of clause 8.6, supplied so a test can
     *                be reproducible; {@code null} draws it from a secure
     *                random, which is what a real personalisation does
     */
    public static String wrap(String kbpk, String header, String clearKey, String padding) {
        String head = normalizeAscii(header);
        if (head.length() != HEADER_LENGTH) {
            throw new IllegalArgumentException("The header is " + HEADER_LENGTH + " characters");
        }
        VersionId version = VersionId.of(head.charAt(0));
        byte[] key = bytes(normalizeHex(clearKey, "clear key"));
        if (key.length == 0) {
            throw new IllegalArgumentException("There is no key to wrap");
        }

        int blockBytes = version.blockBytes();
        int bits = key.length * 8;
        int unpadded = 2 + key.length;
        int padLength = (blockBytes - (unpadded % blockBytes)) % blockBytes;
        byte[] padBytes;
        if (padding == null) {
            padBytes = new byte[padLength];
            new java.security.SecureRandom().nextBytes(padBytes);
        } else {
            padBytes = bytes(normalizeHex(padding, "padding"));
            if (padBytes.length != padLength) {
                throw new IllegalArgumentException("A " + key.length + "-byte key needs exactly "
                        + padLength + " bytes of padding to reach a multiple of " + blockBytes
                        + ", not " + padBytes.length);
            }
        }

        byte[] plain = new byte[2 + key.length + padBytes.length];
        plain[0] = (byte) (bits >> 8);
        plain[1] = (byte) bits;
        System.arraycopy(key, 0, plain, 2, key.length);
        System.arraycopy(padBytes, 0, plain, 2 + key.length, padBytes.length);

        byte[] encrypted = encipher(version, kbpk, head, plain, true);
        String authenticator = authenticator(version, kbpk, head, hex(encrypted));
        return head + hex(encrypted) + authenticator;
    }

    /**
     * Unwraps a key block and checks its authenticator.
     *
     * <p>The authenticator is computed over the header and the <b>encrypted</b>
     * key data. That is not what clause 8.7's wording suggests for 3DES, and it
     * is what the hardware does.</p>
     */
    public static Unwrapped unwrap(String kbpk, String input) {
        KeyBlock block = parse(input);
        VersionId version = block.version();
        String head = block.raw().substring(0, HEADER_LENGTH + optionalCharacters(block));
        byte[] encrypted = bytes(block.encryptedKeyData());
        if (encrypted.length == 0 || encrypted.length % version.blockBytes() != 0) {
            throw new IllegalArgumentException("The key data is " + encrypted.length + " bytes, "
                    + "which a " + version.blockBytes() + "-byte block cipher cannot have produced");
        }

        String expected = authenticator(version, kbpk, head, block.encryptedKeyData());
        boolean authentic = expected.equalsIgnoreCase(block.authenticator());

        byte[] plain = encipher(version, kbpk, head, encrypted, false);

        int bits = ((plain[0] & 0xFF) << 8) | (plain[1] & 0xFF);
        int keyBytes = bits / 8;
        if (bits % 8 != 0 || keyBytes < 1 || 2 + keyBytes > plain.length) {
            // Lead with the authenticator when it failed: the nonsense length is
            // a symptom of the wrong LMK or an altered block, not a finding.
            throw new IllegalArgumentException(authentic
                    ? "The recovered key length is " + bits + " bits, which does not fit the "
                            + plain.length + " bytes of key data, even though the authenticator matched"
                    : "The authenticator does not match, so this is the wrong LMK or the block was "
                            + "altered; what came out decrypts to a key length of " + bits + " bits, "
                            + "which is not a length at all");
        }
        String clearKey = hex(java.util.Arrays.copyOfRange(plain, 2, 2 + keyBytes));
        String padding = hex(java.util.Arrays.copyOfRange(plain, 2 + keyBytes, plain.length));
        return new Unwrapped(block, clearKey, bits, padding, authentic, expected);
    }

    private static int optionalCharacters(KeyBlock block) {
        int total = 0;
        for (OptionalBlock optional : block.optionalBlocks()) {
            total += optional.declaredLength();
        }
        return total;
    }

    /**
     * Clause 8.6 — the key data under the encryption key, in CBC with the
     * header as the initialisation vector.
     *
     * <p>One block of header: the first eight characters for 3DES, all sixteen
     * for AES. That is what binds a block's attributes to its key, and it is
     * the same trick Atalla uses; see {@link AtallaAkbOperations}. Whether a
     * block carrying optional headers extends the IV or still takes only the
     * first block has not been observed, and this takes the first block.</p>
     */
    private static byte[] encipher(VersionId version, String kbpk, String header,
                                   byte[] data, boolean encrypt) {
        byte[] headerBytes = header.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] iv = java.util.Arrays.copyOfRange(headerBytes, 0, version.blockBytes());
        return version == VersionId.DES
                ? cbc(bytes(encryptionKey(kbpk)), iv, data, encrypt)
                : aesCbc(bytes(aesEncryptionKey(kbpk)), iv, data, encrypt);
    }

    /**
     * Clause 8.7 — the authenticator over the header and the <b>encrypted</b>
     * key data: a 3DES CBC-MAC with a zero IV truncated to four bytes, or an
     * AES-CMAC truncated to eight.
     */
    private static String authenticator(VersionId version, String kbpk, String header,
                                        String encryptedKeyData) {
        byte[] data = concat(header.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                bytes(normalizeHex(encryptedKeyData, "encrypted key data")));
        if (version == VersionId.AES) {
            return hex(java.util.Arrays.copyOfRange(cmac(bytes(aesAuthenticationKey(kbpk)), data), 0, 8));
        }
        if (data.length % 8 != 0) {
            throw new IllegalArgumentException("The authenticated data is " + data.length
                    + " bytes; clause 8.7 says no padding is needed because it is always a multiple of 8");
        }
        byte[] chained = cbc(bytes(authenticationKey(kbpk)), new byte[8], data, true);
        return hex(java.util.Arrays.copyOfRange(chained, chained.length - 8, chained.length - 4));
    }

    private static byte[] aesCbc(byte[] key, byte[] iv, byte[] data, boolean encrypt) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(encrypt ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"),
                    new javax.crypto.spec.IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("AES is unavailable", e);
        }
    }

    private static byte[] aesEcb(byte[] key, byte[] block) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"));
            return cipher.doFinal(block);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("AES is unavailable", e);
        }
    }

    /** NIST SP 800-38B, AES-CMAC. */
    private static byte[] cmac(byte[] key, byte[] message) {
        byte[] subkey1 = shiftLeft(aesEcb(key, new byte[16]));
        byte[] subkey2 = shiftLeft(subkey1);

        int blocks = Math.max(1, (message.length + 15) / 16);
        byte[] last = new byte[16];
        if (message.length > 0 && message.length % 16 == 0) {
            System.arraycopy(message, (blocks - 1) * 16, last, 0, 16);
            last = xor(last, subkey1);
        } else {
            int remaining = message.length - (blocks - 1) * 16;
            System.arraycopy(message, (blocks - 1) * 16, last, 0, remaining);
            last[remaining] = (byte) 0x80;
            last = xor(last, subkey2);
        }

        byte[] chained = new byte[16];
        for (int i = 0; i < blocks - 1; i++) {
            chained = aesEcb(key, xor(chained, java.util.Arrays.copyOfRange(message, i * 16, i * 16 + 16)));
        }
        return aesEcb(key, xor(chained, last));
    }

    /** The subkey doubling of SP 800-38B: shift left one bit, and on carry XOR the field polynomial. */
    private static byte[] shiftLeft(byte[] input) {
        byte[] out = new byte[input.length];
        int carry = 0;
        for (int i = input.length - 1; i >= 0; i--) {
            int value = ((input[i] & 0xFF) << 1) | carry;
            carry = (value >> 8) & 1;
            out[i] = (byte) value;
        }
        if (carry != 0) {
            out[out.length - 1] ^= (byte) 0x87;
        }
        return out;
    }

    private static byte[] xor(byte[] left, byte[] right) {
        byte[] out = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            out[i] = (byte) (left[i] ^ right[i]);
        }
        return out;
    }

    private static byte[] cbc(byte[] key, byte[] iv, byte[] data, boolean encrypt) {
        byte[] full = new byte[24];
        if (key.length == 16) {
            System.arraycopy(key, 0, full, 0, 16);
            System.arraycopy(key, 0, full, 16, 8);
        } else {
            full = key.clone();
        }
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(encrypt ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(full, "DESede"),
                    new javax.crypto.spec.IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Triple DES is unavailable", e);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] joined = new byte[left.length + right.length];
        System.arraycopy(left, 0, joined, 0, left.length);
        System.arraycopy(right, 0, joined, left.length, right.length);
        return joined;
    }

    private static String normalizeAscii(String value) {
        return value == null ? "" : value.trim().replaceAll("[\\r\\n\\t]", "");
    }

    public static String describe(Unwrapped unwrapped) {
        StringBuilder report = new StringBuilder(describe(unwrapped.block()));
        report.append("\nUnwrapped\n");
        report.append("  authenticator : ").append(unwrapped.authentic() ? "MATCHES" : "DOES NOT MATCH")
                .append(" (expected ").append(unwrapped.expectedAuthenticator()).append(")\n");
        report.append("  key length    : ").append(unwrapped.keyBits()).append(" bits\n");
        report.append("  key           : ").append(unwrapped.clearKey()).append('\n');
        report.append("  padding       : ").append(unwrapped.padding()).append('\n');
        if (!unwrapped.authentic()) {
            report.append("\nThe key above came out of the block, but the authenticator does not match,\n");
            report.append("so either the LMK is wrong or the block was altered. Do not trust the key.\n");
        }
        return report.toString();
    }

    // =====================================================================
    // Bytes
    //
    // The block itself is ASCII and the parser above never needs these; only
    // the key material and the LMK are hexadecimal.
    // =====================================================================

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
        return normalized.isEmpty() ? new byte[0] : java.util.HexFormat.of().parseHex(normalized);
    }

    private static String hex(byte[] data) {
        return java.util.HexFormat.of().formatHex(data).toUpperCase(Locale.ROOT);
    }
}
