package com.cryptocarver.ui;

import com.cryptocarver.crypto.TR31Operations;

/** Input processing and report formatting without JavaFX or module controller references. */
final class Tr31Logic {
    private Tr31Logic() { }

    record ExportResult(String kbpk, String key, String usage, String keyBlock, TR31Operations.TR31Header header, String report) { }

    static ExportResult export(String kbpkText, String keyText, String versionStr, String usageStr, String algoStr, String modeStr, String exportStr, String optionalBlocks, java.util.function.Function<String, String> text) throws Exception {
        String kbpk = kbpkText.trim().replaceAll("\\s+", "");
        String key = keyText.trim().replaceAll("\\s+", "");

        // Validate inputs
        if (kbpk.isEmpty() || key.isEmpty()) {
            throw new KeyDistributionValidation("module.keys.tr31.required", kbpk.isEmpty() ? "tr31KbpkExportField" : "tr31KeyToWrapField");
        }

        if (!kbpk.matches("[0-9A-Fa-f]+")) {
            throw new KeyDistributionValidation("module.keys.tr31.kbpkInvalid", "tr31KbpkExportField");
        }

        if (!key.matches("[0-9A-Fa-f]+")) {
            throw new KeyDistributionValidation("module.keys.tr31.keyInvalid", "tr31KeyToWrapField");
        }

        // Extract parameters
        char version = versionStr.charAt(0); // 'B' or 'D'

        String usage = usageStr.substring(0, 2); // Extract "P0", "D0", etc.

        char algorithm = algoStr.charAt(0); // 'T' or 'A'

        char mode = modeStr.charAt(0); // 'E', 'D', 'B', etc.

        char exportability = exportStr.charAt(0); // 'E', 'N', or 'S'

        // Wrap key
        String keyBlock = TR31Operations.wrapKey(kbpk, key, usage, version, algorithm, mode, exportability, optionalBlocks);

        // Parse header for display
        TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

        // Build result
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("TR-31 KEY BLOCK EXPORT\n");
        result.append("========================================\n\n");

        result.append("HEADER INFORMATION:\n");
        result.append("------------------\n");
        result.append("Version ID:        ").append(header.versionId).append("\n");
        result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
        result.append("Key Usage:         ").append(header.keyUsage);
        result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
        result.append("Algorithm:         ").append(header.algorithm);
        result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                .append(")\n");
        result.append("Mode of Use:       ").append(header.modeOfUse);
        result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                .append(")\n");
        result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
        result.append("Exportability:     ").append(header.exportability);
        result.append(" (").append(TR31Operations.getExportabilityDescription(header.exportability.charAt(0)))
                .append(")\n");
        result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n\n");
        if (!header.optionalBlockDetails.isEmpty()) {
            result.append(text.apply("module.keys.tr31.optionalBlock").toUpperCase(java.util.Locale.ROOT)).append(":\n");
            for (TR31Operations.OptionalBlock block : header.optionalBlockDetails) {
                result.append("  ").append(block.id()).append(" (" ).append(block.dataCharacters()).append(" characters): ").append(block.data()).append("\n");
            }
            result.append("\n");
        }

        result.append("KEY BLOCK:\n");
        result.append("------------------\n");
        result.append(keyBlock).append("\n\n");

        result.append("KEY BLOCK (Formatted):\n");
        result.append("------------------\n");
        result.append("Header:       ")
                .append(keyBlock.substring(0, Math.min(header.build().length(), keyBlock.length()))).append("\n");
        int headerLen = header.build().length();
        int macLen = (header.versionId.equals("A") || header.versionId.equals("C")) ? 8 : 16;
        if (keyBlock.length() > headerLen + macLen) {
            result.append("Encrypted Key: ").append(keyBlock.substring(headerLen, keyBlock.length() - macLen))
                    .append("\n");
            result.append("MAC:          ").append(keyBlock.substring(keyBlock.length() - macLen)).append("\n");
        }

        result.append("\n========================================\n");
        return new ExportResult(kbpk, key, usage, keyBlock, header, result.toString());
    }

    record ImportResult(TR31Operations.TR31Header header, String unwrappedKey, String report) { }

    static ImportResult importBlock(String kbpkText, String blockText) throws Exception {
        String kbpk = kbpkText.trim().replaceAll("\\s+", "");
        String keyBlock = blockText.trim().replaceAll("\\s+", "");

        // Validate inputs
        if (kbpk.isEmpty() || keyBlock.isEmpty()) {
            throw new KeyDistributionValidation("module.keys.tr31.keyBlockRequired", kbpk.isEmpty() ? "tr31KbpkImportField" : "tr31KeyBlockField");
        }

        // Parse header
        TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

        // Unwrap key
        String unwrappedKey = TR31Operations.unwrapKey(kbpk, keyBlock);

        // Build result
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("TR-31 KEY BLOCK IMPORT\n");
        result.append("========================================\n\n");

        result.append("HEADER INFORMATION:\n");
        result.append("------------------\n");
        result.append("Version ID:        ").append(header.versionId).append("\n");
        result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
        result.append("Key Usage:         ").append(header.keyUsage);
        result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
        result.append("Algorithm:         ").append(header.algorithm);
        result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                .append(")\n");
        result.append("Mode of Use:       ").append(header.modeOfUse);
        result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                .append(")\n");
        result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
        result.append("Exportability:     ").append(header.exportability).append("\n");
        result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n");
        result.append("\n");

        result.append("UNWRAPPED KEY:\n");
        result.append("------------------\n");
        result.append(unwrappedKey.toUpperCase()).append("\n");
        result.append("\nKey Length: ").append(unwrappedKey.length() / 2).append(" bytes (");
        result.append(unwrappedKey.length()).append(" hex characters)\n");

        result.append("\n========================================\n");
        return new ImportResult(header, unwrappedKey, result.toString());
    }

    record ParseResult(String report) { }

    static ParseResult parseHeader(String blockText, java.util.function.Function<String, String> text) throws Exception {
        String keyBlock = blockText.trim().replaceAll("\\s+", "");

        if (keyBlock.isEmpty()) {
            throw new KeyDistributionValidation("module.keys.tr31.keyBlockRequired", "tr31KeyBlockField");
        }

        // Parse header
        TR31Operations.TR31Header header = TR31Operations.TR31Header.parse(keyBlock);

        // Build result
        StringBuilder result = new StringBuilder();
        result.append("========================================\n");
        result.append("TR-31 HEADER PARSE\n");
        result.append("========================================\n\n");

        result.append("HEADER FIELDS:\n");
        result.append("------------------\n");
        result.append("Version ID:        ").append(header.versionId).append("\n");
        result.append("Key Block Length:  ").append(header.keyBlockLength).append(" characters\n");
        result.append("Key Usage:         ").append(header.keyUsage);
        result.append(" (").append(TR31Operations.getKeyUsageDescription(header.keyUsage)).append(")\n");
        result.append("Algorithm:         ").append(header.algorithm);
        result.append(" (").append(TR31Operations.getAlgorithmDescription(header.algorithm.charAt(0)))
                .append(")\n");
        result.append("Mode of Use:       ").append(header.modeOfUse);
        result.append(" (").append(TR31Operations.getModeOfUseDescription(header.modeOfUse.charAt(0)))
                .append(")\n");
        result.append("Key Version:       ").append(header.keyVersionNumber).append("\n");
        result.append("Exportability:     ").append(header.exportability).append(" (")
                .append(TR31Operations.getExportabilityDescription(header.exportability.charAt(0))).append(")\n");
        result.append("Optional Blocks:   ").append(header.numOptionalBlocks).append("\n");
        result.append("Reserved:          ").append(header.reserved).append("\n\n");

        result.append("INPUT LENGTH:       ").append(keyBlock.length()).append(" characters\n");

        if (!header.optionalBlockDetails.isEmpty()) {
            result.append("OPTIONAL BLOCKS:\n");
            result.append("------------------\n");
            for (TR31Operations.OptionalBlock block : header.optionalBlockDetails) {
                result.append(block.id()).append(" (")
                        .append(TR31Operations.OPTIONAL_BLOCKS.getOrDefault(block.id(), "Unknown optional block"))
                        .append("): ").append(block.dataCharacters()).append(" characters\n");
                result.append("  Data: ").append(block.data()).append("\n");
                result.append("  ").append(text.apply("module.keys.tr31.decoded")).append(": ")
                        .append(TR31Operations.describeOptionalBlockData(block.id(), block.data())).append("\n");
            }
            result.append("\n");
        }

        result.append(text.apply("module.keys.tr31.headerWarnings").toUpperCase(java.util.Locale.ROOT)).append(":\n");
        result.append("------------------\n");
        if (header.getDiagnostics().isEmpty()) result.append("No structural warnings detected.\n\n");
        else {
            for (String diagnostic : header.getDiagnostics()) result.append(diagnostic).append("\n");
            result.append("\n");
        }

        result.append("RAW HEADER:\n");
        result.append("------------------\n");
        result.append(header.build()).append("\n");

        result.append("\n========================================\n");
        return new ParseResult(result.toString());
    }
}
