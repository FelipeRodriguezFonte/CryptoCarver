package com.cryptocarver.crypto.icsf;

import com.cryptocarver.crypto.icsf.IcsfVocabulary.DesKeyForm;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.Scope;
import com.cryptocarver.crypto.icsf.IcsfVocabulary.TvvState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The batch: inventory, findings, statistics and duplicate detection. */
class IcsfBatchAnalyzerTest {

    private final byte[] importer = IcsfTestTokens.des("IMPORTER", 16);
    private final byte[] oddByte59 = IcsfTestTokens.des("IMPORTER", 16, 0x40, false, null);
    private final byte[] singleData = IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8);
    private final byte[] noExport = IcsfTestTokens.des("EXPORTER", 16, null, true, null);
    private final byte[] aes = IcsfTestTokens.aesFixed();

    private IcsfBatchReport standardBatch() {
        String text = List.of(importer, oddByte59, singleData, noExport, aes).stream()
                .map(IcsfTestTokens::hex)
                .collect(Collectors.joining("\n"));
        return IcsfBatchAnalyzer.analyse(text, BatchInputFormat.LINE, Origin.INFER);
    }

    private static Set<FindingCode> codesOf(IcsfBatchReport report, int index) {
        return report.items().get(index).findings().stream()
                .map(Finding::code).collect(Collectors.toSet());
    }

    // =====================================================================
    // Inventory
    // =====================================================================
    @Test
    void buildsOneInventoryRowPerToken() {
        IcsfBatchReport report = standardBatch();

        assertEquals(5, report.total());
        assertEquals(5, report.analysed().size());
        assertEquals(0, report.failed().size());
        assertEquals(5, report.rows().size());
    }

    @Test
    void theInventoryCarriesTheAnalyserVerdicts() {
        InventoryRow row = standardBatch().rows().get(0);

        assertEquals("OK", row.get(InventoryColumn.STATUS));
        assertEquals("IMPORTER", row.get(InventoryColumn.KEY_TYPE));
        assertEquals(DesKeyForm.DOUBLE.name(), row.get(InventoryColumn.KEY_LENGTH));
        assertEquals(Scope.EXTERNAL.name(), row.get(InventoryColumn.SCOPE));
        assertEquals(TvvState.VALID.name(), row.get(InventoryColumn.TVV));
        assertEquals("64", row.get(InventoryColumn.BYTES));
        assertEquals("1", row.get(InventoryColumn.INDEX));
    }

    @Test
    void theAesTokenIsCataloguedAsADataKey() {
        assertEquals("DATA", standardBatch().rows().get(4).get(InventoryColumn.KEY_TYPE));
    }

    @Test
    void everyColumnBoundToADimensionCoversExactlyTheAggregatedOnes() {
        Set<SummaryKey> bound = Arrays.stream(InventoryColumn.values())
                .map(InventoryColumn::summaryKey)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<SummaryKey> aggregated = Arrays.stream(SummaryKey.values())
                .filter(SummaryKey::aggregated)
                .collect(Collectors.toSet());

        assertEquals(aggregated, bound,
                "the inventory must tabulate exactly the dimensions statistics count");
        assertEquals(12, bound.size());
        assertEquals(18, InventoryColumn.values().length);
    }

    // =====================================================================
    // Findings: detected, and no false positives
    // =====================================================================
    @Test
    void detectsByte59OutsideTheCurrentTableWithoutFlaggingAStandardOne() {
        IcsfBatchReport report = standardBatch();

        assertTrue(codesOf(report, 1).contains(FindingCode.BYTE59_FUERA_DE_TABLA));
        assertFalse(codesOf(report, 0).contains(FindingCode.BYTE59_FUERA_DE_TABLA));
    }

    @Test
    void detectsASingleLengthDesKeyAndItsZeroControlVector() {
        Set<FindingCode> codes = codesOf(standardBatch(), 2);

        assertTrue(codes.contains(FindingCode.DES_56_BITS));
        assertTrue(codes.contains(FindingCode.CV_CERO));
    }

    @Test
    void detectsNoExportWithoutAlsoCallingTheControlVectorCorrupt() {
        Set<FindingCode> codes = codesOf(standardBatch(), 3);

        assertTrue(codes.contains(FindingCode.NO_EXPORTABLE));
        assertFalse(codes.contains(FindingCode.CV_INVALIDO));
    }

    @Test
    void detectsLegacyEcbWrappingAndTheEnhancedMethods() {
        assertTrue(codesOf(standardBatch(), 0).contains(FindingCode.WRAP_ECB));

        byte[] enhanced = IcsfTestTokens.externalDes(new byte[16],
                IcsfTestTokens.hex("00427D0003410000"), IcsfTestTokens.hex("00427D0003210000"),
                false, 2, false);
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(enhanced),
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.WRAP_MEJORADO));
        assertFalse(codesOf(report, 0).contains(FindingCode.WRAP_ECB));
    }

    @Test
    void detectsMaterialInTheClear() {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(aes),
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.MATERIAL_EN_CLARO));
    }

    @Test
    void detectsAnInvalidControlVector() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);
        token[32] = 0x01;
        IcsfTestTokens.writeTvv(token);

        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(token),
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.CV_INVALIDO));
    }

    @Test
    void detectsAnInvalidTvvAndDistinguishesItFromAnAbsentOne() {
        byte[] wrong = IcsfTestTokens.des("IMPORTER", 16);
        wrong[16] ^= 0xFF;
        IcsfBatchReport wrongReport = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(wrong),
                BatchInputFormat.LINE, Origin.INFER);
        assertTrue(codesOf(wrongReport, 0).contains(FindingCode.TVV_INVALIDO));
        assertFalse(codesOf(wrongReport, 0).contains(FindingCode.TVV_AUSENTE));

        byte[] absent = IcsfTestTokens.des("IMPORTER", 16);
        Arrays.fill(absent, 60, 64, (byte) 0);
        IcsfBatchReport absentReport = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(absent),
                BatchInputFormat.LINE, Origin.INFER);
        assertTrue(codesOf(absentReport, 0).contains(FindingCode.TVV_AUSENTE));
        assertFalse(codesOf(absentReport, 0).contains(FindingCode.TVV_INVALIDO));
    }

    @Test
    void detectsANocvTransportKey() {
        byte[] token = IcsfTestTokens.externalDes(new byte[16],
                IcsfTestTokens.hex("00427D0003410000"), IcsfTestTokens.hex("00427D0003210000"),
                true, 0, false);

        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(token),
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.NOCV));
        assertFalse(codesOf(report, 0).contains(FindingCode.CV_CERO));
    }

    @Test
    void detectsAKeyThatCollapsesToSingleDesAndOneThatIsOnlyDouble() {
        byte[] collapsed = new byte[24];
        Arrays.fill(collapsed, (byte) 0xCC);
        IcsfBatchReport single = IcsfBatchAnalyzer.analyse(
                IcsfTestTokens.hex(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, collapsed)),
                BatchInputFormat.LINE, Origin.INFER);
        assertTrue(codesOf(single, 0).contains(FindingCode.DES_FUERZA_SIMPLE));

        byte[] effectivelyDouble = new byte[24];
        Arrays.fill(effectivelyDouble, 0, 8, (byte) 0xAA);
        Arrays.fill(effectivelyDouble, 8, 16, (byte) 0xBB);
        Arrays.fill(effectivelyDouble, 16, 24, (byte) 0xAA);
        IcsfBatchReport doubled = IcsfBatchAnalyzer.analyse(
                IcsfTestTokens.hex(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, effectivelyDouble)),
                BatchInputFormat.LINE, Origin.INFER);
        assertTrue(codesOf(doubled, 0).contains(FindingCode.DES_FUERZA_DOBLE));
        assertFalse(codesOf(doubled, 0).contains(FindingCode.DES_FUERZA_SIMPLE));
    }

    @Test
    void detectsEnhOnlyOnATripleLengthKey() {
        // Table 676's default CV for triple-length keys carries bit 56 set.
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(
                IcsfTestTokens.hex(IcsfTestTokens.des("IMPORTER", 24)),
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.ENH_ONLY));
    }

    @Test
    void detectsACompliantTaggedKey() {
        byte[] cvLeft = IcsfTestTokens.hex("00427D0003410000");
        cvLeft[7] = (byte) 0x21;                 // bit 58 (COMP-TAG) plus the parity bit
        byte[] token = IcsfTestTokens.externalDes(new byte[16], cvLeft,
                IcsfTestTokens.hex("00427D0003210000"), false, 0, false);

        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(token),
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.COMP_TAG));
        assertFalse(codesOf(report, 0).contains(FindingCode.CV_INVALIDO),
                "setting bit 58 with its parity compensated must not look like a broken CV");
    }

    @Test
    void promotesTheParserByte59WarningToItsOwnFinding() {
        byte[] token = IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 16);
        token[4] = 0x00;                         // byte 59 says double, version says single
        IcsfTestTokens.writeTvv(token);

        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(token),
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.BYTE59_INCOHERENTE));
        assertTrue(codesOf(report, 0).contains(FindingCode.AVISOS_PARSER));
    }

    @Test
    void aCleanTokenRaisesNoHighSeverityFinding() {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(importer),
                BatchInputFormat.LINE, Origin.INFER);

        List<FindingCode> high = report.findings().stream()
                .map(IcsfBatchReport.AggregatedFinding::code)
                .filter(code -> code.severity() == FindingCode.Severity.HIGH)
                .toList();

        assertTrue(high.isEmpty(), "unexpected high-severity findings: " + high);
    }

    // =====================================================================
    // Aggregation and ordering
    // =====================================================================
    @Test
    void aggregatesFindingsBySeverityThenByHowManyTokensRaisedThem() {
        IcsfBatchReport report = standardBatch();

        List<FindingCode.Severity> order = report.findings().stream()
                .map(finding -> finding.code().severity()).toList();
        List<FindingCode.Severity> sorted = new ArrayList<>(order);
        sorted.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        assertEquals(sorted, order);

        IcsfBatchReport.AggregatedFinding des56 = report.findings().stream()
                .filter(finding -> finding.code() == FindingCode.DES_56_BITS)
                .findFirst().orElseThrow();
        assertEquals(FindingCode.Severity.HIGH, des56.code().severity());
        assertEquals(1, des56.count());
        assertEquals(List.of(3), des56.tokens());
    }

    @Test
    void theInventoryCanBeFilteredDownToOneFinding() {
        IcsfBatchReport report = standardBatch();

        List<InventoryRow> rows = report.rowsWith(FindingCode.DES_56_BITS);

        assertEquals(1, rows.size());
        assertEquals("3", rows.get(0).get(InventoryColumn.INDEX));
    }

    @Test
    void theFindingsColumnListsEveryCodeRaised() {
        String findings = standardBatch().rows().get(2).get(InventoryColumn.FINDINGS);

        assertTrue(findings.contains("DES-56-BITS"));
        assertTrue(findings.contains("CV-CERO"));
    }

    // =====================================================================
    // Duplicates and failures
    // =====================================================================
    @Test
    void flagsTheSameBytesAppearingMoreThanOnce() {
        String hex = IcsfTestTokens.hex(importer);
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(hex + "\n" + hex,
                BatchInputFormat.LINE, Origin.INFER);

        assertTrue(codesOf(report, 0).contains(FindingCode.DUPLICADO));
        assertTrue(codesOf(report, 1).contains(FindingCode.DUPLICADO));
        assertTrue(report.items().get(0).findings().stream()
                .anyMatch(finding -> finding.text().contains("#1") && finding.text().contains("#2")));
    }

    @Test
    void distinctTokensAreNotFlaggedAsDuplicates() {
        assertFalse(codesOf(standardBatch(), 0).contains(FindingCode.DUPLICADO));
    }

    @Test
    void countsUnreadableAndUnrecognisedEntriesApartWithoutBreakingTheReport() {
        String text = String.join("\n", IcsfTestTokens.hex(importer), IcsfTestTokens.hex(importer),
                "ZZZZ", "0102030405060708");

        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(text, BatchInputFormat.LINE, Origin.INFER);

        assertEquals(4, report.total());
        assertEquals(2, report.failed().size());
        assertEquals("ERROR", report.rows().get(2).get(InventoryColumn.STATUS));
        assertEquals("ERROR", report.rows().get(3).get(InventoryColumn.STATUS));
        assertTrue(codesOf(report, 2).contains(FindingCode.ENTRADA_NO_RECONOCIDA));
        assertTrue(codesOf(report, 3).contains(FindingCode.ENTRADA_NO_RECONOCIDA));
        assertTrue(IcsfBatchRenderer.renderSummary(report).contains("UNRECOGNISED ENTRIES"));
    }

    // =====================================================================
    // Statistics
    // =====================================================================
    @Test
    void everyStatisticAddsUpToTheNumberOfAnalysedTokens() {
        IcsfBatchReport report = standardBatch();
        int analysed = report.analysed().size();

        assertFalse(report.statistics().isEmpty());
        for (IcsfBatchReport.Group group : report.statistics()) {
            int total = group.values().stream()
                    .mapToInt(IcsfBatchReport.Group.Value::count).sum();
            assertEquals(analysed, total, "dimension " + group.dimension() + " must cover every token");

            double percentage = group.values().stream()
                    .mapToDouble(IcsfBatchReport.Group.Value::percentage).sum();
            assertEquals(100.0, percentage, 0.5, "percentages of " + group.dimension());
        }
    }

    @Test
    void statisticsIgnoreTheEntriesThatDidNotAnalyse() {
        String text = IcsfTestTokens.hex(importer) + "\nZZZZ";

        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(text, BatchInputFormat.LINE, Origin.INFER);

        assertEquals(2, report.total());
        assertEquals(1, report.analysed().size());
        for (IcsfBatchReport.Group group : report.statistics()) {
            assertEquals(1, group.values().stream()
                    .mapToInt(IcsfBatchReport.Group.Value::count).sum());
        }
    }

    @Test
    void aDimensionNoTokenInTheBatchUsesIsDroppedRatherThanPrintedEmpty() {
        // A batch of nothing but null tokens has no wrapping method to report on.
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse("00000000000000000000000000000000",
                BatchInputFormat.LINE, Origin.INFER);

        assertEquals(1, report.analysed().size());
        assertTrue(report.statistics().stream()
                .noneMatch(group -> group.dimension() == SummaryKey.WRAPPING));
    }

    @Test
    void statisticsCoverEveryAggregatedDimensionOnAMixedBatch() {
        IcsfBatchReport report = standardBatch();

        Set<SummaryKey> reported = report.statistics().stream()
                .map(IcsfBatchReport.Group::dimension).collect(Collectors.toSet());

        for (SummaryKey key : List.of(SummaryKey.FAMILY, SummaryKey.SCOPE, SummaryKey.ALGORITHM,
                SummaryKey.KEY_TYPE, SummaryKey.KEY_LENGTH, SummaryKey.MATERIAL_STATE,
                SummaryKey.CONTROL_VECTOR, SummaryKey.TVV, SummaryKey.MKVP)) {
            assertTrue(reported.contains(key), "missing statistic for " + key);
        }
    }

    // =====================================================================
    // Provenance flows through to the batch
    // =====================================================================
    @Test
    void theDeclaredProvenanceReachesEveryTokenAnalysis() {
        String text = IcsfTestTokens.hex(IcsfTestTokens.unmaterialisedInternalDes());

        IcsfBatchReport raw = IcsfBatchAnalyzer.analyse(text, BatchInputFormat.LINE, Origin.RAW_KDS);
        IcsfBatchReport krr = IcsfBatchAnalyzer.analyse(text, BatchInputFormat.LINE, Origin.KRR);

        assertFalse(raw.items().get(0).result().warned(DiagnosticCode.UNMATERIALIZED_ON_KRR));
        assertTrue(krr.items().get(0).result().warned(DiagnosticCode.UNMATERIALIZED_ON_KRR));
        assertEquals(Origin.KRR, krr.origin());
    }
}
