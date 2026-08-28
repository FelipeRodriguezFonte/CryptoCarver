package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.Algorithm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Exportability;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MaterialState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MkvpState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Scope;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.WrapMethod;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The formats and findings the other test classes do not reach.
 *
 * <p>Written after an audit found that four of the twenty-three findings had no
 * detection test, and that the variable-length and PKA parsers had no direct
 * coverage at all — they were only ever exercised through the "unrecognised
 * token" path, which proves nothing about them.</p>
 */
class IcsfCoverageTest {

    private static Set<FindingCode> findingsOf(byte[] token) {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(token),
                BatchInputFormat.LINE, Origin.INFER);
        return report.items().get(0).findings().stream()
                .map(Finding::code).collect(Collectors.toSet());
    }

    // =====================================================================
    // Variable-length symmetric tokens (Table 618)
    // =====================================================================
    @Test
    void parsesAVariableLengthAesCipherToken() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.variableLength(false, false));

        assertTrue(result.isOk(), result.error());
        assertEquals(TokenFamily.SYM_VARIABLE, result.tokenFamily());
        assertTrue(result.is(SummaryKey.SCOPE, Scope.INTERNAL));
        assertTrue(result.is(SummaryKey.ALGORITHM, Algorithm.AES));
        assertEquals("CIPHER", result.code(SummaryKey.KEY_TYPE, ""));
        assertEquals("256", result.code(SummaryKey.KEY_LENGTH, ""));
        assertTrue(result.is(SummaryKey.WRAPPING, WrapMethod.AESKW));
        assertTrue(result.is(SummaryKey.MATERIAL_STATE, MaterialState.ENCRYPTED));
        assertTrue(result.is(SummaryKey.MKVP, MkvpState.PRESENT));
        // This format carries no TVV and no Control Vector at all.
        assertTrue(result.is(SummaryKey.TVV, TvvState.NOT_APPLICABLE));
        assertTrue(result.is(SummaryKey.CONTROL_VECTOR, IcsfVocabulary.CvState.NOT_APPLICABLE));
    }

    @Test
    void aWellFormedVariableLengthTokenRaisesNoParserWarning() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.variableLength(false, false));

        // If the fixture's associated-data arithmetic were wrong the parser would say so,
        // which would make every other assertion here worthless.
        assertTrue(result.warnings().isEmpty(),
                "unexpected warnings: " + result.warnings());
    }

    @Test
    void readsTheUsageFieldsExportabilityAndPedigree() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.variableLength(false, false));

        assertTrue(result.is(SummaryKey.EXPORTABILITY, Exportability.YES));
        assertTrue(result.value(SummaryKey.EXPORTABILITY).orElseThrow().text().contains("symmetric key"));
        assertEquals("PRESENT", result.code(SummaryKey.PEDIGREE, ""));
        assertTrue(result.value(SummaryKey.PEDIGREE).orElseThrow().text().contains("Randomly generated"));
        assertTrue(result.value(SummaryKey.ALLOWED_USES).orElseThrow().text().contains("encryption"));
    }

    @Test
    void detectsADegradedSecurityHistory() {
        assertTrue(findingsOf(IcsfTestTokens.variableLength(true, false))
                .contains(FindingCode.HISTORIA_DEBIL));
        assertFalse(findingsOf(IcsfTestTokens.variableLength(false, false))
                .contains(FindingCode.HISTORIA_DEBIL));
    }

    @Test
    void detectsACompliantTaggedVariableLengthKey() {
        assertTrue(findingsOf(IcsfTestTokens.variableLength(false, true))
                .contains(FindingCode.COMP_TAG));
        assertFalse(findingsOf(IcsfTestTokens.variableLength(false, false))
                .contains(FindingCode.COMP_TAG));
    }

    @Test
    void detectsATruncatedVariableLengthTokenWithoutThrowing() {
        byte[] token = IcsfTestTokens.variableLength(false, false);
        byte[] truncated = Arrays.copyOf(token, 50);

        ParseResult result = IcsfTokenParser.parse(truncated);

        // It still reads what it can and says what does not add up, rather than crashing.
        assertTrue(result.isOk() || result.error() != null);
        if (result.isOk()) {
            assertTrue(result.warned(DiagnosticCode.DECLARED_LENGTH_MISMATCH)
                    || result.warned(DiagnosticCode.ASSOCIATED_DATA_OVERFLOW)
                    || result.warned(DiagnosticCode.MANAGEMENT_FIELDS_TRUNCATED));
        }
    }

    // =====================================================================
    // PKA tokens (Tables 637-646)
    // =====================================================================
    @Test
    void parsesAPkaPublicRsaToken() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.pkaPublicRsa());

        assertTrue(result.isOk(), result.error());
        assertEquals(TokenFamily.PKA, result.tokenFamily());
        assertTrue(result.is(SummaryKey.SCOPE, Scope.INTERNAL));
        assertTrue(result.is(SummaryKey.ALGORITHM, Algorithm.RSA));
        assertEquals("PUBLIC_ONLY", result.code(SummaryKey.KEY_TYPE, ""));
        assertEquals("RSA 1024 bits", result.code(SummaryKey.KEY_LENGTH, ""));
        assertEquals("NO", result.code(SummaryKey.PRIVATE_KEY_PRESENT, ""));
        // With no private material there is nothing to protect or to export.
        assertTrue(result.is(SummaryKey.MATERIAL_STATE, MaterialState.NO_KEY));
        assertTrue(result.is(SummaryKey.EXPORTABILITY, Exportability.NOT_APPLICABLE));
        assertTrue(result.warnings().isEmpty(), "unexpected warnings: " + result.warnings());
    }

    @Test
    void thePkaSectionWalkReportsWhatItFound() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.pkaPublicRsa());

        assertTrue(result.value(SummaryKey.SECTIONS).orElseThrow().text().contains("X'04'"));
        assertTrue(result.value(SummaryKey.SECTIONS).orElseThrow().text().contains("RSA public key"));
        // The public exponent is decoded, not just dumped.
        boolean exponentDecoded = result.sections().stream()
                .flatMap(section -> section.fields().stream())
                .anyMatch(field -> IcsfMessages.resolve(field.value()).contains("65537"));
        assertTrue(exponentDecoded, "the public exponent must be read, not just shown as hex");
    }

    @Test
    void aPkaTokenAlwaysCarriesTheInTestingCaveat() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.pkaPublicRsa());

        assertEquals("IN_TESTING", result.code(SummaryKey.MATURITY, ""));
        assertTrue(result.value(SummaryKey.MATURITY).orElseThrow().text().contains("not yet been validated against a real token"));
        assertTrue(findingsOf(IcsfTestTokens.pkaPublicRsa()).contains(FindingCode.PKA_EN_PRUEBAS));
    }

    @Test
    void aPkaSectionThatDoesNotFitStopsTheWalkInsteadOfRunningOff() {
        byte[] token = IcsfTestTokens.pkaPublicRsa();
        token[10] = (byte) 0xFF;                 // section length far past the token
        token[11] = (byte) 0xFF;

        ParseResult result = IcsfTokenParser.parse(token);

        assertTrue(result.isOk());
        assertTrue(result.warned(DiagnosticCode.PKA_SECTION_LENGTH_INVALID));
    }

    // =====================================================================
    // The remaining findings
    // =====================================================================
    @Test
    void detectsAnInternalEncryptedTokenWithNoMkvp() {
        Set<FindingCode> findings = findingsOf(IcsfTestTokens.unmaterialisedInternalDes());

        assertTrue(findings.contains(FindingCode.MKVP_AUSENTE));
        // The same token also has no TVV, and that is the matching state, not a second fault.
        assertTrue(findings.contains(FindingCode.TVV_AUSENTE));
        assertFalse(findings.contains(FindingCode.TVV_INVALIDO));
    }

    @Test
    void doesNotClaimAMissingMkvpOnAnExternalToken() {
        // An external token has no MKVP by definition, so the finding must not fire.
        assertFalse(findingsOf(IcsfTestTokens.des("IMPORTER", 16))
                .contains(FindingCode.MKVP_AUSENTE));
    }

    @Test
    void detectsAFixedLengthTokenThatIsNotSixtyFourBytes() {
        Set<FindingCode> findings = findingsOf(IcsfTestTokens.truncatedDes());

        assertTrue(findings.contains(FindingCode.LONGITUD_INESPERADA));
        assertFalse(findingsOf(IcsfTestTokens.des("IMPORTER", 16))
                .contains(FindingCode.LONGITUD_INESPERADA));
    }

    // =====================================================================
    // The catalogue as a whole
    // =====================================================================
    @Test
    void everyFindingInTheCatalogueIsReachableFromSomeToken() {
        List<byte[]> corpus = List.of(
                IcsfTestTokens.des("IMPORTER", 16),                                   // WRAP-ECB
                IcsfTestTokens.des("IMPORTER", 24),                                   // ENH-ONLY
                IcsfTestTokens.des("EXPORTER", 16, null, true, null),                 // NO-EXPORTABLE
                IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8),                   // DES-56-BITS, CV-CERO
                IcsfTestTokens.des("IMPORTER", 16, 0x40, false, null),                // BYTE59-FUERA-DE-TABLA
                IcsfTestTokens.aesFixed(),                                            // MATERIAL-EN-CLARO
                IcsfTestTokens.unmaterialisedInternalDes(),                           // MKVP/TVV-AUSENTE
                IcsfTestTokens.truncatedDes(),                                        // LONGITUD-INESPERADA
                IcsfTestTokens.variableLength(true, true),                            // HISTORIA-DEBIL, COMP-TAG
                IcsfTestTokens.pkaPublicRsa(),                                        // PKA-EN-PRUEBAS
                collapsedTriple(),                                                    // DES-FUERZA-SIMPLE
                effectivelyDoubleTriple(),                                            // DES-FUERZA-DOBLE
                enhancedWrapped(),                                                    // WRAP-MEJORADO
                nocvToken(),                                                          // NOCV
                brokenControlVector(),                                                // CV-INVALIDO
                wrongTvv(),                                                           // TVV-INVALIDO
                byte59VersionMismatch());                                             // BYTE59-INCOHERENTE

        StringBuilder input = new StringBuilder();
        for (byte[] token : corpus) input.append(IcsfTestTokens.hex(token)).append('\n');
        input.append(IcsfTestTokens.hex(corpus.get(0))).append('\n');   // DUPLICADO
        input.append("NOT-HEX\n");                                      // ENTRADA-NO-RECONOCIDA

        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(input.toString(),
                BatchInputFormat.LINE, Origin.INFER);

        Set<FindingCode> raised = report.findings().stream()
                .map(IcsfBatchReport.AggregatedFinding::code)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FindingCode.class)));
        Set<FindingCode> missing = EnumSet.allOf(FindingCode.class);
        missing.removeAll(raised);

        assertTrue(missing.isEmpty(),
                "findings in the catalogue that no token in the corpus can raise: " + missing);
        assertEquals(FindingCode.values().length, raised.size());
    }

    @Test
    void theCatalogueHasTheTwentyThreeCodesInTheirThreeSeverities() {
        assertEquals(23, FindingCode.values().length);
        assertEquals(6, count(FindingCode.Severity.HIGH));
        assertEquals(11, count(FindingCode.Severity.MEDIUM));
        assertEquals(6, count(FindingCode.Severity.INFO));
    }

    private static long count(FindingCode.Severity severity) {
        return Arrays.stream(FindingCode.values())
                .filter(code -> code.severity() == severity).count();
    }

    // --- corpus builders --------------------------------------------------
    private static byte[] collapsedTriple() {
        byte[] material = new byte[24];
        Arrays.fill(material, (byte) 0xCC);
        return IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, material);
    }

    private static byte[] effectivelyDoubleTriple() {
        byte[] material = new byte[24];
        Arrays.fill(material, 0, 8, (byte) 0xAA);
        Arrays.fill(material, 8, 16, (byte) 0xBB);
        Arrays.fill(material, 16, 24, (byte) 0xAA);
        return IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, material);
    }

    private static byte[] enhancedWrapped() {
        return IcsfTestTokens.externalDes(new byte[16],
                IcsfTestTokens.hex("00427D0003410000"), IcsfTestTokens.hex("00427D0003210000"),
                false, 2, false);
    }

    private static byte[] nocvToken() {
        return IcsfTestTokens.externalDes(new byte[16],
                IcsfTestTokens.hex("00427D0003410000"), IcsfTestTokens.hex("00427D0003210000"),
                true, 0, false);
    }

    private static byte[] brokenControlVector() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        token[32] = 0x01;
        IcsfTestTokens.writeTvv(token);
        return token;
    }

    private static byte[] wrongTvv() {
        byte[] token = IcsfTestTokens.des("MAC", 16);
        token[16] ^= 0xFF;
        return token;
    }

    private static byte[] byte59VersionMismatch() {
        byte[] token = IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 16);
        token[4] = 0x00;
        IcsfTestTokens.writeTvv(token);
        return token;
    }
}
