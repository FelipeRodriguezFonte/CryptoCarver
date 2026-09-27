package com.cryptocarver.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TR31OperationsTest {

    @Test
    void reproducesPublicTr31ExamplesForAllFourVersions() throws Exception {
        // Public solved vectors from TR-31:2018 Annex A, published in
        // OpenEMV tr31 (LGPL-2.1), test/tr31_decrypt_test.c. Its LGPL-compatible
        // data is used as an independent unwrap oracle. The existing writer is
        // also exercised with each vector's attributes and must round-trip.
        String[] protectionKeys = {"89E88CF7931444F334BD7547FC3F380C", "DD7515F2BFC17F85CE48F3CA25CB21F6",
                "B8ED59E0A279A295E9F5ED7944FD06B9", "88E1AB2A2E3DD38C1FA039A536500CC8A87AB9D62DC92C01058FA79F44657DE6"};
        String[] blocks = {
                "A0072P0TE00E0000F5161ED902807AF26F1D62263644BD24192FDB3193C730301CEE8701",
                "B0080P0TE00E000094B420079CC80BA3461F86FE26EFC4A3B8E4FA4C5F5341176EED7B727B8A248E",
                "C0096B0TX12S0100KS1800604B120F9292800000BFB9B689CB567E66FC3FEE5AD5F52161FC6545B9D60989015D02155C",
                "D0112P0AE00E0000B82679114F470F540165EDFBF7E250FCEA43F810D215F8D207E2E417C07156A27E8E31DA05F7425509593D03A457DC34"};
        String[] keys = {"F039121BEC83D26B169BDCD5B22AAF8F", "3F419E1CB7079442AA37474C2EFBF8B8",
                "EDB380DD340BC2620247D445F5B8D678", "3F419E1CB7079442AA37474C2EFBF8B8"};
        String[] usages = {"P0", "P0", "B0", "P0"};
        char[] versions = {'A', 'B', 'C', 'D'};
        char[] algorithms = {'T', 'T', 'T', 'A'};
        char[] modes = {'E', 'E', 'X', 'E'};
        char[] exports = {'E', 'E', 'S', 'E'};
        String[] options = {"", "", "0100KS1800604B120F9292800000", ""};
        for (int i = 0; i < versions.length; i++) {
            assertEquals(keys[i], TR31Operations.unwrapKey(protectionKeys[i], blocks[i]));
            String wrapped = TR31Operations.wrapKey(protectionKeys[i], keys[i], usages[i], versions[i], algorithms[i], modes[i], exports[i], options[i]);
            assertEquals(keys[i], TR31Operations.unwrapKey(protectionKeys[i], wrapped));
        }
    }

    @Test
    void optionalBlockTablesIncludeDocumentedIdentifiersAndNewUsageAssignments() {
        assertTrue(TR31Operations.KEY_USAGES.keySet().containsAll(java.util.List.of("B2", "B3", "D2", "E6", "K3", "M8", "P1", "S2", "V4")));
        assertTrue(TR31Operations.OPTIONAL_BLOCKS.keySet().containsAll(java.util.List.of("CT", "HM", "IK", "KC", "KP", "KS", "KV", "LB", "PB", "TS", "WP", "DA", "BI")));
        assertThrows(IllegalArgumentException.class, () -> TR31Operations.normalizeOptionalBlocks("0100HM0200"));
        assertThrows(IllegalArgumentException.class, () -> TR31Operations.normalizeOptionalBlocks("0100TS04ABCD"));
    }

    @Test
    void everyDocumentedOptionalIdentifierNormalizesAsACompactRoundTrip() {
        TR31Operations.OPTIONAL_BLOCKS.keySet().forEach(id -> {
            String data = switch (id) {
                case "AL" -> "0100";
                case "BI" -> "001122334455";
                case "CT" -> "00QUJD";
                case "DA" -> "01B0TEE";
                case "FL" -> "flag value";
                case "HM" -> "21";
                case "IK" -> "0011223344556677";
                case "KC", "KP", "PK" -> "00000000";
                case "KS" -> "00112233445566778899";
                case "KV" -> "v1XY";
                case "LB" -> "Label With Spaces";
                case "PB" -> "padding  ";
                case "TS", "TC" -> "2026-09-27T10:00:00Z";
                case "WP" -> "000";
                default -> throw new AssertionError("Missing fixture for " + id);
            };
            String encoded = "0100" + id + String.format("%02X", data.length() + 4) + data;
            assertEquals(encoded, TR31Operations.normalizeOptionalBlocks(encoded), id);
        });
    }

    @Test
    void parsesOptionalBlocksIntoStructuredDetails() throws Exception {
        TR31Operations.TR31Header header = TR31Operations.TR31Header.parse("B0024P0TE00E0100KS08ABCD");

        assertEquals("B", header.versionId);
        assertEquals(1, header.optionalBlockDetails.size());
        TR31Operations.OptionalBlock block = header.optionalBlockDetails.get(0);
        assertEquals("KS", block.id());
        assertEquals(4, block.dataCharacters());
        assertEquals("ABCD", block.data());
        assertTrue(TR31Operations.parseHeader("B0024P0TE00E0100KS08ABCD").contains("KS: 4 characters"));
        assertTrue(TR31Operations.parseHeaderJson("B0024P0TE00E0100KS08ABCD")
                .contains("TDEA DUKPT initial key serial number"));
    }

    @Test
    void rejectsTruncatedOptionalBlock() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TR31Operations.TR31Header.parse("B0024P0TE00E0100KS02AB"));

        assertTrue(error.getMessage().contains("truncated"));
    }

    @Test
    void rejectsUnsupportedVersion() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TR31Operations.TR31Header.parse("Z0016P0TE00E0000"));

        assertTrue(error.getMessage().contains("Unsupported TR-31 version"));
    }

    @Test
    void reportsNonFatalHeaderDiagnosticsWithoutPreventingInspection() throws Exception {
        TR31Operations.TR31Header header = TR31Operations.TR31Header.parse("A0016P0TE00E0000EXTRA");

        assertTrue(header.getDiagnostics().stream().anyMatch(message -> message.contains("trailing")));
        assertTrue(header.getDiagnostics().stream().anyMatch(message -> message.contains("legacy")));
    }

    @Test
    void authenticatesAndRecoversKeyBlocksContainingOptionalBlocks() throws Exception {
        String kbpk = "0123456789ABCDEFFEDCBA9876543210";
        String key = "00112233445566778899AABBCCDDEEFF";
        String header = new HeaderBuilder()
                .version('B').keyUsage("P0").algorithm('T').modeOfUse('E').exportability('N')
                .optionalBlocks("0100KS08ABCD").build();

        TR31 tr31 = new TR31(kbpk);
        String block = tr31.wrap(header, key);

        assertEquals(block.length(), Integer.parseInt(block.substring(1, 5)));
        assertEquals(1, TR31Operations.TR31Header.parse(block).optionalBlockDetails.size());
        assertEquals(key, TR31Operations.unwrapKey(kbpk, block));
    }

    @Test
    void validatesCompactOptionalBlocksBeforeWrapping() {
        assertEquals("0100KS08ABCD", TR31Operations.normalizeOptionalBlocks("01 00 KS08ABCD"));
        assertThrows(IllegalArgumentException.class, () -> TR31Operations.normalizeOptionalBlocks("0100KS02AB"));
    }

    @Test
    void preservesCaseAndSpacesInTextOptionalBlockData() throws Exception {
        String lb = "0100LB10Mixed Case  ";
        assertEquals(lb, TR31Operations.normalizeOptionalBlocks(lb));
        String wrapped = TR31Operations.wrapKey("0123456789ABCDEFFEDCBA9876543210",
                "00112233445566778899AABBCCDDEEFF", "P0", 'B', 'T', 'E', 'N', lb);
        assertTrue(wrapped.startsWith("B" + wrapped.substring(1, 5) + "P0TE00N" + lb));
        String ct = "0100CT0E00AbCd+/==";
        assertEquals(ct, TR31Operations.normalizeOptionalBlocks(ct));
        String timestamp = "0100TS18" + "2026-09-27T10:00:00Z";
        assertEquals(timestamp, TR31Operations.normalizeOptionalBlocks(timestamp));
    }

    @Test
    void rejectsMalformedOptionalDataByIdentifier() {
        for (String compact : java.util.List.of(
                "0100AL040102", "0100BI0600AB", "0100CT0600!@@@", "0100DA0701B0TEX",
                "0100IK0801020304", "0100KV040!@#", "0100LB02\u0001x", "0100WP030004")) {
            assertThrows(IllegalArgumentException.class, () -> TR31Operations.normalizeOptionalBlocks(compact), compact);
        }
    }

    @Test
    void warnsWhenParsingSyntheticLegacyKsFixture() throws Exception {
        TR31Operations.TR31Header header = TR31Operations.TR31Header.parse("B0024P0TE00E0100KS08ABCD");
        assertTrue(header.getDiagnostics().stream().anyMatch(message -> message.contains("legacy CryptoCarver KS")));
    }

    @Test
    void wrapRejectsEmptyOddAndNonHexClearKeys() {
        String kbpk = "0123456789ABCDEFFEDCBA9876543210";
        for (String key : java.util.List.of("", "0", "BADHEX", "0011ZZ")) {
            assertThrows(IllegalArgumentException.class,
                    () -> TR31Operations.wrapKey(kbpk, key, "P0", 'B', 'T', 'E', 'N'), key);
        }
    }

    @Test
    void validatesMatrixCombinations() {
        // Impossible: Symmetric TDES algorithm ('T') with Asymmetric Signature mode ('S')
        assertThrows(IllegalArgumentException.class, () -> TR31.validateMatrix('B', 'T', "P0", 'S', 'E'));

        // Impossible: Asymmetric RSA algorithm ('R') with Symmetric Derivation mode ('X')
        assertThrows(IllegalArgumentException.class, () -> TR31.validateMatrix('B', 'R', "P0", 'X', 'E'));

        // Impossible: AES algorithm ('A') using legacy TDES version ('B')
        assertThrows(IllegalArgumentException.class, () -> TR31.validateMatrix('B', 'A', "M3", 'G', 'E'));

        // Legacy warning on DUKPT usage but allowed
        // System.err will print the warning, but no exception thrown
        TR31.validateMatrix('B', 'T', "B1", 'E', 'E');

        // Valid combination
        TR31.validateMatrix('D', 'A', "M3", 'G', 'E');
    }
}
