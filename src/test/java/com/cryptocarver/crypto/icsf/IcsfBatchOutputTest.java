package com.cryptocarver.crypto.icsf;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three batch outputs: the text report, the CSV and the JSON. */
class IcsfBatchOutputTest {

    private IcsfBatchReport report() {
        String text = List.of(
                        IcsfTestTokens.des("IMPORTER", 16),
                        IcsfTestTokens.des("IMPORTER", 16, 0x40, false, null),
                        IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8),
                        IcsfTestTokens.des("EXPORTER", 16, null, true, null),
                        IcsfTestTokens.aesFixed()).stream()
                .map(IcsfTestTokens::hex)
                .collect(Collectors.joining("\n"));
        return IcsfBatchAnalyzer.analyse(text, BatchInputFormat.LINE, Origin.INFER);
    }

    // =====================================================================
    // Text
    // =====================================================================
    @Test
    void theSummaryCarriesAllFourSections() {
        String summary = IcsfBatchRenderer.renderSummary(report());

        assertTrue(summary.contains("ICSF / CCA KEY TOKEN BATCH ANALYSIS"));
        assertTrue(summary.contains("1.  STATISTICS"));
        assertTrue(summary.contains("2.  AUDIT FINDINGS"));
        assertTrue(summary.contains("3.  INVENTORY"));
        assertTrue(summary.contains("Tokens read          : 5"));
    }

    @Test
    void theCoverCarriesTheSecurityNotice() {
        String summary = IcsfBatchRenderer.renderSummary(report());

        // The notice is wrapped to the report width, so match a distinctive phrase.
        assertTrue(summary.contains("decrypts nothing"));
        assertTrue(summary.contains("master key"));
        assertTrue(summary.contains("same care as the dump"));
    }

    @Test
    void everyFindingExplainsWhatItIsAndWhatToDoAboutIt() {
        String summary = IcsfBatchRenderer.renderSummary(report());

        assertTrue(summary.contains("BYTE59-FUERA-DE-TABLA"));
        // The byte-59 wording has to send the reader to the documentation current when
        // the key was made, not declare the token corrupt: the difference is repairing
        // a byte versus running a key ceremony.
        assertTrue(summary.contains("SUBDIVIDED"));
        assertTrue(summary.contains("documentation current when the key was created"));
        assertFalse(summary.contains("corrupt token"));
    }

    @Test
    void theFullReportAppendsTheWholeCardForEveryToken() {
        IcsfBatchReport report = report();

        String full = IcsfBatchRenderer.renderFull(report, true);

        assertTrue(full.contains("FULL PER-TOKEN DETAIL"));
        assertEquals(report.analysed().size(), countOf(full, "FIELD-BY-FIELD DETAIL"));
        assertEquals(report.analysed().size(), countOf(full, System.lineSeparator() + "SUMMARY"));
    }

    @Test
    void theDetailCanBeLeftOutForLargeBatches() {
        IcsfBatchReport report = report();

        String withDetail = IcsfBatchRenderer.renderFull(report, true);
        String withoutDetail = IcsfBatchRenderer.renderFull(report, false);

        assertFalse(withoutDetail.contains("FULL PER-TOKEN DETAIL"));
        assertEquals(IcsfBatchRenderer.renderSummary(report), withoutDetail);
        assertTrue(withoutDetail.length() < withDetail.length());
        // The whole card for an entire CKDS runs to tens of megabytes; the point of the
        // switch is that the saving is large, not cosmetic.
        assertTrue(withDetail.length() > withoutDetail.length() * 3);
    }

    @Test
    void unreadableEntriesGetTheirOwnSectionAndDoNotBreakTheReport() {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(
                IcsfTestTokens.hex(IcsfTestTokens.des("IMPORTER", 16)) + "\nZZZZ",
                BatchInputFormat.LINE, Origin.INFER);

        String summary = IcsfBatchRenderer.renderSummary(report);
        String full = IcsfBatchRenderer.renderFull(report, true);

        assertTrue(summary.contains("4.  UNRECOGNISED ENTRIES"));
        assertTrue(full.contains("READ ERROR"));
    }

    // =====================================================================
    // CSV
    // =====================================================================
    @Test
    void theCsvHasAHeaderAndOneRowPerTokenWithEveryColumn() {
        IcsfBatchReport report = report();

        String csv = IcsfBatchRenderer.toCsv(report);
        List<String> lines = csv.lines().filter(line -> !line.isBlank()).toList();

        assertEquals(report.total() + 1, lines.size());
        // 18 inventory columns plus the token in hex is 19 fields, so 18 separators.
        assertEquals(InventoryColumn.values().length, countOf(lines.get(0), ";"));
        for (InventoryColumn column : InventoryColumn.values()) {
            assertTrue(lines.get(0).contains(column.header()), "missing header " + column.header());
        }
        assertTrue(lines.get(0).endsWith("Token (hex)"));
    }

    @Test
    void everyCsvRowCarriesTheWholeTokenInHexadecimal() {
        IcsfBatchReport report = report();

        String csv = IcsfBatchRenderer.toCsv(report);

        for (BatchItem item : report.items()) {
            assertTrue(csv.contains(item.entry().hex()),
                    "the CSV must carry the token itself: " + item.display());
        }
    }

    @Test
    void theCsvBytesStartWithAByteOrderMarkSoExcelOpensThemCorrectly() {
        byte[] bytes = IcsfBatchRenderer.toCsvBytes(report());

        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        String decoded = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertEquals(IcsfBatchRenderer.toCsv(report()).lines().findFirst().orElseThrow(),
                decoded.lines().findFirst().orElseThrow());
    }

    @Test
    void theCsvSeparatorIsConfigurableAndCellsAreQuotedWhenTheyNeedIt() {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(
                "A;B|" + IcsfTestTokens.hex(IcsfTestTokens.des("IMPORTER", 16)),
                BatchInputFormat.LINE, Origin.INFER);

        String semicolon = IcsfBatchRenderer.toCsv(report, ';');
        String comma = IcsfBatchRenderer.toCsv(report, ',');

        assertTrue(semicolon.contains("\"A;B\""), "a cell holding the separator must be quoted");
        assertTrue(comma.contains("A;B"));
        assertFalse(comma.contains("\"A;B\""), "with a comma separator that cell needs no quoting");
    }

    // =====================================================================
    // JSON
    // =====================================================================
    @Test
    void theJsonViewIsSerializableAndComplete() {
        IcsfBatchReport report = report();

        String json = new Gson().toJson(IcsfBatchRenderer.toMap(report, true));

        assertTrue(json.contains("\"generated\""));
        assertTrue(json.contains("\"statistics\""));
        assertTrue(json.contains("\"findings\""));
        assertTrue(json.contains("\"rows\""));
        assertTrue(json.contains("\"columns\""));
        assertTrue(json.contains("\"entries\""));
        assertTrue(json.contains("\"securityNotice\""));
        assertTrue(json.contains("BYTE59-FUERA-DE-TABLA"));
        assertTrue(json.contains("\"detail\""));
    }

    @Test
    void theJsonCarriesEveryTokenAndEveryFinding() {
        IcsfBatchReport report = report();

        var map = IcsfBatchRenderer.toMap(report, false);

        assertEquals(report.total(), map.get("total"));
        assertEquals(report.analysed().size(), map.get("analysed"));
        assertEquals(report.failed().size(), map.get("failed"));
        assertEquals(report.total(), ((List<?>) map.get("rows")).size());
        assertEquals(report.total(), ((List<?>) map.get("entries")).size());
        assertEquals(report.findings().size(), ((List<?>) map.get("findings")).size());
        assertEquals(InventoryColumn.values().length, ((List<?>) map.get("columns")).size());
    }

    @Test
    void theJsonNamesTheDimensionBehindEveryBoundColumn() {
        var map = IcsfBatchRenderer.toMap(report(), false);

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> columns =
                (List<java.util.Map<String, Object>>) map.get("columns");

        long bound = columns.stream().filter(column -> column.get("summaryKey") != null).count();
        assertEquals(12, bound, "the twelve aggregated dimensions must be traceable from the JSON");
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
