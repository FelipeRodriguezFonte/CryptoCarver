package com.cryptoforge.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TR31OperationsTest {

    @Test
    void parsesOptionalBlocksIntoStructuredDetails() throws Exception {
        TR31Operations.TR31Header header = TR31Operations.TR31Header.parse("B0024P0TE00E0100KS02ABCD");

        assertEquals("B", header.versionId);
        assertEquals(1, header.optionalBlockDetails.size());
        TR31Operations.OptionalBlock block = header.optionalBlockDetails.get(0);
        assertEquals("KS", block.id());
        assertEquals(2, block.dataLength());
        assertEquals("ABCD", block.data());
        assertTrue(TR31Operations.parseHeader("B0024P0TE00E0100KS02ABCD").contains("KS: 2 bytes"));
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
                .optionalBlocks("0100KS02ABCD").build();

        TR31 tr31 = new TR31(kbpk);
        String block = tr31.wrap(header, key);

        assertEquals(block.length(), Integer.parseInt(block.substring(1, 5)));
        assertEquals(1, TR31Operations.TR31Header.parse(block).optionalBlockDetails.size());
        assertEquals(key, TR31Operations.unwrapKey(kbpk, block));
    }

    @Test
    void validatesCompactOptionalBlocksBeforeWrapping() {
        assertEquals("0100KS02ABCD", TR31Operations.normalizeOptionalBlocks("01 00 KS02ABCD"));
        assertThrows(IllegalArgumentException.class, () -> TR31Operations.normalizeOptionalBlocks("0100KS02AB"));
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
