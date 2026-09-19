package com.cryptocarver.crypto;

import com.cryptocarver.crypto.ThalesKeyBlockOperations.Finding;
import com.cryptocarver.crypto.ThalesKeyBlockOperations.Header;
import com.cryptocarver.crypto.ThalesKeyBlockOperations.KeyBlock;
import com.cryptocarver.crypto.ThalesKeyBlockOperations.KeyUsage;
import com.cryptocarver.crypto.ThalesKeyBlockOperations.VersionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checked against chapter 8 of the payShield 10K Host Programmer's Manual, and
 * in particular the example header it works through in clause 8.5.1.8.
 */
class ThalesKeyBlockOperationsTest {

    /** Clause 8.5.1.8, the manual's own example header. */
    private static final String MANUAL_HEADER = "00072V2TG22N0033";

    private static boolean has(List<Finding> findings, String severity, String fragment) {
        return findings.stream()
                .anyMatch(f -> severity.equals(f.severity()) && f.message().contains(fragment));
    }

    /** Pads a block out to the length its header declares, with plausible key data. */
    private static String blockOf(String header, int keyDataBytes, String authenticator) {
        return header + "A".repeat(keyDataBytes * 2) + authenticator;
    }

    // =====================================================================
    // The manual's worked header
    // =====================================================================

    @Test
    void theManualsExampleHeaderReadsAsTheManualExplainsIt() {
        // Clause 8.5.1.8 spells out every field of this header in prose.
        // 16 header + 48 key data characters + 8 authenticator = 72, its declared length.
        KeyBlock block = ThalesKeyBlockOperations.parse(blockOf(MANUAL_HEADER, 24, "0123ABCD"));
        Header header = block.header();

        assertEquals('0', header.versionId());
        assertEquals(72, header.declaredLength());
        assertEquals("V2", header.keyUsage());
        assertEquals('T', header.algorithm());
        assertEquals('G', header.modeOfUse());
        assertEquals("22", header.keyVersionNumber());
        assertEquals('N', header.exportability());
        assertEquals(0, header.optionalBlockCount());
        assertEquals("33", header.lmkId());
    }

    @Test
    void theManualsExampleUsesAnLmkIdItsOwnPageForbids() {
        // Clause 8.5.1.8 defines the LMK ID as '00' to '19', then gives an
        // example using '33' four paragraphs later. Warned about rather than
        // rejected, because blocks shaped like the example evidently exist.
        KeyBlock block = ThalesKeyBlockOperations.parse(blockOf(MANUAL_HEADER, 24, "0123ABCD"));

        assertTrue(has(block.findings(), "WARNING", "outside the range '00' to '19'"));
        assertTrue(block.wellFormed(), "An out-of-range LMK ID is not a reason to call the block broken");
    }

    @Test
    void theSchemeTagIsAcceptedAndIsNotPartOfTheLength() {
        // Clause 8.5.1.1 counts from the header; clause 8.7 keeps the tag out
        // of the authenticated data. So 'S' is not part of the block proper.
        String body = blockOf(MANUAL_HEADER, 24, "0123ABCD");

        KeyBlock tagged = ThalesKeyBlockOperations.parse("S" + body);
        KeyBlock bare = ThalesKeyBlockOperations.parse(body);

        assertEquals(bare.raw(), tagged.raw());
        assertTrue(tagged.wellFormed(), ThalesKeyBlockOperations.describe(tagged));
    }

    @Test
    void aLengthThatDisagreesWithTheBlockIsReported() {
        KeyBlock block = ThalesKeyBlockOperations.parse(blockOf(MANUAL_HEADER, 16, "0123ABCD"));

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "The header declares a length of 72"));
    }

    // =====================================================================
    // The version ID, which is where a TR-31 reader gives up
    // =====================================================================

    @Test
    void aTr31VersionIdIsNamedRatherThanCalledGarbage() {
        // Someone pasting an X9.143 block here should be told which tool to use.
        KeyBlock block = ThalesKeyBlockOperations.parse("D0072V2TG22N0003"
                + "A".repeat(48) + "0123ABCD");

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "ANSI X9.143 / TR-31 version ID"));
    }

    @Test
    void theVersionSaysHowLongTheAuthenticatorIs() {
        // Clause 8.7: 4 bytes under a 3DES LMK, 8 under an AES one.
        assertEquals(8, VersionId.DES.authenticatorCharacters());
        assertEquals(16, VersionId.AES.authenticatorCharacters());
        assertEquals(8, VersionId.DES.blockBytes());
        assertEquals(16, VersionId.AES.blockBytes());
    }

    @Test
    void anAesBlockNeedsTheLongerAuthenticator() {
        // Same shape, one character short of the 16 an AES block needs.
        KeyBlock block = ThalesKeyBlockOperations.parse("10072V2AG22N0003"
                + "A".repeat(48) + "0123ABCD");

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "not a multiple of the 16-byte cipher block")
                || has(block.findings(), "ERROR", "The header declares a length"));
    }

    // =====================================================================
    // The key usage table, which is the point of the whole thing
    // =====================================================================

    @Test
    void theUsageTableRestrictsAlgorithmsPerUsage() {
        // Clause 8.5.1.2 allows only 'T' for BDK-3. An AES BDK-3 is malformed,
        // and a parser that only reads names would never say so.
        KeyBlock block = ThalesKeyBlockOperations.parse("00072" + "42" + "A" + "N" + "00" + "N" + "00" + "03"
                + "A".repeat(48) + "0123ABCD");

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "does not permit algorithm 'A'"));
    }

    @Test
    void theUsageTableRestrictsModesPerAlgorithm() {
        // A Visa PVV key that claims it may encrypt. Clause 8.5.1.2 allows
        // C, G, N and V there and nothing else.
        KeyBlock block = ThalesKeyBlockOperations.parse("00072V2TE22N0003"
                + "A".repeat(48) + "0123ABCD");

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "does not permit mode of use 'E'"));
    }

    @Test
    void aUsageWithNoAnsiEquivalentSaysSo() {
        // Clause 8.5.1.2 note 2: MKPOS/MKSER is regional and not in X9.143.
        KeyUsage italian = ThalesKeyBlockOperations.keyUsage("57");
        assertNull(italian.ansiEquivalent());

        KeyBlock block = ThalesKeyBlockOperations.parse("00072" + "57" + "T" + "N" + "00" + "N" + "00" + "03"
                + "A".repeat(48) + "0123ABCD");
        assertTrue(has(block.findings(), "INFO", "no ANSI X9.143 equivalent"));
    }

    @Test
    void aUsageThatConvertsOnExportSaysWhatItConvertsTo() {
        // '13' is a Visa CVV key and becomes the generic 'C0' in X9.143.
        assertEquals("C0", ThalesKeyBlockOperations.keyUsage("13").ansiEquivalent());

        KeyBlock block = ThalesKeyBlockOperations.parse("00072" + "13" + "T" + "V" + "00" + "N" + "00" + "03"
                + "A".repeat(48) + "0123ABCD");
        assertTrue(has(block.findings(), "INFO", "converts this usage to 'C0'"));
    }

    @Test
    void theTableCarriesTheUsagesTheManualLists() {
        assertEquals("Zone PIN Encryption Key (ZPK)", ThalesKeyBlockOperations.keyUsage("72").description());
        assertEquals("Terminal PIN Encryption Key (TPK)", ThalesKeyBlockOperations.keyUsage("71").description());
        assertEquals("Base Derivation Key (BDK-1)", ThalesKeyBlockOperations.keyUsage("B0").description());
        assertEquals("Key Block Protection Key", ThalesKeyBlockOperations.keyUsage("K1").description());
        assertEquals("PIN Verification Key (Visa PVV)", ThalesKeyBlockOperations.keyUsage("V2").description());
        assertTrue(ThalesKeyBlockOperations.keyUsages().size() > 60,
                "The manual's table is long; a short one means rows were dropped");
    }

    @Test
    void anUnknownUsageIsAWarningAndNotAFailure() {
        // Thales adds codes over time. Refusing an unseen one would make the
        // tool wrong every time the manual is revised.
        KeyBlock block = ThalesKeyBlockOperations.parse("00072" + "ZZ" + "T" + "N" + "00" + "N" + "00" + "03"
                + "A".repeat(48) + "0123ABCD");

        assertTrue(has(block.findings(), "WARNING", "not in the Thales key usage table"));
        assertTrue(block.wellFormed());
    }

    // =====================================================================
    // Key version number, exportability
    // =====================================================================

    @Test
    void aKeyVersionOfCMeansAComponent() {
        // Clause 8.5.1.5: byte 9 'c', byte 10 the component number.
        KeyBlock block = ThalesKeyBlockOperations.parse("00072V2TN" + "c2" + "N0003"
                + "A".repeat(48) + "0123ABCD");

        assertTrue(block.header().isComponent());
        assertEquals("2", block.header().componentNumber());
        assertTrue(has(block.findings(), "INFO", "does not say how many components"));
    }

    @Test
    void exportabilityIsExplainedRatherThanEchoed() {
        KeyBlock block = ThalesKeyBlockOperations.parse(blockOf(MANUAL_HEADER, 24, "0123ABCD"));
        assertTrue(has(block.findings(), "INFO", "no export permitted"));

        KeyBlock other = ThalesKeyBlockOperations.parse("00072V2TG22Q0003"
                + "A".repeat(48) + "0123ABCD");
        assertTrue(has(other.findings(), "ERROR", "Exportability 'Q'"));
    }

    // =====================================================================
    // Optional blocks
    // =====================================================================

    @Test
    void anOptionalBlockLengthIsHexadecimalNotDecimal() {
        // Clause 8.5.2 writes the length in hexadecimal: a 24-byte block is
        // '18'. Reading it as decimal is the classic way to lose a block.
        String optional = ThalesKeyBlockOperations.buildOptionalBlock("05", "HELLO WORLD");
        assertEquals("05", optional.substring(0, 2));
        assertEquals("0F", optional.substring(2, 4), "4 + 11 = 15, written as 0F");
    }

    @Test
    void aKeyStatusBlockIsCheckedAgainstItsPermittedValues() {
        String optional = ThalesKeyBlockOperations.buildOptionalBlock("00", "L");
        String header = "0" + "0037" + "V2TG22N" + "01" + "03";
        KeyBlock block = ThalesKeyBlockOperations.parse(header + optional + "A".repeat(16) + "0123ABCD");

        assertTrue(has(block.findings(), "INFO", "Key status: Live"), block.findings().toString());

        String bad = ThalesKeyBlockOperations.buildOptionalBlock("00", "Z");
        KeyBlock broken = ThalesKeyBlockOperations.parse(header + bad + "A".repeat(16) + "0123ABCD");
        assertTrue(has(broken.findings(), "ERROR", "Key status 'Z'"));
    }

    @Test
    void aRepeatedOptionalBlockIsTheErrorTheHsmReturns() {
        String twice = ThalesKeyBlockOperations.buildOptionalBlock("00", "L")
                + ThalesKeyBlockOperations.buildOptionalBlock("00", "P");
        String header = "0" + "0042" + "V2TG22N" + "02" + "03";
        KeyBlock block = ThalesKeyBlockOperations.parse(header + twice + "A".repeat(16) + "0123ABCD");

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "BC — Repeated optional block"));
    }

    @Test
    void thePaddingBlockMustComeLast() {
        String wrongOrder = ThalesKeyBlockOperations.buildOptionalBlock("PB", "XX")
                + ThalesKeyBlockOperations.buildOptionalBlock("00", "L");
        String header = "0" + "0043" + "V2TG22N" + "02" + "03";
        KeyBlock block = ThalesKeyBlockOperations.parse(header + wrongOrder + "A".repeat(16) + "0123ABCD");

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "must be the last optional block"));
    }

    @Test
    void aDateBlockMustBeTheFormatTheManualGives() {
        String good = ThalesKeyBlockOperations.buildOptionalBlock("03", "2026:09:19:14");
        String header = "0" + "0049" + "V2TG22N" + "01" + "03";
        KeyBlock block = ThalesKeyBlockOperations.parse(header + good + "A".repeat(16) + "0123ABCD");
        assertFalse(has(block.findings(), "ERROR", "YYYY:MM:DD:HH"));

        String bad = ThalesKeyBlockOperations.buildOptionalBlock("03", "19/09/2026:14");
        KeyBlock broken = ThalesKeyBlockOperations.parse(header + bad + "A".repeat(16) + "0123ABCD");
        assertTrue(has(broken.findings(), "ERROR", "YYYY:MM:DD:HH"));
    }

    @Test
    void theOptionalBlocksMustPadToTheCipherBlock() {
        // Clause 8.5.2: their total is a multiple of 8 under a 3DES LMK, and a
        // 'PB' block last is how you get there.
        String five = ThalesKeyBlockOperations.buildOptionalBlock("00", "L");
        String header = "0" + "0037" + "V2TG22N" + "01" + "03";
        KeyBlock block = ThalesKeyBlockOperations.parse(header + five + "A".repeat(16) + "0123ABCD");

        assertFalse(block.wellFormed());
        assertTrue(has(block.findings(), "ERROR", "not a multiple of 8"));
    }

    // =====================================================================
    // Building
    // =====================================================================

    @Test
    void buildingReproducesTheManualsHeaderApartFromItsOwnLmkId() {
        // '33' is what the manual prints and what its field definition forbids,
        // so the builder is asked for a legal '03' and everything else matches.
        String header = ThalesKeyBlockOperations.buildHeader(VersionId.DES, "V2", 'T', 'G',
                "22", 'N', 0, "03", 56);

        assertEquals("00072V2TG22N0003", header);
        assertEquals(MANUAL_HEADER.substring(0, 14), header.substring(0, 14));
    }

    @Test
    void buildingRefusesACombinationTheTableForbids() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ThalesKeyBlockOperations.buildHeader(VersionId.DES, "42", 'A', 'N',
                        "00", 'N', 0, "03", 56));
        assertTrue(thrown.getMessage().contains("permits algorithms"), thrown.getMessage());
    }

    @Test
    void buildingRefusesAModeTheUsageForbids() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ThalesKeyBlockOperations.buildHeader(VersionId.DES, "V2", 'T', 'E',
                        "00", 'N', 0, "03", 56));
        assertTrue(thrown.getMessage().contains("permits modes"), thrown.getMessage());
    }

    @Test
    void whatIsBuiltParsesBackCleanly() {
        String optional = ThalesKeyBlockOperations.buildOptionalBlock("05", "LAB KEY ")
                + ThalesKeyBlockOperations.buildOptionalBlock("PB", "XXXXXXXX");
        String header = ThalesKeyBlockOperations.buildHeader(VersionId.DES, "72", 'T', 'D',
                "01", 'E', 2, "07", optional.length() + 32 + 8);

        KeyBlock block = ThalesKeyBlockOperations.parse(
                header + optional + "A".repeat(32) + "0123ABCD");

        assertTrue(block.wellFormed(), ThalesKeyBlockOperations.describe(block));
        assertEquals(2, block.optionalBlocks().size());
        assertEquals("LAB KEY ", block.optionalBlocks().get(0).data());
    }

    // =====================================================================
    // Reporting and inputs
    // =====================================================================

    @Test
    void describeSaysWhatItReadAndWhatItWouldNeedToReadMore() {
        // A report that stops at the header without saying so reads like a
        // tool that failed halfway. It has to name what is missing: the LMK.
        KeyBlock block = ThalesKeyBlockOperations.parse(blockOf(MANUAL_HEADER, 24, "0123ABCD"));

        String report = ThalesKeyBlockOperations.describe(block);

        assertTrue(report.contains("PIN Verification Key (Visa PVV)"), report);
        assertTrue(report.contains("WELL FORMED"), report);
        assertTrue(report.contains("clear parts only"), report);
        assertTrue(report.contains("Supply the Key Block LMK"), report);
    }

    @Test
    void aSpaceInsideATextBlockSurvivesTheParse() {
        // Clause 8.5.2.1 allows any printable character in a text block, and a
        // space is one. Stripping whitespace to tidy a pasted block shortens it
        // and shifts every field after it — which is how this was found.
        String optional = ThalesKeyBlockOperations.buildOptionalBlock("05", "LAB KEY ")
                + ThalesKeyBlockOperations.buildOptionalBlock("PB", "XXXXXXXX");
        String header = ThalesKeyBlockOperations.buildHeader(VersionId.DES, "72", 'T', 'D',
                "01", 'E', 2, "07", optional.length() + 32 + 8);

        KeyBlock block = ThalesKeyBlockOperations.parse(header + optional + "A".repeat(32) + "0123ABCD");

        assertEquals("LAB KEY ", block.optionalBlocks().get(0).data());
    }

    @Test
    void aBlockPastedAcrossSeveralLinesStillReads() {
        String body = blockOf(MANUAL_HEADER, 24, "0123ABCD");
        String wrapped = "S" + body.substring(0, 30) + "\n" + body.substring(30, 50) + "\r\n"
                + body.substring(50);

        KeyBlock block = ThalesKeyBlockOperations.parse(wrapped);

        assertEquals(body, block.raw());
    }

    @Test
    void anEmptyInputIsRefusedPlainly() {
        assertThrows(IllegalArgumentException.class, () -> ThalesKeyBlockOperations.parse("  "));
    }

    @Test
    void somethingShorterThanAHeaderIsRefusedWithTheLength() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> ThalesKeyBlockOperations.parse("S0007"));
        assertTrue(thrown.getMessage().contains("16 characters"), thrown.getMessage());
    }
}
