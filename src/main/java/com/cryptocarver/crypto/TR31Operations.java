package com.cryptocarver.crypto;

/**
 * Wrapper for TR31 implementation
 * Adapts TR31.java interface to KeysController expectations
 */
public class TR31Operations {

    /**
     * Documented TR-31 usage assignments (field 5–6). The public secondary
     * source is OpenEMV tr31 (LGPL-2.1), src/tr31.h and src/tr31_strings.c,
     * which cites ANSI X9.143:2021 §6.3.1 table 2. K4 is specifically an
     * ISO 20038 KBPK assignment in that source. The 2022 table was not
     * available for direct verification.
     */
    public static final java.util.Map<String, String> KEY_USAGES = java.util.Map.ofEntries(
            java.util.Map.entry("B0", "Base Derivation Key (BDK)"), java.util.Map.entry("B1", "Initial DUKPT Key"),
            java.util.Map.entry("B2", "Base Key Variant Key (deprecated)"), java.util.Map.entry("B3", "Key Derivation Key"),
            java.util.Map.entry("C0", "Card Verification Key (CVK)"), java.util.Map.entry("D0", "Symmetric Data Encryption"),
            java.util.Map.entry("D1", "Asymmetric Data Encryption"), java.util.Map.entry("D2", "Data Encryption Decimalization Table"),
            java.util.Map.entry("D3", "Data Encryption Sensitive Data"), java.util.Map.entry("E0", "EMV Application Cryptograms"),
            java.util.Map.entry("E1", "EMV Secure Messaging Confidentiality"), java.util.Map.entry("E2", "EMV Secure Messaging Integrity"),
            java.util.Map.entry("E3", "EMV Data Authentication Code"), java.util.Map.entry("E4", "EMV Dynamic Numbers"),
            java.util.Map.entry("E5", "EMV Card Personalization"), java.util.Map.entry("E6", "EMV Other"),
            java.util.Map.entry("E7", "EMV Asymmetric PIN Encryption"), java.util.Map.entry("I0", "Initialization Vector"),
            java.util.Map.entry("K0", "Key Encryption or Wrapping Key"), java.util.Map.entry("K1", "TR-31 Key Block Protection Key"),
            java.util.Map.entry("K2", "TR-34 Key Receiving Device Asymmetric Key Pair"), java.util.Map.entry("K3", "Asymmetric Key Wrapping or Key Agreement"),
            java.util.Map.entry("K4", "ISO 20038 Key Block Protection Key"), java.util.Map.entry("M0", "ISO 16609 MAC algorithm 1"),
            java.util.Map.entry("M1", "ISO 9797-1 MAC algorithm 1"), java.util.Map.entry("M2", "ISO 9797-1 MAC algorithm 2"),
            java.util.Map.entry("M3", "ISO 9797-1 MAC algorithm 3 (Retail MAC)"), java.util.Map.entry("M4", "ISO 9797-1 MAC algorithm 4"),
            java.util.Map.entry("M5", "ISO 9797-1 MAC algorithm 5 (legacy)"), java.util.Map.entry("M6", "ISO 9797-1 MAC algorithm 5 (CMAC)"),
            java.util.Map.entry("M7", "HMAC"), java.util.Map.entry("M8", "ISO 9797-1 MAC algorithm 6"),
            java.util.Map.entry("P0", "PIN Encryption Key"), java.util.Map.entry("P1", "PIN Generation Key"),
            java.util.Map.entry("S0", "Asymmetric Digital Signature Key Pair"), java.util.Map.entry("S1", "Asymmetric CA Key Pair"),
            java.util.Map.entry("S2", "Other Asymmetric Key Pair"), java.util.Map.entry("V0", "PIN Verification Key (other)"),
            java.util.Map.entry("V1", "PIN Verification Key (IBM 3624)"), java.util.Map.entry("V2", "PIN Verification Key (VISA PVV)"),
            java.util.Map.entry("V3", "PIN Verification Key (ANSI X9.132 algorithm 1)"), java.util.Map.entry("V4", "PIN Verification Key (ANSI X9.132 algorithm 2)"),
            java.util.Map.entry("V5", "PIN Verification Key (ANSI X9.132 algorithm 3)"));

    /** ANSI X9.143:2021 §§6.3.2–6.3.5 tables 3–6; cross-checked against OpenEMV tr31. */
    public static final java.util.Map<Character, String> ALGORITHMS = java.util.Map.of(
            'A', "AES", 'D', "DES", 'E', "Elliptic Curve", 'H', "HMAC", 'R', "RSA", 'S', "DSA", 'T', "Triple DES");
    public static final java.util.Map<Character, String> MODES = java.util.Map.ofEntries(
            java.util.Map.entry('B', "Encrypt/decrypt"), java.util.Map.entry('C', "MAC generate/verify"),
            java.util.Map.entry('D', "Decrypt only"), java.util.Map.entry('E', "Encrypt only"),
            java.util.Map.entry('G', "MAC generate only"), java.util.Map.entry('N', "No special restrictions"),
            java.util.Map.entry('S', "Signature only"), java.util.Map.entry('V', "MAC verify only"),
            java.util.Map.entry('X', "Key derivation"), java.util.Map.entry('Y', "Create key variants"));
    public static final java.util.Map<Character, String> EXPORTABILITY = java.util.Map.of(
            'E', "Exportable in trusted key blocks", 'N', "Not exportable", 'S', "Sensitive");
    /**
     * Optional block identifiers. Clause/table mapping is ANSI X9.143:2021:
     * AL §6.3.6.1 table 8; BI .2 table 9; CT .3 table 10; DA .4 table 12;
     * HM .5 table 13; IK .6 table 14; KC .7 table 15; KS .8 table 16;
     * KV .9 table 17; LB .10 table 18; PB .11 table 19; KP .12 table 20;
     * TC .13 table 21; TS .14 table 22; WP .15 table 23. FL and PK are
     * included only as implemented/documented by OpenEMV tr31 (LGPL-2.1),
     * src/tr31.h; no normative clause was verified for those two IDs.
     */
    public static final java.util.Map<String, String> OPTIONAL_BLOCKS = java.util.Map.ofEntries(
            java.util.Map.entry("AL", "Asymmetric Key Life"), java.util.Map.entry("BI", "DUKPT Base Key Identifier"),
            java.util.Map.entry("CT", "Public Key Certificate"), java.util.Map.entry("DA", "Derivations Allowed"),
            java.util.Map.entry("FL", "Flags"), java.util.Map.entry("HM", "HMAC Hash Algorithm"),
            java.util.Map.entry("IK", "Initial AES-DUKPT Key Identifier"), java.util.Map.entry("KC", "Wrapped Key Check Value"),
            java.util.Map.entry("KP", "KBPK Check Value"), java.util.Map.entry("KS", "TDEA-DUKPT Key Serial Number"),
            java.util.Map.entry("KV", "Deprecated Key Block Values"), java.util.Map.entry("LB", "User Label"),
            java.util.Map.entry("PB", "Padding"), java.util.Map.entry("PK", "Export KBPK Check Value"),
            java.util.Map.entry("TC", "Time of Creation"), java.util.Map.entry("TS", "Timestamp"),
            java.util.Map.entry("WP", "Wrapping Pedigree"));

    /**
     * Wrap a key into TR-31 format
     */
    public static String wrapKey(String kbpk, String key, String usage, char version, char algorithm, char mode, char exportability) throws Exception {
        return wrapKey(kbpk, key, usage, version, algorithm, mode, exportability, "");
    }

    /** Wraps a key with an optional compact TR-31 optional-block section (NNRR...). */
    public static String wrapKey(String kbpk, String key, String usage, char version, char algorithm, char mode,
            char exportability, String optionalBlocks) throws Exception {
        validateProtectionKey(version, kbpk);
        if (key == null || key.isEmpty() || (key.length() & 1) != 0 || !key.matches("(?i)[0-9a-f]+"))
            throw new IllegalArgumentException("Clear key must be non-empty, even-length hexadecimal data");
        TR31.validateMatrix(version, algorithm, usage, mode, exportability);

        // Build header with specified version
        HeaderBuilder builder = new HeaderBuilder()
            .version(version)
            .keyUsage(usage)
            .algorithm(algorithm)
            .modeOfUse(mode)
            .exportability(exportability);
        String normalizedOptionalBlocks = normalizeOptionalBlocks(optionalBlocks);
        validateOptionalCombinations(normalizedOptionalBlocks, usage, algorithm);
        if (!normalizedOptionalBlocks.isEmpty()) builder.optionalBlocks(normalizedOptionalBlocks);

        String header = builder.build();

        // Generate key block
        TR31 tr31 = new TR31(kbpk);
        return tr31.wrap(header, key);
    }

    /**
     * Validates and canonicalizes count/reserved and optional-block headers. Data is length-delimited and
     * preserved verbatim for textual fields (notably LB, CT, TC, TS, PB and KV); only hex-coded fields
     * are uppercased. This follows X9.143:2021 §6.3.6 tables 8–23 and OpenEMV tr31 (LGPL-2.1),
     * src/tr31.c (field validators and add/decode routines). Whitespace is accepted between records,
     * but within a record's data it is significant.
     */
    public static String normalizeOptionalBlocks(String optionalBlocks) {
        String source = optionalBlocks == null ? "" : optionalBlocks;
        int cursor = skipWhitespace(source, 0);
        if (cursor == source.length()) return "";
        StringBuilder normalized = new StringBuilder();
        String countText = takeMetadata(source, cursor, 2, "Optional-block count");
        cursor = skipWhitespace(source, cursor + 2);
        String reserved = takeMetadata(source, cursor, 2, "Optional-block reserved field");
        cursor = skipWhitespace(source, cursor + 2);
        final int count;
        try { count = Integer.parseInt(countText, 10); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Optional-block count must be two decimal characters", e); }
        if (!reserved.matches("(?i)[0-9A-Z]{2}")) throw new IllegalArgumentException("Optional-block reserved field must be two alphanumeric characters");
        normalized.append(countText.toUpperCase(java.util.Locale.ROOT)).append(reserved.toUpperCase(java.util.Locale.ROOT));
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        String previousId = null;
        for (int index = 0; index < count; index++) {
            if (cursor + 4 > source.length()) throw new IllegalArgumentException("Optional block " + (index + 1) + " header is truncated");
            String id = source.substring(cursor, cursor + 2).toUpperCase(java.util.Locale.ROOT);
            String lengthText = source.substring(cursor + 2, cursor + 4);
            if (!id.matches("[A-Z0-9]{2}")) throw new IllegalArgumentException("Optional-block identifier must be alphanumeric");
            if (!OPTIONAL_BLOCKS.containsKey(id)) throw new IllegalArgumentException("Unsupported optional-block identifier " + id + "; custom blocks are not in the documented catalogue");
            if (!seenIds.add(id)) throw new IllegalArgumentException("Optional block " + id + " must not occur more than once");
            if ("PB".equals(previousId)) throw new IllegalArgumentException("PB padding block must be last");
            final int blockLength;
            try { blockLength = Integer.parseInt(lengthText, 16); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Optional block " + id + " length must be hexadecimal", e); }
            // The length field is the length of the WHOLE optional block, its
            // two-character identifier and two-character length field included,
            // written in hexadecimal. 'KS08ABCD' is eight characters long and
            // carries four of data. Reading it as a count of data bytes to be
            // doubled walks past the next block.
            if (blockLength < 4) throw new IllegalArgumentException("Optional block " + id + " declares a length of " + blockLength + "; the minimum is 4 because the identifier and the length field are inside it");
            int end = cursor + blockLength;
            if (end > source.length()) throw new IllegalArgumentException("Optional block " + id + " is truncated");
            String data = source.substring(cursor + 4, end);
            validateOptionalBlockData(id, data);
            normalized.append(id).append(String.format(java.util.Locale.ROOT, "%02X", blockLength)).append(canonicalizeOptionalData(id, data));
            previousId = id;
            cursor = skipWhitespace(source, end);
        }
        if (cursor != source.length()) throw new IllegalArgumentException("Optional-block section contains trailing characters after " + count + " declared block(s)");
        return normalized.toString();
    }

    private static int skipWhitespace(String value, int cursor) {
        while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) cursor++;
        return cursor;
    }

    private static String takeMetadata(String value, int cursor, int length, String field) {
        if (cursor + length > value.length()) throw new IllegalArgumentException(field + " is truncated");
        String metadata = value.substring(cursor, cursor + length);
        if (metadata.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException(field + " must not contain whitespace");
        return metadata;
    }

    private static String canonicalizeOptionalData(String id, String data) {
        return switch (id) {
            case "AL", "BI", "HM", "IK", "KC", "KP", "KS", "PK", "WP" -> data.toUpperCase(java.util.Locale.ROOT);
            case "DA" -> data.substring(0, Math.min(2, data.length())).toUpperCase(java.util.Locale.ROOT)
                    + (data.length() > 2 ? data.substring(2) : "");
            case "CT" -> data.substring(0, Math.min(2, data.length())).toUpperCase(java.util.Locale.ROOT)
                    + (data.length() > 2 ? data.substring(2) : "");
            default -> data;
        };
    }

    /** Field formats follow X9.143:2021 §6.3.6 tables 8–23, cross-checked with OpenEMV tr31 (LGPL-2.1), src/tr31.c. */
    private static void validateOptionalBlockData(String id, String data) {
        switch (id) {
            case "AL" -> {
                if (!data.matches("(?i)01(?:00|01)")) throw invalidData(id, "must be version 01 and AKL value 00 (ephemeral) or 01 (static)");
            }
            case "BI" -> {
                if (!data.matches("(?i)00[0-9a-f]{10}|01[0-9a-f]{8}")) throw invalidData(id, "must be 00 plus a 5-byte TDES-DUKPT ID or 01 plus a 4-byte AES-DUKPT ID");
            }
            case "CT" -> validateCertificateData(data);
            case "DA" -> {
                if (data.length() < 7 || !data.matches("(?i)01(?:[a-z0-9]{5})+")) throw invalidData(id, "must be version 01 followed by one or more 5-character alphanumeric derivation tuples");
                for (int i = 2; i < data.length(); i += 5) {
                    String tuple = data.substring(i, i + 5).toUpperCase(java.util.Locale.ROOT);
                    if (!KEY_USAGES.containsKey(tuple.substring(0, 2)) || !ALGORITHMS.containsKey(tuple.charAt(2))
                            || !MODES.containsKey(tuple.charAt(3)) || !EXPORTABILITY.containsKey(tuple.charAt(4)))
                        throw invalidData(id, "contains an undefined usage/algorithm/mode/exportability tuple");
                }
            }
            case "FL", "LB", "PB" -> validatePrintableAscii(id, data);
            case "HM" -> {
                if (!java.util.Set.of("10", "20", "21", "22", "23", "24", "25", "30", "31", "32", "33", "40", "41").contains(data.toUpperCase(java.util.Locale.ROOT)))
                    throw invalidData(id, "must be a defined two-character HMAC hash identifier");
            }
            case "IK" -> { if (!data.matches("(?i)[0-9a-f]{16}")) throw invalidData(id, "must be an 8-byte hexadecimal Initial Key Identifier"); }
            case "KC", "KP", "PK" -> {
                if (!data.matches("(?i)00(?:[0-9a-f]{2}|[0-9a-f]{4}|[0-9a-f]{6})|01(?:[0-9a-f]{2}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8}|[0-9a-f]{10})"))
                    throw invalidData(id, "must contain KCV algorithm 00 (1–3 bytes) or 01 (1–5 bytes) and a non-empty KCV");
            }
            case "KS" -> { if (!data.matches("(?i)([0-9a-f]{20}|[0-9a-f]{16}|[0-9a-f]{4})")) throw invalidData(id, "must be a 10-byte KSN (8-byte legacy is also recognized)"); }
            case "KV" -> { if (data.length() != 4 || !isAlphanumeric(data)) throw invalidData(id, "must contain two 2-character alphanumeric values"); }
            case "TC", "TS" -> validateIso8601(id, data);
            case "WP" -> { if (!data.matches("(?i)00[0-3]")) throw invalidData(id, "must be version 00 and wrapping pedigree 0 through 3"); }
            default -> { }
        }
    }

    private static IllegalArgumentException invalidData(String id, String detail) { return new IllegalArgumentException(id + " data " + detail); }

    private static boolean isAlphanumeric(String value) { return value.matches("[A-Za-z0-9]*"); }

    private static void validatePrintableAscii(String id, String data) {
        if (data.chars().anyMatch(c -> c < 0x20 || c > 0x7E)) throw invalidData(id, "must contain printable ASCII characters");
    }

    private static void validateIso8601(String id, String data) {
        try {
            if (data.matches("[0-9]{14}Z|[0-9]{16}Z")) {
                String pattern = data.length() == 15 ? "uuuuMMddHHmmss'Z'" : "uuuuMMddHHmmssSS'Z'";
                java.time.LocalDateTime.parse(data, new java.time.format.DateTimeFormatterBuilder()
                        .appendPattern(pattern).toFormatter().withResolverStyle(java.time.format.ResolverStyle.STRICT));
            } else if (data.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{2})?Z")) {
                java.time.OffsetDateTime.parse(data);
            } else {
                throw invalidData(id, "must use a supported ISO 8601 UTC representation ending in Z");
            }
        } catch (java.time.format.DateTimeParseException e) {
            throw invalidData(id, "must contain a valid ISO 8601 UTC date/time");
        }
    }

    private static void validateCertificateData(String data) {
        if (data.startsWith("00") || data.startsWith("01")) {
            String cert = data.substring(2);
            if (cert.isEmpty() || !isBase64(cert)) throw invalidData("CT", "must be a certificate format (00 X.509 or 01 EMV) followed by Base64 data");
            return;
        }
        if (data.startsWith("02")) {
            int cursor = 2, elements = 0;
            while (cursor < data.length()) {
                if (cursor + 6 > data.length()) throw invalidData("CT", "certificate chain element header is truncated");
                String format = data.substring(cursor, cursor + 2), lengthText = data.substring(cursor + 2, cursor + 6);
                if (!format.matches("00|01")) throw invalidData("CT", "certificate chain contains an unsupported format");
                int length;
                try { length = Integer.parseInt(lengthText, 16); } catch (NumberFormatException e) { throw invalidData("CT", "certificate chain length must be hexadecimal"); }
                cursor += 6;
                if (length < 1 || cursor + length > data.length() || !isBase64(data.substring(cursor, cursor + length))) throw invalidData("CT", "certificate chain Base64 element has invalid length or data");
                cursor += length;
                elements++;
            }
            if (elements < 2) throw invalidData("CT", "certificate chain must contain at least two certificates");
            return;
        }
        throw invalidData("CT", "must start with X.509 (00), EMV (01), or certificate-chain (02) format");
    }

    private static boolean isBase64(String value) {
        try { java.util.Base64.getDecoder().decode(value); return value.matches("[A-Za-z0-9+/]+={0,2}"); }
        catch (IllegalArgumentException e) { return false; }
    }

    /** Human-readable interpretation of documented optional-block payload fields. */
    public static String describeOptionalBlockData(String id, String data) {
        if (data == null) return "";
        return switch (id) {
            case "AL" -> "01".equalsIgnoreCase(data.substring(0, Math.min(2, data.length())))
                    ? (data.endsWith("00") ? "ephemeral asymmetric key" : "static asymmetric key") : "unknown asymmetric key life";
            case "BI" -> data.startsWith("00") ? "TDEA DUKPT base identifier: " + data.substring(2)
                    : "AES DUKPT base identifier: " + data.substring(Math.min(2, data.length()));
            case "CT" -> (data.startsWith("00") ? "X.509 certificate" : data.startsWith("01") ? "EMV certificate" : "certificate chain");
            case "DA" -> "derivation version " + data.substring(0, Math.min(2, data.length()))
                    + ", attributes: " + data.substring(Math.min(2, data.length()));
            case "HM" -> java.util.Map.ofEntries(
                    java.util.Map.entry("10", "SHA-1"), java.util.Map.entry("20", "SHA-224"),
                    java.util.Map.entry("21", "SHA-256"), java.util.Map.entry("22", "SHA-384"),
                    java.util.Map.entry("23", "SHA-512"), java.util.Map.entry("24", "SHA-512/224"),
                    java.util.Map.entry("25", "SHA-512/256"), java.util.Map.entry("30", "SHA3-224"),
                    java.util.Map.entry("31", "SHA3-256"), java.util.Map.entry("32", "SHA3-384"),
                    java.util.Map.entry("33", "SHA3-512"), java.util.Map.entry("40", "SHAKE128"),
                    java.util.Map.entry("41", "SHAKE256")).getOrDefault(data.toUpperCase(java.util.Locale.ROOT), "unknown hash identifier");
            case "KC", "KP", "PK" -> (data.startsWith("00") ? "legacy" : "CMAC")
                    + " KCV: " + data.substring(Math.min(2, data.length()));
            case "IK" -> "AES DUKPT initial key identifier: " + data;
            case "KS" -> "TDEA DUKPT initial key serial number: " + data;
            case "KV" -> "key block values: " + data;
            case "LB" -> "label: " + data;
            case "PB" -> "padding (" + data.length() + " characters)";
            case "TC", "TS" -> "ISO 8601 UTC: " + data;
            case "WP" -> "wrapping pedigree version " + data.substring(0, Math.min(2, data.length()))
                    + ", value " + data.substring(Math.min(2, data.length()));
            default -> data;
        };
    }

    /** Cross-field rules backed by OpenEMV tr31 and IBM's X9.143 block-data profile. */
    private static void validateOptionalCombinations(String optionalBlocks, String usage, char algorithm) {
        boolean hm = false, da = false;
        for (OptionalBlock block : parseCompactBlocks(optionalBlocks)) {
            hm |= "HM".equals(block.id());
            da |= "DA".equals(block.id());
        }
        if (algorithm == 'H' && !hm) throw new IllegalArgumentException("Algorithm H requires the HM hash-algorithm optional block");
        if (hm && algorithm != 'H') throw new IllegalArgumentException("HM is defined for HMAC algorithm H");
        if (da && !"B3".equals(usage)) throw new IllegalArgumentException("DA is defined for key usage B3");
    }

    private static java.util.List<OptionalBlock> parseCompactBlocks(String compact) {
        java.util.List<OptionalBlock> blocks = new java.util.ArrayList<>();
        if (compact.isEmpty()) return blocks;
        int count = Integer.parseInt(compact.substring(0, 2), 10), position = 4;
        for (int i = 0; i < count; i++) {
            String id = compact.substring(position, position + 2);
            int length = Integer.parseInt(compact.substring(position + 2, position + 4), 16);
            String data = compact.substring(position + 4, position + length);
            blocks.add(new OptionalBlock(id, data.length(), data));
            position += length;
        }
        return blocks;
    }

    /**
     * Unwrap a TR-31 key block
     */
    public static String unwrapKey(String kbpk, String keyBlock) throws Exception {
        if (keyBlock == null || keyBlock.length() < 1) throw new IllegalArgumentException("TR-31 key block is empty");
        validateProtectionKey(keyBlock.charAt(0), kbpk);
        TR31 tr31 = new TR31(kbpk);
        TR31.UnwrapResult result = tr31.unwrap(keyBlock);
        return bytesToHex(result.key);
    }

    /**
     * Version-to-KBPK algorithm matrix (ANSI X9.143 key block method profiles;
     * independently corroborated by OpenEMV tr31/src/tr31.c). A/B/C use TDES
     * KBPKs and D uses AES KBPKs. This validates the protection key, not the
     * separate algorithm field describing the wrapped key.
     */
    private static void validateProtectionKey(char version, String kbpkHex) {
        if (kbpkHex == null || !kbpkHex.matches("(?i)([0-9a-f]{32}|[0-9a-f]{48}|[0-9a-f]{64})"))
            throw new IllegalArgumentException("KBPK must be 16, 24, or 32 bytes of hexadecimal key material");
        int bytes = kbpkHex.length() / 2;
        if ((version == 'A' || version == 'B' || version == 'C') && bytes == 32)
            throw new IllegalArgumentException("TR-31 version " + version + " requires a TDES KBPK (16 or 24 bytes)");
        if (version == 'D' && bytes != 16 && bytes != 24 && bytes != 32)
            throw new IllegalArgumentException("TR-31 version D requires an AES KBPK");
    }

    /**
     * Parse TR-31 header from key block
     */
    public static String parseHeader(String keyBlock) {
        try {
            TR31Header header = TR31Header.parse(keyBlock);
            StringBuilder sb = new StringBuilder();
            sb.append("Version ID: ").append(header.versionId).append("\n");
            sb.append("Length: ").append(header.keyBlockLength).append("\n");
            sb.append("Key Usage: ").append(header.keyUsage).append("\n");
            sb.append("Algorithm: ").append(header.algorithm).append("\n");
            sb.append("Mode of Use: ").append(header.modeOfUse).append("\n");
            sb.append("Key Version: ").append(header.keyVersionNumber).append("\n");
            sb.append("Exportability: ").append(header.exportability).append("\n");
            sb.append("Input Length: ").append(keyBlock == null ? 0 : keyBlock.length()).append("\n");
            sb.append("Optional Blocks: ").append(header.optionalBlockDetails.size()).append("\n");
            for (OptionalBlock block : header.optionalBlockDetails) {
                sb.append("  - ").append(block.id()).append(": ").append(block.dataCharacters()).append(" characters, data=")
                        .append(block.data()).append(", decoded=")
                        .append(describeOptionalBlockData(block.id(), block.data())).append("\n");
            }
            if (!header.diagnostics.isEmpty()) {
                sb.append("Diagnostics:\n");
                for (String diagnostic : header.diagnostics) sb.append("  - ").append(diagnostic).append("\n");
            } else {
                sb.append("Diagnostics: no structural warnings\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Invalid TR-31 key block: " + e.getMessage();
        }
    }

    /** Read-only batch representation of header fields, decoded optional blocks and diagnostics. */
    public static String parseHeaderJson(String keyBlock) {
        try {
            TR31Header header = TR31Header.parse(keyBlock);
            java.util.List<java.util.Map<String, Object>> blocks = header.optionalBlockDetails.stream().map(block -> {
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("id", block.id());
                item.put("description", OPTIONAL_BLOCKS.getOrDefault(block.id(), "Unknown optional block"));
                item.put("data", block.data());
                item.put("dataCharacters", block.dataCharacters());
                item.put("decoded", describeOptionalBlockData(block.id(), block.data()));
                return item;
            }).toList();
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("version", header.versionId);
            result.put("length", header.keyBlockLength);
            result.put("usage", header.keyUsage);
            result.put("usageDescription", getKeyUsageDescription(header.keyUsage));
            result.put("algorithm", header.algorithm);
            result.put("algorithmDescription", getAlgorithmDescription(header.algorithm.charAt(0)));
            result.put("mode", header.modeOfUse);
            result.put("modeDescription", getModeOfUseDescription(header.modeOfUse.charAt(0)));
            result.put("exportability", header.exportability);
            result.put("exportabilityDescription", getExportabilityDescription(header.exportability.charAt(0)));
            result.put("optionalBlocks", blocks);
            result.put("warnings", header.getDiagnostics());
            return new com.google.gson.Gson().toJson(result);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid TR-31 key block: " + e.getMessage(), e);
        }
    }

    /**
     * Get description for key usage code
     */
    public static String getKeyUsageDescription(String usage) {
        return KEY_USAGES.getOrDefault(usage, usage);
    }

    /**
     * Get description for algorithm code
     */
    public static String getAlgorithmDescription(char algorithm) {
        return ALGORITHMS.getOrDefault(algorithm, String.valueOf(algorithm));
    }

    /**
     * Get description for mode of use code
     */
    public static String getModeOfUseDescription(char mode) {
        // T is retained as an existing compatibility value used by historical inputs.
        return MODES.getOrDefault(mode, mode == 'T' ? "Both Sign & Key Transport" : String.valueOf(mode));
    }

    /**
     * Get description for exportability code
     */
    public static String getExportabilityDescription(char exportability) {
        return EXPORTABILITY.getOrDefault(exportability, String.valueOf(exportability));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * TR31Header class for compatibility with KeysController
     */
    public static class TR31Header {
        public String versionId;
        public int keyBlockLength;
        public String keyUsage;
        public String algorithm;
        public String modeOfUse;
        public String keyVersionNumber;
        public String exportability;
        public int numOptionalBlocks;
        public String reserved;
        public String optionalBlocks;
        public java.util.List<OptionalBlock> optionalBlockDetails = new java.util.ArrayList<>();
        /** Non-fatal findings. Parsing remains permissive enough to inspect peer key blocks. */
        public java.util.List<String> diagnostics = new java.util.ArrayList<>();

        public static TR31Header parse(String keyBlock) throws Exception {
            if (keyBlock.length() < 16) {
                throw new IllegalArgumentException("TR-31 key block too short");
            }

            TR31Header header = new TR31Header();
            header.versionId = String.valueOf(keyBlock.charAt(0));
            if (!"ABCD".contains(header.versionId)) {
                throw new IllegalArgumentException("Unsupported TR-31 version: " + header.versionId);
            }
            header.keyBlockLength = Integer.parseInt(keyBlock.substring(1, 5));
            if (header.keyBlockLength < 16) {
                throw new IllegalArgumentException("Invalid TR-31 declared length: " + header.keyBlockLength);
            }
            if (keyBlock.length() < header.keyBlockLength) {
                throw new IllegalArgumentException("TR-31 block is truncated: declares " + header.keyBlockLength
                        + " characters but contains " + keyBlock.length());
            }
            if (keyBlock.length() > header.keyBlockLength) {
                header.diagnostics.add("WARNING: input has " + (keyBlock.length() - header.keyBlockLength)
                        + " trailing character(s) beyond the declared key-block length");
            }
            header.keyUsage = keyBlock.substring(5, 7);
            header.algorithm = String.valueOf(keyBlock.charAt(7));
            header.modeOfUse = String.valueOf(keyBlock.charAt(8));
            header.keyVersionNumber = keyBlock.substring(9, 11);
            header.exportability = String.valueOf(keyBlock.charAt(11));
            header.numOptionalBlocks = Integer.parseInt(keyBlock.substring(12, 14), 10);
            header.reserved = keyBlock.substring(14, 16);

            if (!header.keyUsage.matches("[A-Z0-9]{2}")) header.diagnostics.add("WARNING: key-usage field is not alphanumeric");
            if (getKeyUsageDescription(header.keyUsage).equals(header.keyUsage)) {
                header.diagnostics.add("INFO: key usage " + header.keyUsage + " is not in CryptoCarver's descriptive catalogue");
            }
            if (getAlgorithmDescription(header.algorithm.charAt(0)).equals(header.algorithm)) {
                header.diagnostics.add("WARNING: unknown algorithm identifier " + header.algorithm);
            }
            if (getModeOfUseDescription(header.modeOfUse.charAt(0)).equals(header.modeOfUse)) {
                header.diagnostics.add("WARNING: unknown mode-of-use identifier " + header.modeOfUse);
            }
            if (getExportabilityDescription(header.exportability.charAt(0)).equals(header.exportability)) {
                header.diagnostics.add("WARNING: unknown exportability identifier " + header.exportability);
            }
            if (!"00".equals(header.reserved)) header.diagnostics.add("INFO: reserved field is " + header.reserved + "; verify the peer profile permits it");
            if ("A".equals(header.versionId) || "C".equals(header.versionId)) header.diagnostics.add("WARNING: version " + header.versionId + " is a legacy variant-binding profile");
            header.diagnostics.add("INFO: version " + header.versionId + " determines the KBPK method; the KBPK algorithm is not encoded in this header");

            try {
                TR31.validateMatrix(header.versionId.charAt(0), header.algorithm.charAt(0), header.keyUsage, header.modeOfUse.charAt(0), header.exportability.charAt(0));
            } catch (IllegalArgumentException e) {
                header.diagnostics.add("ERROR: " + e.getMessage());
            }

            // Extract optional blocks if present
            int position = 16;
            int limit = Math.min(header.keyBlockLength, keyBlock.length());
            java.util.Set<String> seenIds = new java.util.HashSet<>();
            for (int i = 0; i < header.numOptionalBlocks; i++) {
                if (position + 4 > limit) throw new IllegalArgumentException("Optional block " + (i + 1) + " header is truncated");
                String id = keyBlock.substring(position, position + 2);
                int blockLength;
                try { blockLength = Integer.parseInt(keyBlock.substring(position + 2, position + 4), 16); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Optional block " + id + " has invalid hexadecimal length"); }
                if (blockLength < 4) throw new IllegalArgumentException("Optional block " + id + " declares a length of " + blockLength + "; the minimum is 4 because the identifier and the length field are inside it");
                int end = position + blockLength;
                if (end > limit) throw new IllegalArgumentException("Optional block " + id + " is truncated (declares " + blockLength + " characters)");
                String data = keyBlock.substring(position + 4, end);
                if (!id.matches("[A-Z0-9]{2}")) header.diagnostics.add("WARNING: optional-block identifier " + id + " is not alphanumeric");
                if (!OPTIONAL_BLOCKS.containsKey(id)) header.diagnostics.add("WARNING: optional block " + id + " is not in the documented standard catalogue");
                if (!seenIds.add(id)) header.diagnostics.add("ERROR: optional block " + id + " occurs more than once");
                if (i > 0 && "PB".equals(header.optionalBlockDetails.get(i - 1).id())) header.diagnostics.add("ERROR: PB padding block must be last");
                if (!data.matches("[0-9A-Fa-f]*")) header.diagnostics.add("INFO: optional block " + id + " contains non-hex data; shown verbatim");
                try { validateOptionalBlockData(id, data); }
                catch (IllegalArgumentException invalidData) { header.diagnostics.add("WARNING: optional block " + id + " data: " + invalidData.getMessage()); }
                if ("BI".equals(id) && ((data.startsWith("00") && header.algorithm.charAt(0) != 'T')
                        || (data.startsWith("01") && header.algorithm.charAt(0) != 'A')))
                    header.diagnostics.add("WARNING: BI key-type identifier is inconsistent with the header algorithm");
                // Historical CryptoCarver fixtures use a synthetic four-hex-digit KS value. Keep parsing/wrapping
                // this value stable, but make the X9.143-profile deviation visible to readers.
                if ("KS".equals(id) && data.matches("(?i)[0-9a-f]{4}"))
                    header.diagnostics.add("WARNING: legacy CryptoCarver KS value is not the standard 10-byte KSN or 8-byte legacy KSN");
                header.optionalBlockDetails.add(new OptionalBlock(id, data.length(), data));
                position = end;
            }
            header.optionalBlocks = keyBlock.substring(16, position);
            if (position > header.keyBlockLength) throw new IllegalArgumentException("Optional blocks exceed declared key-block length");
            try { validateOptionalCombinations(header.optionalBlocks, header.keyUsage, header.algorithm.charAt(0)); }
            catch (IllegalArgumentException e) { header.diagnostics.add("ERROR: " + e.getMessage()); }

            return header;
        }

        public java.util.List<String> getDiagnostics() {
            return java.util.List.copyOf(diagnostics);
        }

        /**
         * Build header string (for compatibility)
         */
        public String build() {
            StringBuilder sb = new StringBuilder();
            sb.append(versionId);
            sb.append(String.format("%04d", keyBlockLength));
            sb.append(keyUsage);
            sb.append(algorithm);
            sb.append(modeOfUse);
            sb.append(keyVersionNumber);
            sb.append(exportability);
            sb.append(String.format("%02d", numOptionalBlocks));
            sb.append(reserved);
            if (optionalBlocks != null && !optionalBlocks.isEmpty()) {
                sb.append(optionalBlocks);
            }
            return sb.toString();
        }
    }

    /** Parsed optional block in the compact format currently emitted by HeaderBuilder. */
    /** @param dataCharacters the data alone: the declared length less the four
 *                       characters of identifier and length field */
    public record OptionalBlock(String id, int dataCharacters, String data) { }
}
