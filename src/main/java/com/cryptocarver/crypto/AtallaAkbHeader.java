package com.cryptocarver.crypto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The eight-character AKB header, byte by byte.
 *
 * <p>Source: Utimaco, <i>Atalla Key Block Migration Guide</i>, AJ560-9004A
 * (2019), tables 2-1 to 2-8. Decoding is informational: the vendor's own tool
 * builds a block whatever the header says, so nothing here gates
 * {@link AtallaAkbOperations}. Byte 5 must be {@code 0}, which matches the
 * {@code B5} complaint the tool raised for {@code 1PUNE100}.</p>
 */
public final class AtallaAkbHeader {

    private static final String[] BYTE_NAMES = {
        "Version", "Key usage", "Algorithm", "Mode of use",
        "Exportability", "Reserved", "Special handling", "Other information"
    };

    private static final Map<Character, String> VERSION = table(
            '1', "3DES format, key under a 3DES MFK",
            '3', "AES format, key under an AES MFK");

    private static final Map<Character, String> USAGE = table(
            'A', "ATM Master Key", 'B', "CMAC 3DES Key", 'C', "Card Verification Value / Code",
            'D', "Data Encryption", 'G', "Gilbarco",
            'I', "Initialization Vector (byte 7: M = CV-KMAC, P = CV-KPE)",
            'J', "Rijndael - AES", 'K', "Key Encryption Key",
            'M', "Message Authentication Code (byte 7: C = Challenge/Response)",
            'P', "PIN Encryption Key", 'R', "Root Signing Key (RSA commands)",
            'S', "Signing Key (RSA commands)", 'T', "Token Key", 'V', "PIN verification, KPV",
            'b', "Signing Token", 'c', "Communication Key (byte 7: B = PIN encrypt and MAC)",
            'd', "Derivation Key", 'i', "DUKPT Initial PIN Encryption Key",
            'k', "TR-31/TR-34 Key Wrapping Key",
            'm', "Master Key (byte 7: M = MAC, P = PAC, m = MF-MAC, p = MF-PAC)",
            'n', "Diebold/Burroughs Number Table, Decimalization/Conversion Table",
            'p', "Signing Key (RSA commands)", 's', "Signing Key (RSA commands)",
            'w', "TR-34 Key Message Signing Key");

    private static final Map<Character, String> ALGORITHM = table(
            '0', "SDI Security Dynamic Algorithm", '2', "HMAC-SHA256", '3', "IBM 3624", '4', "ARIS",
            '7', "IBM 4731", 'B', "Atalla Bilevel", 'C', "Conversion Table", 'D', "DES",
            'E', "EMV Key Derivation", 'F', "Key Derivation", 'H', "HMAC-SHA1", 'I', "Identikey",
            'J', "Rijndael - AES", 'N', "NCR", 'O', "Discover", 'P', "Preem", 'R', "RSA",
            'S', "Digital Signature Algorithm (DSA)", 'U', "unknown or unspecified", 'V', "Visa",
            'X', "American Express", 'Z', "Key Derivation", 'a', "Atalla 2x2", 'b', "Key Derivation",
            'd', "Diebold Number Table", 'i', "ICC Key Derivation", 'r', "Routex Derivation",
            's', "custom", 'u', "Unisys (Burroughs)");

    private static final Map<Character, String> MODE = table(
            'D', "Decrypt only", 'E', "Encrypt only", 'G', "Generate only", 'I', "Import RSA key",
            'N', "No special restrictions", 'R', "RSA", 'V', "Verify only", 'X', "Export RSA key",
            'p', "PIN Printing Key");

    private static final Map<Character, String> EXPORT = table(
            'E', "Exportable under a trusted key", 'N', "Not exportable",
            'S', "Sensitive, exportable under an untrusted key");

    private static final Map<Character, String> RESERVED = table('0', "Reserved, must be 0");

    private static final Map<Character, String> SPECIAL = table(
            '0', "No special considerations", '1', "Contains a derivation key component",
            '2', "Component of a 2-component key", '3', "Component of a 3-component key",
            '4', "Component of a 4-component key", 'C', "Key component - not a key",
            'E', "KEK usable only to export a working key", 'I', "KEK usable only to import a working key",
            'K', "KEK usable only to import a KEK", 'M', "Master File Key", 'd', "Derivation key",
            'e', "Export key exchange key component", 'i', "KEK usable only to import a key component - not a key",
            'k', "Key exchange key component", 'n', "Key component - not a key");

    private static final Map<Character, String> OTHER = table(
            '0', "Initialization/Control Vector (byte 1 = I)",
            '1', "ISO 16609 MAC algorithm 1 (TDEA) / ISO 9797-1 MAC algorithm 1",
            '2', "ISO 9797-1 MAC algorithm 2", '3', "ISO 9797-1 MAC algorithm 3",
            '4', "ISO 9797-1 MAC algorithm 4", '5', "ISO 9797-1 MAC algorithm 5",
            'B', "PIN and MAC key (only if byte 1 = c)", 'C', "Challenge/Response key (only if byte 1 = M)",
            'E', "EMV key if byte 1 = m: EMV Master Key - Confidentiality", 'I', "IVR key",
            'M', "MAC key if byte 1 = I or m; EMV Master Key - Integrity if byte 1 = m",
            'P', "PIN key if byte 1 = I; PAC key if byte 1 = m", 'S', "Sermepa Derivation", 'T', "SSL",
            'a', "EMV Master Key - Application Cryptogram", 'b', "Bank Master key, Data Decrypt and MAC key",
            'd', "EMV Master Key - Data Authentication", 'e', "EMV Master Key - Dynamic Numbers",
            'f', "EMV Master Key - Card Personalization", 'g', "EMV Master Key - Other",
            'k', "TR-34 Wrapping", 'm', "MAC key (only if byte 1 = m)", 'n', "Nordea Derivation Method",
            'p', "PAC key (only if byte 1 = m)", 'r', "Registration Master key");

    @SuppressWarnings("unchecked")
    private static final Map<Character, String>[] TABLES = new Map[] {
        VERSION, USAGE, ALGORITHM, MODE, EXPORT, RESERVED, SPECIAL, OTHER
    };

    /** One selectable value of one header byte. */
    public record Option(char code, String label) {
        @Override
        public String toString() {
            return code + " — " + label;
        }
    }

    /** A working-key header from table 2-9 of the migration guide. */
    public record Template(String name, String header) {
        @Override
        public String toString() {
            return name + "  (" + header + ")";
        }
    }

    private static final String[][] TEMPLATE_ROWS = {
        {"Master File Key (MFK)", "1KDDN0M0"},
        {"Key Encryption Key / ZMK / LMK / TMK (KEK)", "1KDNE000"},
        {"KEK, encrypt only (export)", "1KDEE000"},
        {"KEK, decrypt only (import)", "1KDDE000"},
        {"PIN Encryption Key (KPE / TPK / ZPK)", "1PUNE000"},
        {"PIN Encryption Key, DES (KPE-DES)", "1PDNE000"},
        {"KPE, encrypt only", "1PUEE000"},
        {"KPE, decrypt only", "1PUDE000"},
        {"ATM Communication Key (KC)", "1cDNE000"},
        {"Communication Key, encrypt only", "1cDEE000"},
        {"Communication Key, decrypt only", "1cDDE000"},
        {"PIN Encrypt and MAC (KC)", "1cDNE00B"},
        {"ATM A-Key, IBM 3624 (KATM)", "1K3NE000"},
        {"ATM Master Key, IBM 3624 (KM)", "1A3NE000"},
        {"ATM Master Key, IBM 4731 (KM)", "1A7NE000"},
        {"ATM Master Key (AMK)", "1ADNE000"},
        {"Data Encryption Key (KD)", "1DDNE000"},
        {"KD, encrypt only", "1DDEE000"},
        {"KD, decrypt only", "1DDDE000"},
        {"Message Authentication Key (KMAC / TAK / ZAK)", "1MDNE000"},
        {"KMAC, generate only", "1MDGE000"},
        {"KMAC, verify only", "1MDVE000"},
        {"KMAC used in Challenge/Response", "1MDNE00C"},
        {"KC used to encrypt IBM 4731 PIN block", "1c7NE000"},
        {"American Express Card Security Code (KCSC)", "1mXNE000"},
        {"CVV/CVC Key (KCVV)", "1CDNE000"},
        {"KCVV, generate only", "1CDGE000"},
        {"KCVV, verify only", "1CDVE000"},
        {"PIN Verification Key (KPV)", "1VUNE000"},
        {"KPV, generate only", "1VUGE000"},
        {"KPV, verify only", "1VUVE000"},
        {"PIN Verification Key, IBM 3624", "1V3NE000"},
        {"Visa PIN Verification Key pair", "1VVNE000"},
        {"PIN Verification Key, Atalla BiLevel", "1VBNE000"},
        {"PIN Verification Key, NCR", "1VNNE000"},
        {"PIN Verification Key, Atalla 2x2", "1VaNE000"},
        {"SecureID Card Seed Encryption Key (KCSE)", "1V0NE000"},
        {"Bank ID & Comparison Id, Identikey (BID)", "1VINE000"},
        {"MF-PAC-MK", "1mFNE00p"},
        {"MF-MAC-MK", "1mFNE00m"},
        {"Diebold Number Table row (DNT)", "1ndNE000"},
        {"Burroughs Number Table (BNT)", "1nuNE000"},
        {"Decimalization/Conversion Table", "1nCNE000"},
        {"Initialization Vector or MAB (IV)", "1IDNE000"},
        {"Control Vector for MAC derivation (CV-MAC)", "1IDNE00M"},
        {"Control Vector for KPE derivation (CV-PAC)", "1IDNE00P"},
        {"Derivation Key (KDREV)", "1dDNE000"},
        {"Master Key (KGK / MK)", "1mZNE000"},
        {"MAC Terminal Master Key (MAC-MK-SL)", "1mFNE00M"},
        {"Visa Stored Value Card Master Key (VSVCMK)", "1mVNE000"},
        {"Issuer Master Key, Application Cryptogram (IMK-AC)", "1mENE000"},
        {"ICC Intermediate Master Key (IMK)", "1miNE000"},
        {"Issuer Master Key, Message Integrity (IMK-MAC)", "1mENE00M"},
        {"Issuer Master Key, Message Confidentiality (IMK-ENC)", "1mENE00E"},
        {"Terminal PAC Master Key (PAC-MK)", "1mFNE00P"},
        {"Token Key type 1", "1TDNE001"},
        {"Token Key type 2", "1TDNE002"},
        {"Token Key type 3", "1TDNE003"},
        {"Token Key type 4", "1TDNE004"},
        {"PTK", "1PUEE000"},
        {"Import KEK (command 11B)", "1KDNE0I0"},
    };

    private AtallaAkbHeader() {
    }

    /** The values the migration guide allows for one header byte, in table order. */
    public static List<Option> options(int byteIndex) {
        List<Option> out = new ArrayList<>();
        TABLES[byteIndex].forEach((code, label) -> out.add(new Option(code, label)));
        return out;
    }

    public static String byteName(int byteIndex) {
        return BYTE_NAMES[byteIndex];
    }

    /** The working-key headers of table 2-9, ready to fill the eight selectors. */
    public static List<Template> templates() {
        List<Template> out = new ArrayList<>();
        for (String[] row : TEMPLATE_ROWS) {
            out.add(new Template(row[0], row[1]));
        }
        return out;
    }

    /** One line per header byte: {@code B1 'P' Key usage: PIN Encryption Key}. */
    public static String decode(String header) {
        if (header == null || header.length() != AtallaAkbOperations.HEADER_LENGTH) {
            throw new IllegalArgumentException("The header is " + AtallaAkbOperations.HEADER_LENGTH + " characters");
        }
        StringBuilder out = new StringBuilder();
        for (int at = 0; at < AtallaAkbOperations.HEADER_LENGTH; at++) {
            char c = header.charAt(at);
            String meaning = TABLES[at].get(c);
            out.append("  B").append(at).append(" '").append(c).append("'  ")
                    .append(BYTE_NAMES[at]).append(": ")
                    .append(meaning != null ? meaning : "not in the migration guide's table").append('\n');
        }
        return out.toString();
    }

    private static Map<Character, String> table(Object... pairs) {
        Map<Character, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Character) pairs[i], (String) pairs[i + 1]);
        }
        return map;
    }
}
