package com.cryptoforge.crypto;

/**
 * Wrapper for TR31 implementation
 * Adapts TR31.java interface to KeysController expectations
 */
public class TR31Operations {
    
    /**
     * Wrap a key into TR-31 format
     */
    public static String wrapKey(String kbpk, String key, String usage, char version, char algorithm, char mode, char exportability) throws Exception {
        return wrapKey(kbpk, key, usage, version, algorithm, mode, exportability, "");
    }

    /** Wraps a key with an optional compact TR-31 optional-block section (NNRR...). */
    public static String wrapKey(String kbpk, String key, String usage, char version, char algorithm, char mode,
            char exportability, String optionalBlocks) throws Exception {
        // Build header with specified version
        HeaderBuilder builder = new HeaderBuilder()
            .version(version)
            .keyUsage(usage)
            .algorithm(algorithm)
            .modeOfUse(mode)
            .exportability(exportability);
        String normalizedOptionalBlocks = normalizeOptionalBlocks(optionalBlocks);
        if (!normalizedOptionalBlocks.isEmpty()) builder.optionalBlocks(normalizedOptionalBlocks);
        
        String header = builder.build();
        
        // Generate key block
        TR31 tr31 = new TR31(kbpk);
        return tr31.wrap(header, key);
    }

    /** Validates the compact form: count (hex), reserved (2 chars), then ID/length/data entries. */
    public static String normalizeOptionalBlocks(String optionalBlocks) {
        String compact = optionalBlocks == null ? "" : optionalBlocks.replaceAll("\\s+", "").toUpperCase();
        if (compact.isEmpty()) return "";
        if (compact.length() < 4) throw new IllegalArgumentException("Optional blocks need count and reserved fields (for example 0100KS02ABCD)");
        final int count;
        try { count = Integer.parseInt(compact.substring(0, 2), 16); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Optional-block count must be two hexadecimal characters", e); }
        if (!compact.substring(2, 4).matches("[0-9A-Z]{2}")) throw new IllegalArgumentException("Optional-block reserved field must be two alphanumeric characters");
        int position = 4;
        for (int index = 0; index < count; index++) {
            if (position + 4 > compact.length()) throw new IllegalArgumentException("Optional block " + (index + 1) + " header is truncated");
            String id = compact.substring(position, position + 2);
            if (!id.matches("[A-Z0-9]{2}")) throw new IllegalArgumentException("Optional-block identifier must be alphanumeric");
            final int byteLength;
            try { byteLength = Integer.parseInt(compact.substring(position + 2, position + 4), 16); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Optional block " + id + " length must be hexadecimal", e); }
            position += 4 + byteLength * 2;
            if (position > compact.length()) throw new IllegalArgumentException("Optional block " + id + " is truncated");
        }
        if (position != compact.length()) throw new IllegalArgumentException("Optional-block section contains trailing characters after " + count + " declared block(s)");
        return compact;
    }
    
    /**
     * Unwrap a TR-31 key block
     */
    public static String unwrapKey(String kbpk, String keyBlock) throws Exception {
        TR31 tr31 = new TR31(kbpk);
        TR31.UnwrapResult result = tr31.unwrap(keyBlock);
        return bytesToHex(result.key);
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
                sb.append("  - ").append(block.id()).append(": ").append(block.dataLength()).append(" bytes, data=")
                        .append(block.data()).append("\n");
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
    
    /**
     * Get description for key usage code
     */
    public static String getKeyUsageDescription(String usage) {
        switch (usage) {
            case "B0": return "BDK Base Derivation Key";
            case "B1": return "Initial DUKPT Key";
            case "C0": return "CVK Card Verification Key";
            case "D0": return "Data Encryption (symmetric)";
            case "D1": return "Data Encryption (asymmetric)";
            case "I0": return "Initialization Vector";
            case "K0": return "Key Encryption/Wrapping";
            case "K1": return "TR-31 KBPK";
            case "M0": return "ISO 16609 MAC (algorithm 1)";
            case "M1": return "ISO 9797-1 MAC (algorithm 1)";
            case "M3": return "ISO 9797-1 MAC (algorithm 3 - Retail)";
            case "M5": return "ISO 9797-1 MAC (algorithm 5)";
            case "M6": return "ISO 9797-1 MAC (CMAC)";
            case "M7": return "HMAC";
            case "P0": return "PIN Encryption";
            case "V0": return "PIN Verification (other)";
            case "V1": return "PIN Verification (IBM 3624)";
            case "V2": return "PIN Verification (VISA PVV)";
            case "S0": return "Asymmetric key for digital signature";
            case "E0": return "EMV/Chip Issuer Master Key";
            default: return usage;
        }
    }
    
    /**
     * Get description for algorithm code
     */
    public static String getAlgorithmDescription(char algorithm) {
        switch (algorithm) {
            case 'A': return "AES";
            case 'D': return "DES (single)";
            case 'E': return "Elliptic Curve";
            case 'H': return "HMAC";
            case 'R': return "RSA";
            case 'S': return "DSA";
            case 'T': return "Triple DES (TDES)";
            default: return String.valueOf(algorithm);
        }
    }
    
    /**
     * Get description for mode of use code
     */
    public static String getModeOfUseDescription(char mode) {
        switch (mode) {
            case 'B': return "Both Encrypt & Decrypt";
            case 'C': return "Both Generate & Verify";
            case 'D': return "Decrypt Only";
            case 'E': return "Encrypt Only";
            case 'G': return "Generate Only";
            case 'N': return "No Special Restrictions";
            case 'S': return "Signature Only";
            case 'T': return "Both Sign & Key Transport";
            case 'V': return "Verify Only";
            case 'X': return "Key Derivation";
            case 'Y': return "Create Cryptographic Checksum";
            default: return String.valueOf(mode);
        }
    }
    
    /**
     * Get description for exportability code
     */
    public static String getExportabilityDescription(char exportability) {
        switch (exportability) {
            case 'E': return "Exportable";
            case 'N': return "Non-exportable";
            case 'S': return "Sensitive";
            default: return String.valueOf(exportability);
        }
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
            header.numOptionalBlocks = Integer.parseInt(keyBlock.substring(12, 14), 16);
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
            if ("D".equals(header.versionId) && !"A".equals(header.algorithm)) header.diagnostics.add("WARNING: version D normally protects AES key material; algorithm is " + header.algorithm);
            if (("A".equals(header.versionId) || "B".equals(header.versionId) || "C".equals(header.versionId)) && "A".equals(header.algorithm)) {
                header.diagnostics.add("WARNING: AES key material with version " + header.versionId + " needs confirmation against the key-block profile");
            }
            
            // Extract optional blocks if present
            int position = 16;
            int limit = Math.min(header.keyBlockLength, keyBlock.length());
            for (int i = 0; i < header.numOptionalBlocks; i++) {
                if (position + 4 > limit) throw new IllegalArgumentException("Optional block " + (i + 1) + " header is truncated");
                String id = keyBlock.substring(position, position + 2);
                int dataLength;
                try { dataLength = Integer.parseInt(keyBlock.substring(position + 2, position + 4), 16); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Optional block " + id + " has invalid hexadecimal length"); }
                int end = position + 4 + dataLength * 2;
                if (end > limit) throw new IllegalArgumentException("Optional block " + id + " is truncated (declares " + dataLength + " bytes)");
                String data = keyBlock.substring(position + 4, end);
                if (!id.matches("[A-Z0-9]{2}")) header.diagnostics.add("WARNING: optional-block identifier " + id + " is not alphanumeric");
                if (!data.matches("[0-9A-Fa-f]*")) header.diagnostics.add("INFO: optional block " + id + " contains non-hex data; shown verbatim");
                header.optionalBlockDetails.add(new OptionalBlock(id, dataLength, data));
                position = end;
            }
            header.optionalBlocks = keyBlock.substring(16, position);
            if (position > header.keyBlockLength) throw new IllegalArgumentException("Optional blocks exceed declared key-block length");
            
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
    public record OptionalBlock(String id, int dataLength, String data) { }
}
