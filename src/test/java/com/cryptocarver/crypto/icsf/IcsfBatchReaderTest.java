package com.cryptocarver.crypto.icsf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the batch: the three shapes the input arrives in, and labels versus hex separators. */
class IcsfBatchReaderTest {

    private final byte[] importer = IcsfTestTokens.des("IMPORTER", 16);
    private final byte[] aes = IcsfTestTokens.aesFixed();

    // =====================================================================
    // One token per line
    // =====================================================================
    @Test
    void autoReadsOneTokenPerLine() {
        String text = String.join("\n", IcsfTestTokens.hex(importer),
                IcsfTestTokens.hex(IcsfTestTokens.des(IcsfTestTokens.ZERO_CV_TYPE, 8)),
                IcsfTestTokens.hex(aes));

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.AUTO);

        assertEquals(3, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.format() == BatchInputFormat.Resolved.LINE));
        assertArrayEquals(importer, entries.get(0).data());
        assertArrayEquals(aes, entries.get(2).data());
    }

    @Test
    void indexesAndLineNumbersAreOneBasedAndSurviveBlankLinesAndComments() {
        String text = "# a leading comment\n"
                + IcsfTestTokens.hex(importer) + "\n"
                + "# a comment inside the block does not end it\n"
                + IcsfTestTokens.hex(aes) + "\n"
                + "\n"
                + IcsfTestTokens.hex(importer) + "\n";

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.AUTO);

        assertEquals(3, entries.size());
        assertEquals(1, entries.get(0).index());
        assertEquals(2, entries.get(0).line());
        assertEquals(4, entries.get(1).line());
        assertEquals(6, entries.get(2).line());
        assertEquals(3, entries.get(2).index());
    }

    // =====================================================================
    // Two host rows per token
    // =====================================================================
    @Test
    void autoRecognisesTheTwoRowHostDump() {
        String text = IcsfTestTokens.twoRows(importer) + "\n\n" + IcsfTestTokens.twoRows(aes);

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.AUTO);

        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.format() == BatchInputFormat.Resolved.TWO_ROW));
        assertArrayEquals(importer, entries.get(0).data());
        assertArrayEquals(aes, entries.get(1).data());
        assertEquals(2, entries.get(0).lineCount());
    }

    @Test
    void forcingLineModeOnTwoRowInputDoesNotSlipTheTokenThrough() {
        List<BatchEntry> entries = BatchReader.read(IcsfTestTokens.twoRows(importer),
                BatchInputFormat.LINE);

        assertEquals(2, entries.size());
        assertTrue(entries.stream().noneMatch(e -> java.util.Arrays.equals(e.data(), importer)));
    }

    @Test
    void forcingTwoRowModeOnAnOddBlockReportsItPerLineInsteadOfGuessing() {
        String text = IcsfTestTokens.hex(importer);

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.TWO_ROW);

        assertEquals(1, entries.size());
        assertTrue(entries.get(0).failedToRead());
        assertTrue(entries.get(0).error().contains("pairs"));
    }

    // =====================================================================
    // The whole block as one stacked-hex token
    // =====================================================================
    @Test
    void autoRecognisesHexStackedOneBytePerLine() {
        StringBuilder stacked = new StringBuilder();
        for (byte value : importer) stacked.append(String.format("%02X%n", value & 0xFF));

        List<BatchEntry> entries = BatchReader.read(stacked.toString(), BatchInputFormat.AUTO);

        assertEquals(1, entries.size());
        assertEquals(BatchInputFormat.Resolved.BLOCK, entries.get(0).format());
        assertArrayEquals(importer, entries.get(0).data());
        assertEquals(64, entries.get(0).lineCount());
    }

    @Test
    void aNullTokenNeverOutscoresAReadingThatProducedRealTokens() {
        // Every high-digit row of a token starting X'02' reads as a string of zeros,
        // which parses as a null token. If a null scored the same as a real family,
        // the line reading would tie with, and beat, the correct two-row reading.
        String text = IcsfTestTokens.twoRows(importer);

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.AUTO);

        assertEquals(1, entries.size());
        assertEquals(BatchInputFormat.Resolved.TWO_ROW, entries.get(0).format());
        assertArrayEquals(importer, entries.get(0).data());
    }

    // =====================================================================
    // Labels versus hex separators
    // =====================================================================
    @Test
    void acceptsALabelAheadOfTheHex() {
        String hex = IcsfTestTokens.hex(importer);
        String text = "MY.KEY.01|" + hex + "\nOTHER," + hex + "\nNO.SEP " + hex;

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.LINE);

        assertEquals("MY.KEY.01", entries.get(0).label());
        assertEquals("OTHER", entries.get(1).label());
        assertEquals("NO.SEP", entries.get(2).label());
        for (BatchEntry entry : entries) assertArrayEquals(importer, entry.data());
    }

    @Test
    void aCommaBetweenHexBytesIsNotMistakenForALabel() {
        List<BatchEntry> entries = BatchReader.read("01,AF,02,03", BatchInputFormat.LINE);

        assertEquals("", entries.get(0).label());
        assertArrayEquals(new byte[]{0x01, (byte) 0xAF, 0x02, 0x03}, entries.get(0).data());
    }

    @Test
    void aColonBetweenHexBytesIsNotMistakenForALabelEither() {
        List<BatchEntry> entries = BatchReader.read("01:AF:02:03", BatchInputFormat.LINE);

        assertEquals("", entries.get(0).label());
        assertArrayEquals(new byte[]{0x01, (byte) 0xAF, 0x02, 0x03}, entries.get(0).data());
    }

    @Test
    void aLabelMadeOnlyOfHexDigitsNeedsAStrongSeparator() {
        String hex = IcsfTestTokens.hex(importer);

        // With ';' the label is accepted whatever it looks like.
        BatchEntry strong = BatchReader.read("ABCDEF;" + hex, BatchInputFormat.LINE).get(0);
        assertEquals("ABCDEF", strong.label());
        assertArrayEquals(importer, strong.data());

        // With ',' it cannot be told apart from hex, so it is treated as hex.
        BatchEntry weak = BatchReader.read("ABCDEF," + hex, BatchInputFormat.LINE).get(0);
        assertEquals("", weak.label());
        assertFalse(java.util.Arrays.equals(importer, weak.data()));
    }

    @Test
    void everyStrongSeparatorIsAccepted() {
        String hex = IcsfTestTokens.hex(importer);
        for (String separator : new String[]{"\t", "|", ";"}) {
            BatchEntry entry = BatchReader.read("LBL" + separator + hex, BatchInputFormat.LINE).get(0);
            assertEquals("LBL", entry.label(), "separator: " + separator);
            assertArrayEquals(importer, entry.data());
        }
    }

    @Test
    void theTwoRowReadingTakesTheLabelFromWhicheverRowCarriesIt() {
        String rows = IcsfTestTokens.twoRows(importer);
        String[] parts = rows.split("\n");
        String text = parts[0] + "\nTAGGED|" + parts[1];

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.TWO_ROW);

        assertEquals(1, entries.size());
        assertEquals("TAGGED", entries.get(0).label());
        assertArrayEquals(importer, entries.get(0).data());
    }

    @Test
    void splitLabelIsDirectlyTestableForTheAwkwardCases() {
        assertArrayEquals(new String[]{"", "01AF"}, BatchReader.splitLabel("01AF"));
        assertArrayEquals(new String[]{"A", "01AF"}, BatchReader.splitLabel("A|01AF"));
        assertArrayEquals(new String[]{"", "01,AF"}, BatchReader.splitLabel("01,AF"));
        assertArrayEquals(new String[]{"NAME", "01AF"}, BatchReader.splitLabel("NAME:01AF"));
        assertTrue(BatchReader.isHexish("0x01,0xAF"));
        assertFalse(BatchReader.isHexish("MY.KEY"));
        assertFalse(BatchReader.isHexish(""));
    }

    // =====================================================================
    // Reading failures
    // =====================================================================
    @Test
    void anUnreadableLineIsReportedWithoutStoppingTheBatch() {
        String text = IcsfTestTokens.hex(importer) + "\nZZZZ\n" + IcsfTestTokens.hex(aes);

        List<BatchEntry> entries = BatchReader.read(text, BatchInputFormat.LINE);

        assertEquals(3, entries.size());
        assertFalse(entries.get(0).failedToRead());
        assertTrue(entries.get(1).failedToRead());
        assertFalse(entries.get(2).failedToRead());
    }

    @Test
    void emptyInputYieldsNoEntries() {
        assertTrue(BatchReader.read("", BatchInputFormat.AUTO).isEmpty());
        assertTrue(BatchReader.read("   \n\n  ", BatchInputFormat.AUTO).isEmpty());
        assertTrue(BatchReader.read("# only a comment", BatchInputFormat.AUTO).isEmpty());
        assertTrue(BatchReader.read(null, BatchInputFormat.AUTO).isEmpty());
    }
}
