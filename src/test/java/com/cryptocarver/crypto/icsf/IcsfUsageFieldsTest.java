package com.cryptocarver.crypto.icsf;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The key-usage and key-management fields of a variable-length token, read byte by
 * byte against Tables 619-629 of the ICSF Application Programmer's Guide (HCR77E0).
 *
 * <p>Written after an EXPORTER whose restriction lived entirely in its usage fields —
 * EXPTT31D, VARDRV-D, WR-AES only — came out of the analyser as a bare
 * {@code 010001004000F800}: KUF1 and KUF2 were not read at all, and no low-order
 * byte was ever shown.</p>
 */
class IcsfUsageFieldsTest {

    private static final Locale ENGLISH = Locale.ENGLISH;

    /** Every decoded byte of one section, by offset, rendered in English. */
    private static Map<Integer, String> readings(ParseResult result, String sectionTitleKey) {
        IcsfSection section = result.sections().stream()
                .filter(candidate -> sectionTitleKey.equals(candidate.title().key()))
                .findFirst().orElseThrow();
        return section.fields().stream()
                .filter(field -> field.length() == 1)
                .collect(Collectors.toMap(IcsfSection.Field::offset,
                        field -> IcsfMessages.resolve(field.name(), ENGLISH) + " | "
                                + IcsfMessages.resolve(field.value(), ENGLISH)));
    }

    private static Map<Integer, String> usage(ParseResult result) {
        return readings(result, "icsf.section.permittedUses");
    }

    private static ParseResult parse(int algorithm, int keyType, String usage, String management) {
        ParseResult result = IcsfTokenParser.parse(
                IcsfTestTokens.variableLength(algorithm, keyType, usage, management));
        assertTrue(result.isOk(), result.error());
        return result;
    }

    @Test
    void readsEveryByteOfTheExptt31dExporter() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.hex(IcsfTestTokens.AES_EXPTT31D_EXPORTER));
        Map<Integer, String> bytes = usage(result);

        assertEquals(8, bytes.size(), "one reading per byte of the four usage fields: " + bytes);
        assertTrue(bytes.get(45).startsWith("KUF1 high-order byte (operations) | B'0000 0001' = EXPTT31D"),
                bytes.get(45));
        assertTrue(bytes.get(46).contains("KUF1 low-order byte (UDX control)"), bytes.get(46));
        assertTrue(bytes.get(46).contains("both in CCA and in UDXs"), bytes.get(46));
        assertTrue(bytes.get(47).contains("B'0000 0001' = VARDRV-D"), bytes.get(47));
        assertTrue(bytes.get(48).contains("not set: KEK-RAW"), bytes.get(48));
        assertTrue(bytes.get(49).contains("B'0100 0000' = WR-AES"), bytes.get(49));
        assertTrue(bytes.get(49).contains("not set: WR-DES, WR-HMAC, WR-RSA, WR-ECC, WR-QSA"), bytes.get(49));
        assertTrue(bytes.get(51).contains("B'1111 1000' = WR-DATA + WR-KEK + WR-PIN + WRDERIVE + WR-CARD"),
                bytes.get(51));
        assertTrue(bytes.get(51).contains("not set: WR-CVAR"), bytes.get(51));

        assertEquals("EXPTT31D, VARDRV-D, WR-AES, WR-DATA, WR-KEK, WR-PIN, WRDERIVE, WR-CARD",
                result.value(SummaryKey.ALLOWED_USES).orElseThrow().text());
        assertEquals("DECODED", result.code(SummaryKey.ALLOWED_USES, ""));
        assertTrue(result.warnings().isEmpty(), "a well-formed token: " + result.warnings());
    }

    @Test
    void readsTheManagementBytesWithTheirKeywords() {
        ParseResult result = IcsfTokenParser.parse(IcsfTestTokens.hex(IcsfTestTokens.AES_EXPTT31D_EXPORTER));
        Map<Integer, String> bytes = readings(result, "icsf.section.management");

        assertTrue(bytes.get(54).contains("B'1111 0000' = XPRT-SYM + XPRTUASY + XPRTAASY + XPRT-RAW"),
                bytes.get(54));
        assertTrue(bytes.get(55).contains("XPRT-DES + XPRT-AES + XPRT-RSA"), bytes.get(55));
        assertTrue(bytes.get(56).contains("complete: no more parts can be added"), bytes.get(56));
        // Defaults are shown in the detail, but they are not uses of the key.
        assertFalse(result.value(SummaryKey.ALLOWED_USES).orElseThrow().text().contains("XPRT"));
    }

    @Test
    void theReportKeepsMultiLineReadingsUnderTheirArrow() {
        byte[] token = IcsfTestTokens.hex(IcsfTestTokens.AES_EXPTT31D_EXPORTER);
        String report = IcsfTokenReport.renderText(IcsfTokenParser.parse(token, Origin.INFER), Origin.INFER,
                token, Locale.forLanguageTag("es"));

        assertTrue(report.contains("[off 49   len 1  ] KUF3 byte alto (algoritmos envolvibles)"), report);
        assertTrue(report.contains("-> B'0100 0000' = WR-AES" + System.lineSeparator()
                + "         · WR-AES: puede envolver claves AES"), report);
    }

    @Test
    void vardrvDWithoutExptt31dIsAnInvalidCombination() {
        // EXPORT alone in KUF1, VARDRV-D in KUF2.
        ParseResult result = parse(0x02, 0x0003, "8000010040008000", "F00000000505");

        assertTrue(result.warned(DiagnosticCode.USAGE_FIELD_COMBINATION_INVALID));
        assertTrue(usage(result).get(47).contains("VARDRV-D is only valid"), usage(result).get(47));
    }

    @Test
    void exptt31dCannotBeCombinedWithAnotherOperation() {
        ParseResult result = parse(0x02, 0x0003, "8100010040008000", "F00000000505");

        assertTrue(result.warned(DiagnosticCode.USAGE_FIELD_COMBINATION_INVALID));
        assertTrue(usage(result).get(45).contains("EXPTT31D (B'0000 0001') cannot be combined"),
                usage(result).get(45));
    }

    @Test
    void wrTr31IsNotValidWithTheKeyBlockBindingMethod() {
        ParseResult result = parse(0x02, 0x0004, "0100800040008000", "F00000000505");

        assertTrue(result.warned(DiagnosticCode.USAGE_FIELD_COMBINATION_INVALID));
        assertTrue(usage(result).get(45).contains("IMPTT31D"), usage(result).get(45));
    }

    @Test
    void reservedBitsAreShownAndWarnedAbout() {
        // KUF3 low-order byte must be zero; KUF4 high-order byte bit 7 is reserved.
        ParseResult result = parse(0x02, 0x0003, "8000000040018100", "F00000000505");

        assertTrue(result.warned(DiagnosticCode.USAGE_FIELD_RESERVED_BITS));
        assertTrue(usage(result).get(50).contains("reserved bits set: B'0000 0001'"), usage(result).get(50));
        assertTrue(usage(result).get(51).contains("reserved bits set: B'0000 0001'"), usage(result).get(51));
    }

    @Test
    void anUndefinedCipherModeIsWarnedAbout() {
        ParseResult result = parse(0x02, 0x0001, "C0000900", "80000000");

        assertTrue(result.warned(DiagnosticCode.USAGE_FIELD_UNDEFINED_VALUE));
        assertTrue(usage(result).get(47).contains("X'09' is not a value this table defines"), usage(result).get(47));
    }

    @Test
    void aKeyUsageFieldCountTheTableDoesNotAllowIsWarnedAbout() {
        ParseResult result = parse(0x02, 0x0003, "80000000", "F00000000505");

        assertTrue(result.warned(DiagnosticCode.USAGE_FIELD_COUNT_UNEXPECTED));
        // What is there is still read.
        assertTrue(usage(result).get(45).contains("= EXPORT"), usage(result).get(45));
    }

    @Test
    void pinprotOperationBitsDependOnTheDirection() {
        // Outbound (ENCRYPT): B'0010 0000' is CPINENC. Inbound (DECRYPT): B'0001 0000' is EPINVER.
        ParseResult outbound = parse(0x02, 0x0005, "8000002000000100", "80000000");
        ParseResult inbound = parse(0x02, 0x0005, "4000001000000100", "80000000");

        assertTrue(usage(outbound).get(45).contains("= ENCRYPT"), usage(outbound).get(45));
        assertTrue(usage(outbound).get(48).contains("= CPINENC"), usage(outbound).get(48));
        assertTrue(usage(outbound).get(51).contains("= ISO-4"), usage(outbound).get(51));
        assertTrue(usage(inbound).get(48).contains("= EPINVER"), usage(inbound).get(48));
        assertTrue(outbound.warnings().isEmpty(), outbound.warnings().toString());

        // The same bit on the inbound key is not defined.
        ParseResult wrong = parse(0x02, 0x0005, "4000002000000100", "80000000");
        assertTrue(wrong.warned(DiagnosticCode.USAGE_FIELD_RESERVED_BITS));
    }

    @Test
    void aDkEnabledPinprotHasThreeFieldsAndItsCommonControl() {
        ParseResult result = parse(0x02, 0x0005, "8000003C0201", "80000000");

        assertTrue(usage(result).get(49).contains("= DKPINOPP"), usage(result).get(49));
        assertTrue(usage(result).get(50).contains("DK enabled"), usage(result).get(50));
        assertFalse(result.warned(DiagnosticCode.USAGE_FIELD_COUNT_UNEXPECTED), result.warnings().toString());
    }

    @Test
    void aDkygenkyReadsItsRelatedFieldsWithTheTableOfTheKeyItGenerates() {
        // D-CIPHER, KUF-MBE, DKYL0; related fields: ENCRYPT, then GCM.
        ParseResult result = parse(0x02, 0x0009, "010080008000" + "0400", "80000000");
        Map<Integer, String> bytes = usage(result);

        assertTrue(bytes.get(45).contains("= D-CIPHER"), bytes.get(45));
        assertTrue(bytes.get(47).contains("= KUF-MBE + KMF-GND + KMF-GND2"), bytes.get(47));
        assertTrue(bytes.get(48).contains("= DKYL0"), bytes.get(48));
        assertTrue(bytes.get(49).contains("related KUF1 of CIPHER"), bytes.get(49));
        assertTrue(bytes.get(49).contains("= ENCRYPT"), bytes.get(49));
        assertTrue(bytes.get(51).contains("= GCM"), bytes.get(51));
        // What it may derive is not what it may do.
        assertEquals("D-CIPHER, KUF-MBE, DKYL0", result.value(SummaryKey.ALLOWED_USES).orElseThrow().text());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test
    void anHmacKeyListsItsHashMethods() {
        ParseResult result = parse(0x03, 0x0002, "C0002800", "80000000");

        assertTrue(usage(result).get(45).contains("= GENERATE + VERIFY"), usage(result).get(45));
        assertTrue(usage(result).get(47).contains("= SHA-256 + SHA-512"), usage(result).get(47));
    }

    @Test
    void aKeyTypeWithoutATableStillShowsEveryBit() {
        ParseResult result = parse(0x02, 0x0055, "1234", "80000000");

        assertTrue(usage(result).get(45).contains("B'0001 0010'"), usage(result).get(45));
        assertTrue(usage(result).get(46).contains("no decoding table"), usage(result).get(46));
    }

    @Test
    void kdkgenkyBlocksAreShownWholeRatherThanMisread() {
        ParseResult result = parse(0x02, 0x000B, "0100" + "00".repeat(24), "80000000");
        IcsfSection section = result.sections().stream()
                .filter(candidate -> "icsf.section.permittedUses".equals(candidate.title().key()))
                .findFirst().orElseThrow();
        Map<Integer, IcsfSection.Field> byOffset = section.fields().stream()
                .collect(Collectors.toMap(IcsfSection.Field::offset, Function.identity(), (a, b) -> b));

        assertTrue(IcsfMessages.resolve(byOffset.get(45).value(), ENGLISH).contains("= KDKTYPEB"));
        assertEquals(24, byOffset.get(47).length());
        assertFalse(result.warned(DiagnosticCode.USAGE_FIELD_COUNT_UNEXPECTED));
    }

    @Test
    void bitPatternsAreWrittenTheWayTheManualWritesThem() {
        assertEquals("B'0000 0001'", VariableFieldDecoder.binary(0x01));
        assertEquals("B'1111 1000'", VariableFieldDecoder.binary(0xF8));
        assertEquals(List.of("B'0100 0000'"), List.of(VariableFieldDecoder.binary(0x40)));
    }
}
