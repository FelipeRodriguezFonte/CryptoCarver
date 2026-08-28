package com.cryptocarver.crypto.icsf;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batch and the one-by-one analysis must never contradict each other.
 *
 * <p>{@link InventoryMapper} is built so it cannot: it is handed the analysis, not
 * the token, so there is no second reading of the bytes to drift from the first.
 * These tests guard that property rather than assume it — a future shortcut in the
 * mapper that re-derived a value from the bytes would fail here.</p>
 */
class IcsfBatchConsistencyTest {

    /** A corpus spanning every family and the awkward cases within each. */
    private static List<byte[]> corpus() {
        List<byte[]> tokens = new ArrayList<>();

        tokens.add(IcsfTestTokens.aesFixed());
        tokens.add(IcsfTestTokens.des("IMPORTER", 16));
        tokens.add(IcsfTestTokens.des("EXPORTER", 16));
        tokens.add(IcsfTestTokens.des("EXPORTER", 16, null, true, null));
        tokens.add(IcsfTestTokens.des("CIPHER", 16));
        tokens.add(IcsfTestTokens.des("MAC", 16));
        tokens.add(IcsfTestTokens.des("MAC", 24));
        tokens.add(IcsfTestTokens.des("PINVER", 16));
        tokens.add(IcsfTestTokens.des("IPINENC", 16));
        tokens.add(IcsfTestTokens.des("IMPORTER", 24));
        tokens.add(IcsfTestTokens.des("IMPORTER", 16, 0x40, false, null));
        tokens.add(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8));
        tokens.add(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 16));
        tokens.add(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24));
        tokens.add(IcsfTestTokens.unmaterialisedInternalDes());

        // triple-length key that collapses to a single DES
        byte[] collapsed = new byte[24];
        Arrays.fill(collapsed, (byte) 0xCC);
        tokens.add(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, collapsed));

        // triple-length key that is really only double
        byte[] effectivelyDouble = new byte[24];
        Arrays.fill(effectivelyDouble, 0, 8, (byte) 0xAA);
        Arrays.fill(effectivelyDouble, 8, 16, (byte) 0xBB);
        Arrays.fill(effectivelyDouble, 16, 24, (byte) 0xAA);
        tokens.add(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 24, null, false, effectivelyDouble));

        byte[] cvLeft = IcsfTestTokens.hex("00427D0003410000");
        byte[] cvRight = IcsfTestTokens.hex("00427D0003210000");
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, true, 0, false));   // NOCV
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 1, false));  // WRAP-ENH
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 2, false));  // WRAPENH2
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 3, false));  // WRAPENH3
        tokens.add(IcsfTestTokens.externalDes(new byte[16], cvLeft, cvRight, false, 0, true));   // clear key

        // a token whose TVV no longer adds up
        byte[] wrongTvv = IcsfTestTokens.des("IMPORTER", 16);
        wrongTvv[16] ^= 0xFF;
        tokens.add(wrongTvv);

        // a token with a structurally broken Control Vector
        byte[] brokenCv = IcsfTestTokens.des("IMPORTER", 16);
        brokenCv[32] = 0x01;
        IcsfTestTokens.writeTvv(brokenCv);
        tokens.add(brokenCv);

        // RKX
        byte[] rkx = new byte[64];
        rkx[0] = 0x02;
        rkx[4] = 0x10;
        rkx[7] = 24;
        tokens.add(rkx);

        // null token
        tokens.add(new byte[16]);

        return tokens;
    }

    private static String corpusText() {
        return corpus().stream().map(IcsfTestTokens::hex).collect(Collectors.joining("\n"));
    }

    @Test
    void everyInventoryCellMatchesTheSingleTokenAnalyserTokenByToken() {
        for (Origin origin : Origin.values()) {
            IcsfBatchReport report = IcsfBatchAnalyzer.analyse(corpusText(),
                    BatchInputFormat.LINE, origin);

            assertEquals(corpus().size(), report.total());

            for (BatchItem item : report.items()) {
                // Analyse the very same bytes on their own, exactly as the single-token
                // view would, and demand the same verdict in all twelve dimensions.
                ParseResult standalone = IcsfTokenParser.parse(item.entry().data(), origin);

                assertEquals(standalone.isOk(), item.isOk(),
                        "agreement on whether " + item.display() + " analyses at all");
                if (!standalone.isOk()) continue;

                for (InventoryColumn column : InventoryColumn.values()) {
                    SummaryKey key = column.summaryKey();
                    if (key == null) continue;
                    assertEquals(standalone.code(key, InventoryRow.NONE), item.row().get(column),
                            "provenance " + origin + ", token " + item.display()
                                    + " (" + standalone.tokenFamily() + "), dimension " + key);
                }

                assertEquals(standalone.tokenFamily().code(),
                        standalone.code(SummaryKey.FAMILY, ""),
                        "the family column must be the family the analyser reported");
                assertEquals(String.valueOf(standalone.warnings().size()),
                        item.row().get(InventoryColumn.WARNINGS),
                        "warning count for " + item.display());
            }
        }
    }

    @Test
    void findingsAreReproducibleFromTheStandaloneAnalysisAlone() {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(corpusText(),
                BatchInputFormat.LINE, Origin.INFER);

        for (BatchItem item : report.items()) {
            ParseResult standalone = IcsfTokenParser.parse(item.entry().data(), Origin.INFER);
            List<FindingCode> recomputed = FindingDetector.detect(item.entry(), standalone).stream()
                    .map(Finding::code).toList();
            List<FindingCode> reported = item.findings().stream()
                    .map(Finding::code)
                    // DUPLICADO is a property of the batch, not of the token, so the
                    // standalone detector cannot and should not produce it.
                    .filter(code -> code != FindingCode.DUPLICADO)
                    .toList();

            assertEquals(recomputed, reported, "findings for " + item.display());
        }
    }

    @Test
    void theSameTokenReadThroughAnyOfTheThreeInputShapesYieldsTheSameInventory() {
        byte[] token = IcsfTestTokens.des("IMPORTER", 16);

        IcsfBatchReport asLine = IcsfBatchAnalyzer.analyse(IcsfTestTokens.hex(token),
                BatchInputFormat.AUTO, Origin.INFER);
        IcsfBatchReport asTwoRows = IcsfBatchAnalyzer.analyse(IcsfTestTokens.twoRows(token),
                BatchInputFormat.AUTO, Origin.INFER);
        StringBuilder stacked = new StringBuilder();
        for (byte value : token) stacked.append(String.format("%02X%n", value & 0xFF));
        IcsfBatchReport asBlock = IcsfBatchAnalyzer.analyse(stacked.toString(),
                BatchInputFormat.AUTO, Origin.INFER);

        for (InventoryColumn column : InventoryColumn.values()) {
            if (column == InventoryColumn.INDEX) continue;
            assertEquals(asLine.rows().get(0).get(column), asTwoRows.rows().get(0).get(column),
                    "column " + column + " must not depend on how the token was pasted in");
            assertEquals(asLine.rows().get(0).get(column), asBlock.rows().get(0).get(column),
                    "column " + column + " must not depend on how the token was pasted in");
        }
        assertArrayEquals(token, asTwoRows.items().get(0).entry().data());
        assertArrayEquals(token, asBlock.items().get(0).entry().data());
    }

    @Test
    void theCorpusExercisesEveryFamilyTheAnalyserSupports() {
        IcsfBatchReport report = IcsfBatchAnalyzer.analyse(corpusText(),
                BatchInputFormat.LINE, Origin.INFER);

        List<String> families = report.items().stream()
                .filter(BatchItem::isOk)
                .map(item -> item.result().tokenFamily().code())
                .distinct().toList();

        assertTrue(families.contains(TokenFamily.SYM_FIXED_AES.code()));
        assertTrue(families.contains(TokenFamily.SYM_FIXED_DES_EXT.code()));
        assertTrue(families.contains(TokenFamily.SYM_FIXED_DES_INT.code()));
        assertTrue(families.contains(TokenFamily.RKX_DES_EXT.code()));
        assertTrue(families.contains(TokenFamily.NULL.code()));
        assertFalse(report.analysed().isEmpty());
    }
}
