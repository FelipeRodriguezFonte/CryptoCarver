package com.cryptocarver.crypto;

import java.util.LinkedHashMap;
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

    private AtallaAkbHeader() {
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
