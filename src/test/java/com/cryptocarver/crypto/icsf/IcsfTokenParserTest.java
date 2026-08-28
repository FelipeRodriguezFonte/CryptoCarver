package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.Algorithm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.CvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.DesKeyForm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.EffectiveStrength;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Exportability;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MaterialState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.MkvpState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Scope;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.WrapMethod;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The single-token analyser: every format, and the verdicts the batch layer will rely on. */
class IcsfTokenParserTest {

    // =====================================================================
    // Input reading
    // =====================================================================
    @Test
    void readsHexWithEverySeparatorAHostDumpProduces() {
        byte[] expected = {0x01, (byte) 0xAF, 0x02, 0x03};
        assertArrayEquals(expected, IcsfHex.clean("01AF0203"));
        assertArrayEquals(expected, IcsfHex.clean("01 AF 02 03"));
        assertArrayEquals(expected, IcsfHex.clean("0x01,0xAF,0x02,0x03"));
        assertArrayEquals(expected, IcsfHex.clean("01:AF:02:03"));
        assertArrayEquals(expected, IcsfHex.clean("01-AF-02-03"));
        assertArrayEquals(expected, IcsfHex.clean("01AF\n0203"));
        // A terminal emulator copy routinely yields U+00A0 rather than a plain space.
        assertArrayEquals(expected, IcsfHex.clean("01 AF 02 03"));
    }

    @Test
    void rejectsOddDigitCountAndNonHex() {
        IllegalArgumentException odd = assertThrows(IllegalArgumentException.class,
                () -> IcsfHex.clean("01A"));
        assertTrue(odd.getMessage().contains("odd"));
        assertThrows(IllegalArgumentException.class, () -> IcsfHex.clean("ZZZZ"));
    }

    @Test
    void deinterleavesTheTwoRowHostDump() {
        // Row 1 carries the high digit of each byte, row 2 the low one.
        assertArrayEquals(IcsfHex.clean("0A1F2C3D415263"),
                IcsfHex.deinterleaveTwoRows("0123456\nafcd123"));

        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        assertArrayEquals(token, IcsfHex.deinterleaveTwoRows(IcsfTestTokens.twoRows(token)));
    }

    @Test
    void twoRowModeRefusesAnythingButExactlyTwoEvenRows() {
        assertThrows(IllegalArgumentException.class, () -> IcsfHex.deinterleaveTwoRows("0123"));
        assertThrows(IllegalArgumentException.class,
                () -> IcsfHex.deinterleaveTwoRows("01\n23\n45"));
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> IcsfHex.deinterleaveTwoRows("0123\nab"));
        assertTrue(mismatch.getMessage().contains("different lengths"));
    }

    @Test
    void resolvesProvenanceValuesAndTheLegacyAliases() {
        assertEquals(Origin.RAW_KDS, Origin.fromValue("kds-crudo"));
        assertEquals(Origin.KRR, Origin.fromValue("key-record-read"));
        assertEquals(Origin.INFER, Origin.fromValue("inferir"));
        assertEquals(Origin.RAW_KDS, Origin.fromValue("ckds"));
        assertEquals(Origin.RAW_KDS, Origin.fromValue("pkds"));
        assertEquals(Origin.KRR, Origin.fromValue("csnbkrr"));
        assertEquals(Origin.KRR, Origin.fromValue("csndkrr"));
        assertEquals(Origin.INFER, Origin.fromValue("unknown"));
        assertThrows(IllegalArgumentException.class, () -> Origin.fromValue("nonsense"));
    }

    // =====================================================================
    // AES fixed-length (Table 614)
    // =====================================================================
    @Test
    void parsesAnAesFixedLengthToken() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.aesFixed());

        assertTrue(result.isOk());
        assertEquals(TokenFamily.SYM_FIXED_AES, result.tokenFamily());
        assertTrue(result.is(SummaryKey.SCOPE, Scope.INTERNAL));
        assertTrue(result.is(SummaryKey.ALGORITHM, Algorithm.AES));
        assertEquals("DATA", result.code(SummaryKey.KEY_TYPE, ""));
        assertEquals("128", result.code(SummaryKey.KEY_LENGTH, ""));
        assertTrue(result.is(SummaryKey.TVV, TvvState.VALID));
        assertTrue(result.is(SummaryKey.MKVP, MkvpState.ABSENT));
        assertTrue(result.is(SummaryKey.CONTROL_VECTOR, CvState.ZERO));
        // Flag byte 6 is zero: neither encrypted nor "no key", so the key is in the clear.
        assertTrue(result.is(SummaryKey.MATERIAL_STATE, MaterialState.CLEAR));
    }

    @Test
    void flagsAnAesTokenWhoseDeclaredLengthsAreOutsideTheTable() {
        byte[] token = IcsfTestTokens.aesFixed();
        token[57] = (byte) 111;                 // not 0/128/192/256
        token[59] = (byte) 7;                   // encrypted length not 0/32
        IcsfTestTokens.writeTvv(token);

        ParseResult result = IcsfTokenParser.parse(token);

        assertTrue(result.warned(DiagnosticCode.AES_CLEAR_LENGTH_INVALID));
        assertTrue(result.warned(DiagnosticCode.AES_ENCRYPTED_LENGTH_INVALID));
    }

    // =====================================================================
    // DES fixed-length (Tables 615/616)
    // =====================================================================
    @Test
    void readsKeyTypeAndLengthOfAnImporterFromItsControlVector() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.des("IMPORTER", 16));

        assertEquals(TokenFamily.SYM_FIXED_DES_EXT, result.tokenFamily());
        assertTrue(result.is(SummaryKey.SCOPE, Scope.EXTERNAL));
        assertTrue(result.is(SummaryKey.ALGORITHM, Algorithm.DES_TDES));
        assertEquals("IMPORTER", result.code(SummaryKey.KEY_TYPE, ""));
        assertTrue(result.is(SummaryKey.KEY_LENGTH, DesKeyForm.DOUBLE));
        assertTrue(result.is(SummaryKey.CONTROL_VECTOR, CvState.PRESENT));
        assertTrue(result.is(SummaryKey.TVV, TvvState.VALID));
        assertTrue(result.is(SummaryKey.WRAPPING, WrapMethod.ECB));
        assertTrue(result.is(SummaryKey.EXPORTABILITY, Exportability.YES));
        assertEquals(Boolean.TRUE, result.controlVectorStructureValid().orElse(null));
    }

    @Test
    void recognisesEveryTable676KeyTypeTheFixturesCover() {
        assertEquals("EXPORTER", keyTypeOf(IcsfTestTokens.des("EXPORTER", 16)));
        assertEquals("IMPORTER", keyTypeOf(IcsfTestTokens.des("IMPORTER", 16)));
        assertEquals("CIPHER", keyTypeOf(IcsfTestTokens.des("CIPHER", 16)));
        assertEquals("MAC", keyTypeOf(IcsfTestTokens.des("MAC", 16)));
        assertEquals("PINVER", keyTypeOf(IcsfTestTokens.des("PINVER", 16)));
        assertEquals("IPINENC", keyTypeOf(IcsfTestTokens.des("IPINENC", 16)));
        assertEquals("DATA", keyTypeOf(IcsfTestTokens.des("DATA", 16)));
    }

    @Test
    void aZeroControlVectorIsALegacyDataKeyNotAMissingOne() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8));

        assertTrue(result.is(SummaryKey.CONTROL_VECTOR, CvState.ZERO));
        assertEquals("DATA", result.code(SummaryKey.KEY_TYPE, ""));
        assertTrue(result.is(SummaryKey.KEY_LENGTH, DesKeyForm.SINGLE));
        // With no CV bits there is nothing to read: the bytes cannot settle exportability.
        assertTrue(result.is(SummaryKey.EXPORTABILITY, Exportability.NOT_DETERMINABLE));
    }

    @Test
    void aNocvKeyCarriesNoControlVectorToRead() {
        byte[] token = IcsfTestTokens.externalDes(new byte[16],
                IcsfTestTokens.hex("00427D0003410000"), IcsfTestTokens.hex("00427D0003210000"),
                true, 0, false);

        ParseResult result = IcsfTokenParser.parse(token);

        assertTrue(result.is(SummaryKey.CONTROL_VECTOR, CvState.NOCV));
        assertEquals("NOCV", result.code(SummaryKey.KEY_TYPE, ""));
        assertTrue(result.is(SummaryKey.EXPORTABILITY, Exportability.NOT_DETERMINABLE));
    }

    @Test
    void theExportBitOverridesTheFlagByteAndDoesNotCorruptTheControlVector() {
        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.des("EXPORTER", 16, null, true, null));

        assertTrue(result.is(SummaryKey.EXPORTABILITY, Exportability.NO));
        assertTrue(result.value(SummaryKey.EXPORTABILITY).orElseThrow().text().contains("NO-XPORT"));
        // Clearing bit 17 while compensating bit 23 must leave the CV structurally valid,
        // otherwise every NO-XPORT key would also look like a corrupt one.
        assertEquals(Boolean.TRUE, result.controlVectorStructureValid().orElse(null));
        assertEquals("EXPORTER", result.code(SummaryKey.KEY_TYPE, ""));
    }

    @Test
    void detectsAControlVectorThatBreaksTheStructuralRules() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        token[32] = 0x01;                        // odd number of one bits: parity broken
        IcsfTestTokens.writeTvv(token);

        ParseResult result = IcsfTokenParser.parse(token);

        assertEquals(Boolean.FALSE, result.controlVectorStructureValid().orElse(null));
        assertTrue(result.warned(DiagnosticCode.CV_STRUCTURE_INVALID));
    }

    @Test
    void reportsByte59OutsideTheCurrentTableWithoutCallingItCorrupt() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.des("IMPORTER", 16, 0x40, false, null));

        assertEquals(0x40, result.byte59().orElseThrow());
        // Byte 59 out of range is not, by itself, a coherence warning: on older ICSF levels
        // that byte was subdivided and legitimately carried other values.
        assertFalse(result.warned(DiagnosticCode.BYTE59_VERSION_MISMATCH));
    }

    @Test
    void detectsByte59DisagreeingWithTheTokenVersion() {
        byte[] token = IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 16);
        assertEquals(0x10, token[59]);           // double-length demands version X'01'
        token[4] = 0x00;                         // ...and the token now says X'00'
        IcsfTestTokens.writeTvv(token);

        ParseResult result = IcsfTokenParser.parse(token);

        assertTrue(result.warned(DiagnosticCode.BYTE59_VERSION_MISMATCH));
        assertTrue(result.warning(DiagnosticCode.BYTE59_VERSION_MISMATCH).orElseThrow().text().contains("byte 59"));
    }

    // =====================================================================
    // Effective strength
    // =====================================================================
    @Test
    void aTripleLengthKeyWithK1EqualK3IsEffectivelyDouble() {
        byte[] material = new byte[24];
        Arrays.fill(material, 0, 8, (byte) 0xAA);
        Arrays.fill(material, 8, 16, (byte) 0xBB);
        Arrays.fill(material, 16, 24, (byte) 0xAA);   // K3 = K1

        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, material));

        assertTrue(result.is(SummaryKey.KEY_LENGTH, DesKeyForm.TRIPLE));
        assertTrue(result.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.DOUBLE));
        assertEquals("K1 = K3 != K2", result.code(SummaryKey.COMPONENT_PATTERN, ""));
        assertEquals(3, result.desComponentCount());
        assertTrue(result.desComponentsReliable());
    }

    @Test
    void aTripleLengthKeyWithThreeEqualComponentsCollapsesToSingleDes() {
        byte[] material = new byte[24];
        Arrays.fill(material, (byte) 0xCC);

        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, material));

        assertTrue(result.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.SINGLE));
        assertEquals("K1 = K2 = K3", result.code(SummaryKey.COMPONENT_PATTERN, ""));
    }

    @Test
    void threeDistinctComponentsAreGenuinelyTriple() {
        byte[] material = new byte[24];
        Arrays.fill(material, 0, 8, (byte) 0x11);
        Arrays.fill(material, 8, 16, (byte) 0x22);
        Arrays.fill(material, 16, 24, (byte) 0x33);

        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, material));

        assertTrue(result.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.TRIPLE));
    }

    @Test
    void aNonZeroControlVectorMakesTheComponentComparisonUnreliable() {
        byte[] material = new byte[16];
        Arrays.fill(material, (byte) 0x99);      // both halves identical in the ciphertext

        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.des("IMPORTER", 16, null, false, material));

        // Under a non-zero CV each component is encrypted under a different variant,
        // so equal ciphertext blocks prove nothing about the clear key.
        assertTrue(result.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.UNRELIABLE_SINGLE));
        assertFalse(result.desComponentsReliable());
    }

    @Test
    void wrapenh3ObfuscatesTheLengthSoNothingIsInferred() {
        byte[] token = IcsfTestTokens.externalDes(new byte[16],
                IcsfTestTokens.hex("00427D0003410000"), IcsfTestTokens.hex("00427D0003210000"),
                false, 3, false);

        ParseResult result = IcsfTokenParser.parse(token);

        assertTrue(result.is(SummaryKey.WRAPPING, WrapMethod.WRAPENH3));
        assertTrue(result.is(SummaryKey.KEY_LENGTH, DesKeyForm.OBFUSCATED));
        assertTrue(result.is(SummaryKey.EFFECTIVE_STRENGTH, EffectiveStrength.NOT_APPLICABLE));
        assertNull(DesKeyAnalysis.components(token));
    }

    @Test
    void readsEveryWrappingMethod() {
        byte[] cvLeft = IcsfTestTokens.hex("00427D0003410000");
        byte[] cvRight = IcsfTestTokens.hex("00427D0003210000");
        assertTrue(IcsfTokenParser.parse(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 0, false))
                .is(SummaryKey.WRAPPING, WrapMethod.ECB));
        assertTrue(IcsfTokenParser.parse(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 1, false))
                .is(SummaryKey.WRAPPING, WrapMethod.WRAP_ENH));
        assertTrue(IcsfTokenParser.parse(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 2, false))
                .is(SummaryKey.WRAPPING, WrapMethod.WRAPENH2));
    }

    // =====================================================================
    // TVV
    // =====================================================================
    @Test
    void distinguishesAValidTvvFromAnAbsentOneAndFromAWrongOne() {
        byte[] valid = IcsfTestTokens.des("IMPORTER", 16);
        assertTrue(IcsfTokenParser.parse(valid).is(SummaryKey.TVV, TvvState.VALID));

        byte[] absent = IcsfTestTokens.des("IMPORTER", 16);
        Arrays.fill(absent, 60, 64, (byte) 0);
        ParseResult absentResult = IcsfTokenParser.parse(absent);
        assertTrue(absentResult.is(SummaryKey.TVV, TvvState.ABSENT));
        assertTrue(absentResult.value(SummaryKey.TVV).orElseThrow().text().contains("non-KDSR"));

        byte[] wrong = IcsfTestTokens.des("IMPORTER", 16);
        wrong[16] ^= 0xFF;                       // change the key, leave the TVV alone
        assertTrue(IcsfTokenParser.parse(wrong).is(SummaryKey.TVV, TvvState.INVALID));
    }

    @Test
    void theComputedTvvIsTheSumOfBytesZeroToFiftyNine() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        assertEquals(IcsfTvv.compute(token), IcsfTvv.stored(token));
    }

    // =====================================================================
    // Other families
    // =====================================================================
    @Test
    void parsesAnRkxToken() {
        byte[] token = new byte[64];
        token[0] = 0x02;
        token[4] = 0x10;
        token[7] = 24;
        byte[] rule = "TESTRULE".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(rule, 0, token, 40, rule.length);

        ParseResult result = IcsfTokenParser.parse(token);

        assertEquals(TokenFamily.RKX_DES_EXT, result.tokenFamily());
        assertEquals("TESTRULE", result.code(SummaryKey.RULE_ID, ""));
        assertTrue(result.is(SummaryKey.TVV, TvvState.NOT_APPLICABLE));
        assertTrue(result.provenanceTexts().stream()
                .anyMatch(note -> note.contains("NOT stored in the CKDS")));
    }

    @Test
    void parsesANullToken() {
        ParseResult result = IcsfTokenParser.parse(new byte[16]);

        assertTrue(result.isOk());
        assertEquals(TokenFamily.NULL, result.tokenFamily());
        assertTrue(result.tokenFamily().isNullToken());
        assertTrue(result.is(SummaryKey.SCOPE, Scope.NULL));
        assertTrue(result.is(SummaryKey.MATERIAL_STATE, MaterialState.NO_KEY));
    }

    @Test
    void rejectsInputThatIsNotAKeyToken() {
        assertFalse(IcsfTokenParser.parse(new byte[0]).isOk());
        assertFalse(IcsfTokenParser.parse(new byte[]{1, 2, 3}).isOk());

        byte[] unknown = new byte[64];
        unknown[0] = 0x77;
        ParseResult result = IcsfTokenParser.parse(unknown);
        assertFalse(result.isOk());
        assertTrue(result.error().contains("Unrecognised identifier/version"));
        assertEquals(TokenFamily.UNKNOWN, result.tokenFamily());
    }

    @Test
    void aTruncatedVariableLengthTokenFailsWithoutThrowing() {
        byte[] token = new byte[20];
        token[0] = 0x01;
        token[4] = 0x05;
        token[2] = 0x00;
        token[3] = 20;

        ParseResult result = IcsfTokenParser.parse(token);

        assertFalse(result.isOk());
        assertTrue(result.error().contains("Truncated"));
    }

    // =====================================================================
    // Provenance
    // =====================================================================
    @Test
    void aRawCkdsCopyCannotHaveProducedAnExternalToken() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.des("IMPORTER", 16), Origin.RAW_KDS);

        assertTrue(result.warned(DiagnosticCode.PROVENANCE_INCONSISTENT));
        assertTrue(result.warning(DiagnosticCode.PROVENANCE_INCONSISTENT).orElseThrow().text().contains("only stores INTERNAL tokens"));
    }

    @Test
    void aZeroMkvpAndTvvAreTheExpectedStateOfARawNonKdsrRecord() {
        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.unmaterialisedInternalDes(), Origin.RAW_KDS);

        assertTrue(result.is(SummaryKey.MKVP, MkvpState.ABSENT));
        assertTrue(result.is(SummaryKey.TVV, TvvState.ABSENT));
        // As stored in a non-KDSR CKDS this is normal, so it must not be warned about.
        assertFalse(result.warned(DiagnosticCode.UNMATERIALIZED_ON_KRR));
        assertTrue(result.provenanceTexts().stream()
                .anyMatch(note -> note.contains("Nothing is missing and nothing is corrupt")));
    }

    @Test
    void theSameStateIsAnomalousWhenTheTokenCameFromKeyRecordRead() {
        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.unmaterialisedInternalDes(), Origin.KRR);

        assertTrue(result.warned(DiagnosticCode.UNMATERIALIZED_ON_KRR));
    }

    @Test
    void inferModeBoundsTheProvenanceWithoutAssertingOne() {
        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.unmaterialisedInternalDes(), Origin.INFER);

        assertTrue(result.provenanceTexts().stream()
                .anyMatch(note -> note.startsWith("  compatible with: raw copy of a non-KDSR CKDS")));
        assertTrue(result.provenanceTexts().stream()
                .anyMatch(note -> note.startsWith("  ruled out: CSNBKRR/KRR2")));
    }

    @Test
    void aWrongTvvIsReportedWhateverProvenanceWasDeclared() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        token[16] ^= 0xFF;
        for (Origin origin : Origin.values()) {
            assertTrue(IcsfTokenParser.parse(token, origin).warned(DiagnosticCode.TVV_PRESENT_BUT_WRONG),
                    "A TVV that does not add up is an integrity problem, not a provenance one: " + origin);
        }
    }

    // =====================================================================
    // Report
    // =====================================================================
    @Test
    void theTextReportCarriesTheSummaryTheDetailAndTheSecurityNotice() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        ParseResult result = IcsfTokenParser.parse(token, Origin.INFER);

        String report = IcsfTokenReport.renderText(result, Origin.INFER, token);

        assertTrue(report.contains("ICSF / CCA KEY TOKEN ANALYSIS"));
        assertTrue(report.contains("SUMMARY"));
        assertTrue(report.contains("FIELD-BY-FIELD DETAIL"));
        assertTrue(report.contains("PROVENANCE"));
        assertTrue(report.contains("WARNINGS"));
        assertTrue(report.contains("IMPORTER"));
        assertTrue(report.contains(IcsfTokenReport.SECURITY_NOTICE));
    }

    @Test
    void theReportMapIsSerializableAndKeepsTheAggregatedFlag() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.des("IMPORTER", 16));

        var map = IcsfTokenReport.toMap(result);
        String json = new com.google.gson.Gson().toJson(map);

        assertTrue(json.contains("\"tokenFamily\""));
        assertTrue(json.contains("IMPORTER"));
        assertTrue(json.contains("\"aggregated\""));
        assertTrue(json.contains("securityNotice"));
    }

    @Test
    void everyAggregatedDimensionIsPresentOnEveryParsableToken() {
        byte[][] corpus = {
                IcsfTestTokens.aesFixed(),
                IcsfTestTokens.des("IMPORTER", 16),
                IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8),
                IcsfTestTokens.des("MAC", 24),
                IcsfTestTokens.unmaterialisedInternalDes(),
                new byte[16],
        };
        for (byte[] token : corpus) {
            ParseResult result = IcsfTokenParser.parse(token);
            assertTrue(result.isOk(), "fixture must parse");
            for (SummaryKey key : SummaryKey.values()) {
                if (!key.aggregated()) continue;
                assertNotNull(result.summary().get(key),
                        "the batch layer counts " + key + ", so every token must report it: "
                                + result.tokenFamily());
            }
        }
    }

    // --- helpers ---------------------------------------------------------
    private static String keyTypeOf(byte[] token) {
        return IcsfTokenParser.parse(token).code(SummaryKey.KEY_TYPE, "");
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }
}
