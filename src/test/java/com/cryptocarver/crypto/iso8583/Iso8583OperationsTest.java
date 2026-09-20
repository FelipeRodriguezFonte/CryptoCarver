package com.cryptocarver.crypto.iso8583;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Iso8583OperationsTest {
    @Test
    void binaryBitmapRoundTripPreservesCommonFields() {
        var p = Iso8583Operations.Profile.iso1987();
        byte[] wire = Iso8583Operations.build("0200", Map.of(3,"000000",11,"123456",41,"TERMID01"), p);
        var parsed = Iso8583Operations.parseBinary(wire,p);
        assertEquals("0200", parsed.mti().value());
        assertEquals("123456", parsed.field(11).orElseThrow().value());
        assertEquals("TERMID01", parsed.field(41).orElseThrow().value());
        assertTrue(parsed.warnings().isEmpty());
    }

    @Test
    void asciiHexBitmapIsExplicitAndDoesNotGetMistakenForBinary() {
        var p = Iso8583Operations.Profile.iso1987().withBitmap(Iso8583Operations.BitmapEncoding.ASCII_HEX);
        byte[] wire = Iso8583Operations.build("0800", Map.of(70,"001"), p);
        assertTrue(new String(wire,4,16, StandardCharsets.US_ASCII).matches("[0-9A-F]{16}"));
        assertEquals("001", Iso8583Operations.parse(wire,p).field(70).orElseThrow().value());
    }

    @Test
    void bcdLllvarUsesThePublishedPackedDecimalShape() {
        var p = Iso8583Operations.Profile.iso1987().withLength(Iso8583Operations.LengthEncoding.BCD);
        String value = "A".repeat(123);
        byte[] wire = Iso8583Operations.build("0200", Map.of(48,value), p);
        // LL/LLL indicators are packed BCD; 123 is 0x01 0x23 (not ASCII "123").
        assertEquals(0x01, wire[12] & 0xff);
        assertEquals(0x23, wire[13] & 0xff);
        assertEquals(value, Iso8583Operations.parseBinary(wire,p).field(48).orElseThrow().value());
    }

    @Test
    void tertiaryBitmapIsConsumedAndUnknownPrivateFieldIsReported() {
        var p = Iso8583Operations.Profile.iso1987();
        byte[] wire = new byte[4 + 24];
        System.arraycopy("0800".getBytes(StandardCharsets.US_ASCII), 0, wire, 0, 4);
        wire[4] = (byte) 0x80;       // secondary bitmap follows
        wire[12] = (byte) 0x80;      // tertiary bitmap follows
        wire[20] = (byte) 0x40;      // DE130 (tertiary bit 2)
        var parsed = Iso8583Operations.parseBinary(wire, p);
        assertTrue(parsed.warnings().stream().anyMatch(w -> w.contains("field 130")));
    }

    @Test
    void version1993SelectsItsFunctionCodeDefinition() {
        assertEquals("Network international identifier", Iso8583Operations.fieldDefinition(24, Iso8583Operations.Version.ISO_1987).name());
        assertEquals("Function code", Iso8583Operations.fieldDefinition(24, Iso8583Operations.Version.ISO_1993).name());
    }

    @Test
    void paymentAndEmvFieldsAreReportedThroughTheirExistingSpecialists() {
        var profile = Iso8583Operations.Profile.iso1987();
        byte[] wire = Iso8583Operations.build("0200", Map.of(
                35, "1234567890123456=25122011234567890",
                45, "%B1234567890123456^CARDHOLDER/TEST^25122011234567890?",
                52, "1234567890ABCDEF",
                55, "9F0206000000000100"), profile);

        String report = Iso8583Operations.parseBinary(wire, profile).report();
        assertTrue(report.contains("F035") && report.contains("Track 2 data"));
        assertTrue(report.contains("F045") && report.contains("Track 1 data"));
        assertTrue(report.contains("F052") && report.contains("Personal identification number data"));
        assertTrue(report.contains("F055") && report.contains("Amount, authorised"));
    }

    /** A local bitmap-boundary fixture, deliberately not presented as an external vector. */
    @Test
    void publishedNetworkManagementFixtureHasField70() {
        String fixture = "0800822000000000000004000000000000000810120000000200301";
        var parsed = Iso8583Operations.parseAsciiHex(fixture, Iso8583Operations.Profile.iso1987());
        assertEquals("0810120000", parsed.field(7).orElseThrow().value());
        assertEquals("000200", parsed.field(11).orElseThrow().value());
        assertEquals("301", parsed.field(70).orElseThrow().value());
    }

    /** Public, complete worked authorization fixture; values are checked against its table. */
    @Test
    void publicAuthorizationFixtureIsParsedValueByValue() {
        String fixture = "0100723C448028C08000164000001234567899000000000000005000081009302100012309302108102812541190200334000001234567899=2812101123450000622209000123TERM0001TESTMERCH000001840";
        var parsed = Iso8583Operations.parseAsciiHex(fixture, Iso8583Operations.Profile.iso1987());
        Map<Integer,String> expected = Map.ofEntries(Map.entry(2,"4000001234567899"),Map.entry(3,"000000"),Map.entry(4,"000000005000"),Map.entry(7,"0810093021"),Map.entry(11,"000123"),Map.entry(12,"093021"),Map.entry(13,"0810"),Map.entry(22,"902"),Map.entry(25,"00"),Map.entry(35,"4000001234567899=2812101123450000"),Map.entry(37,"622209000123"),Map.entry(41,"TERM0001"),Map.entry(42,"TESTMERCH000001"),Map.entry(49,"840"));
        expected.forEach((n,v) -> assertEquals(v, parsed.field(n).orElseThrow().value(), "DE" + n));
    }

    /** Public, complete worked financial fixture; includes binary DE52 in addition to the required set. */
    @Test
    void publicFinancialFixtureIsParsedValueByValue() {
        String fixture = "02007238448128C090001640000012345678990110000000000200000810093045000124093045081060119010006424242334000001234567899=2812101123450000622209000124ATM00001TESTATM000000018409A8B7C6D5E4F3210";
        var parsed = Iso8583Operations.parseAsciiHex(fixture, Iso8583Operations.Profile.iso1987());
        Map<Integer,String> expected = Map.ofEntries(Map.entry(2,"4000001234567899"),Map.entry(3,"011000"),Map.entry(4,"000000020000"),Map.entry(7,"0810093045"),Map.entry(11,"000124"),Map.entry(12,"093045"),Map.entry(13,"0810"),Map.entry(22,"901"),Map.entry(25,"00"),Map.entry(35,"4000001234567899=2812101123450000"),Map.entry(37,"622209000124"),Map.entry(41,"ATM00001"),Map.entry(42,"TESTATM00000001"),Map.entry(49,"840"),Map.entry(52,"9A8B7C6D5E4F3210"));
        expected.forEach((n,v) -> assertEquals(v, parsed.field(n).orElseThrow().value(), "DE" + n));
    }

    @Test
    void allPublicDataTypesAndMtiComponentsAreExercised() {
        var p = Iso8583Operations.Profile.iso1987();
        byte[] wire = Iso8583Operations.build("0200", Map.of(3,"000000",28,"ABC123456",41,"TERM0001",35,"1234D2512",52,"0123456789ABCDEF"), p);
        var parsed = Iso8583Operations.parseBinary(wire, p);
        assertEquals("financial message", parsed.mti().className());
        assertEquals("request", parsed.mti().functionName());
        assertEquals("acquirer", parsed.mti().originName());
        assertEquals("ABC123456", parsed.field(28).orElseThrow().value());
        assertEquals("1234D2512", parsed.field(35).orElseThrow().value());
        assertEquals("0123456789ABCDEF", parsed.field(52).orElseThrow().value());
    }

    @Test
    void sameLlvarFieldSupportsAsciiAndBcdLengthIndicators() {
        String value = "1234567890123456";
        var ascii = Iso8583Operations.Profile.iso1987().withLength(Iso8583Operations.LengthEncoding.ASCII);
        var bcd = Iso8583Operations.Profile.iso1987().withLength(Iso8583Operations.LengthEncoding.BCD);
        assertEquals(value, Iso8583Operations.parseBinary(Iso8583Operations.build("0100", Map.of(2,value), ascii), ascii).field(2).orElseThrow().value());
        assertEquals(value, Iso8583Operations.parseBinary(Iso8583Operations.build("0100", Map.of(2,value), bcd), bcd).field(2).orElseThrow().value());
    }

    @Test
    void truncatedLengthAndMaximumExceededAreRejected() {
        var p = Iso8583Operations.Profile.iso1987();
        assertThrows(IllegalArgumentException.class, () -> Iso8583Operations.parseBinary(new byte[]{'0','1','0','0',0x40,0,0,0,0,0,0,0,'1'}, p));
        assertThrows(IllegalArgumentException.class, () -> Iso8583Operations.build("0100", Map.of(2,"1".repeat(20)), p));
    }

    @Test
    void profileAndBitmapControlFieldsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> Iso8583Operations.build("0100", Map.of(3,"000000"), Iso8583Operations.Profile.iso1993()));
        for (int control : new int[]{1,65,129}) {
            assertThrows(IllegalArgumentException.class, () -> Iso8583Operations.build("0100", Map.of(control,"x"), Iso8583Operations.Profile.iso1987()));
        }
    }

    @Test
    void parsingAndBuildingUseTheSelected1993Dictionary() {
        var p = Iso8583Operations.Profile.iso1993();
        byte[] wire = Iso8583Operations.build("1200", Map.of(24,"123"), p);
        var field = Iso8583Operations.parseBinary(wire, p).field(24).orElseThrow();
        assertEquals("Function code", field.definition().name());
        assertEquals("123", field.value());
    }
}
