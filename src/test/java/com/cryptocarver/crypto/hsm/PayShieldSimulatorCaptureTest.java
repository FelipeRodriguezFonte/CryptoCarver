package com.cryptocarver.crypto.hsm;

import com.cryptocarver.crypto.PaymentOperations;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayShieldSimulatorCaptureTest {
    private final PayShieldMessageCodec codec = new PayShieldMessageCodec(8, true);
    private static final HexFormat HEX = HexFormat.of();

    record Capture(boolean command, String prefix, String ascii, Map<String, String> fields) { }

    @Test
    void everyCapturedFrameIsDecodedAndMatchedFieldByField() {
        List<Capture> captures = List.of(
                c("000F", "00000000A00000U", Map.of("mode","0","keyType","000","scheme","U")),
                r("0033", "00000000A100UF95168C4319FCCC3F9577272B7FDF14E36CBAB", Map.of("key","UF95168C4319FCCC3F9577272B7FDF14E","kcv","36CBAB")),
                c("002E", "00000000BU001U294E6024662EB037097C4C8D5CEEA6ED", Map.of("keyType","00","lengthIndicator","1","key","U294E6024662EB037097C4C8D5CEEA6ED")),
                r("0012", "00000000BV00BB7158", Map.of("kcv","BB7158")),
                c("0043", "00000000CWUEB0F2056EDC79C4BFB52B5B4D3E68D881234567890123456;1510109", Map.of("scheme","U","cvkA","EB0F2056EDC79C4B","cvkB","FB52B5B4D3E68D88","pan","1234567890123456","delimiter",";","expiry","1510","serviceCode","109")),
                r("000F", "00000000CX00079", Map.of("cvv","079")),
                c("0046", "00000000CYUEB0F2056EDC79C4BFB52B5B4D3E68D886841234567890123456;1510109", Map.of("cvk","UEB0F2056EDC79C4BFB52B5B4D3E68D88","cvv","684","pan","1234567890123456","delimiter",";","expiry","1510","serviceCode","109")),
                r("000C", "00000000CZ01", Map.of()),
                c("0046", "00000000CYUEB0F2056EDC79C4BFB52B5B4D3E68D880791234567890123456;1510109", Map.of("cvk","UEB0F2056EDC79C4BFB52B5B4D3E68D88","cvv","079","pan","1234567890123456","delimiter",";","expiry","1510","serviceCode","109")),
                r("000C", "00000000CZ00", Map.of()),
                c("000A", "00000000NC", Map.of()),
                r("0025", "00000000ND0008D7B4FB629D08850007-E000", Map.of("lmkCheckValue","08D7B4FB629D0885","firmwareVersion","0007-E000")),
                c("0050", "00000000A6000U1BF1879107A29B475E07CB594A8D67A4XB38BBEBCEE6C5A5484393BBCC4F0D9F8U", Map.of("keyType","000","zmk","U1BF1879107A29B475E07CB594A8D67A4","keyUnderZmk","XB38BBEBCEE6C5A5484393BBCC4F0D9F8","scheme","U")),
                r("0033", "00000000A700UA987CC2719103EB11FCA7463273B2A6ED6A020", Map.of("key","UA987CC2719103EB11FCA7463273B2A6E","kcv","D6A020")),
                c("0050", "00000000A8002UBB839220AE2F70A754F05D356107D6E3U98DCBCBB630FF4831E05A912D1B042C8U", Map.of("keyType","002","zmkOrTmk","UBB839220AE2F70A754F05D356107D6E3","key","U98DCBCBB630FF4831E05A912D1B042C8","scheme","U")));
        for (Capture capture : captures) {
            byte[] frame = frame(capture);
            PayShieldBodyDecomposer.Decomposition actual = capture.command
                    ? PayShieldBodyDecomposer.decompose(codec.parseCommand(frame)).orElseThrow()
                    : PayShieldBodyDecomposer.decompose(codec.parseResponse(frame)).orElseThrow();
            assertEquals(capture.fields.size(), actual.fields().size(), capture.ascii);
            capture.fields.forEach((name, expected) -> assertEquals(expected, actual.value(name).orElseThrow(), capture.ascii + ":" + name));
            assertNotNull(actual.schema().capturedBody());
            assertNotEquals(PayShieldBodySchema.EvidenceStatus.VERIFIED, actual.schema().evidenceStatus());
        }
    }

    @Test
    void everyOtherLiteralFrameDecodesButHasNoBodySchema() {
        List<Capture> opaque = List.of(
                c("000C", "00000000NO00", Map.of()), r("0020", "00000000NP001101280007-E00000000", Map.of()),
                c("0061", "00000000EEU8AC91C79495A9FC3021AE502DDEDD9800000FFFFFFFF0409541092192939C3DE67FC7B2B2B07006859718N", Map.of()), r("000C", "00000000EF30", Map.of()),
                c("007B", "00000000CAU8EEB4727D594D80799EE7394B5B33DC9UA045EA2A914D7A950C3052AFCF151261129BBBE380C3E5D2400101987654321098;987654321098", Map.of()), r("000C", "00000000CB15", Map.of()),
                c("007B", "00000000CCU00DEB679DB51D99B53A78112D755769BUA045EA2A914D7A950C3052AFCF151261129BBBE380C3E5D2400101987654321098;987654321098", Map.of()), r("000C", "00000000CD15", Map.of()),
                c("0103", "00000000M601031003U0D48F907F0DC6B8E7CD333317595FCC600CC02107238000102C000111670343010001006660000000000000000000912065731000092155726091206703400393138303030303230343030303133303039373334202020203032390301000000000020202020202020200000000000000000020000000000", Map.of()), r("000C", "00000000M715", Map.of()),
                r("000C", "00000000A930", Map.of()));
        for (Capture capture : opaque) {
            byte[] frame = frame(capture);
            assertTrue(capture.command
                    ? PayShieldBodyDecomposer.decompose(codec.parseCommand(frame)).isEmpty()
                    : PayShieldBodyDecomposer.decompose(codec.parseResponse(frame)).isEmpty(), capture.ascii);
        }
    }

    @Test
    void everyRegisteredCaptureIsCompleteAndNoRowClaimsRealHardware() {
        assertEquals(14, PayShieldBodySchemas.all().size());
        PayShieldBodySchemas.validate(PayShieldBodySchemas.all());
        for (PayShieldBodySchema schema : PayShieldBodySchemas.all()) {
            assertNotEquals(PayShieldBodySchema.EvidenceStatus.VERIFIED, schema.evidenceStatus());
            assertNotNull(schema.capturedBody());
            assertTrue(schema.evidenceId().startsWith("SIM-"));
            assertTrue(PayShieldBodyDecomposer.decompose(schema, schema.capturedBody()).isPresent(), schema.evidenceId());
        }
        for (PayShieldBodySchema.EvidenceStatus status : List.of(
                PayShieldBodySchema.EvidenceStatus.EXTERNAL_REQUEST,
                PayShieldBodySchema.EvidenceStatus.THIRD_PARTY_SIMULATOR)) {
            assertThrows(IllegalArgumentException.class, () -> new PayShieldBodySchema(
                    PayShieldBodySchema.Direction.COMMAND, "ZZ", null, status, "SIM-TEST", null, List.of()));
        }
    }

    @Test
    void schemeLengthsAndUnknownSchemeAreExplicit() {
        PayShieldBodySchema schema = new PayShieldBodySchema(PayShieldBodySchema.Direction.COMMAND, "ZZ", null,
                PayShieldBodySchema.EvidenceStatus.EXTERNAL_REQUEST, "SIM-TEST", "Z0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII),
                List.of(new PayShieldBodySchema.Field("key", "key", 0, PayShieldBodySchema.FieldType.SCHEME_KEY)));
        for (String key : List.of("Z0123456789ABCDEF", "U0123456789ABCDEF0123456789ABCDEF", "X0123456789ABCDEF0123456789ABCDEF",
                "T0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF", "Y0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")) {
            assertEquals(key, PayShieldBodyDecomposer.decompose(schema, key.getBytes(StandardCharsets.US_ASCII)).orElseThrow().value("key").orElseThrow());
        }
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PayShieldBodyDecomposer.decompose(schema, "Q0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII))).getMessage().contains("Unknown payShield key scheme"));
    }

    @Test
    void cryptographicCrossChecksMatchCapturedValues() throws Exception {
        String lmk = "0123456789ABCDEFFEDCBA9876543210";
        assertEquals("08D7B4FB629D0885", encrypt(lmk, "0000000000000000"));
        String a0 = decrypt(lmk, "F95168C4319FCCC3F9577272B7FDF14E");
        String bu = decrypt(lmk, "294E6024662EB037097C4C8D5CEEA6ED");
        String cw = decrypt(lmk, "EB0F2056EDC79C4BFB52B5B4D3E68D88");
        assertEquals("AD910110291FE6349DA438A1E6203E40", a0);
        assertEquals("52664598B1734B56665BF777B66E0923", bu);
        assertEquals("36CBAB", encrypt(a0, "0000000000000000").substring(0, 6));
        assertEquals("BB7158", encrypt(bu, "0000000000000000").substring(0, 6));
        assertEquals("1696CECE6555717F07873F6AD8F46726", cw);
        assertEquals("079", PaymentOperations.generateCVV(cw.substring(0, 16), cw.substring(16), "1234567890123456", "1510", "109"));
    }

    private static String encrypt(String key, String data) throws Exception { return cipher(Cipher.ENCRYPT_MODE, key, data); }
    private static String decrypt(String key, String data) throws Exception { return cipher(Cipher.DECRYPT_MODE, key, data); }
    private static String cipher(int mode, String key, String data) throws Exception {
        byte[] k = HEX.parseHex(key);
        byte[] expanded = new byte[24];
        System.arraycopy(k, 0, expanded, 0, 16);
        System.arraycopy(k, 0, expanded, 16, 8);
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(mode, new SecretKeySpec(expanded, "DESede"));
        return HEX.withUpperCase().formatHex(cipher.doFinal(HEX.parseHex(data)));
    }
    private static Capture c(String prefix, String ascii, Map<String,String> fields) { return new Capture(true,prefix,ascii,fields); }
    private static Capture r(String prefix, String ascii, Map<String,String> fields) { return new Capture(false,prefix,ascii,fields); }
    private static byte[] frame(Capture capture) {
        byte[] text = capture.ascii.getBytes(StandardCharsets.US_ASCII);
        assertEquals(text.length, Integer.parseInt(capture.prefix, 16), capture.ascii);
        byte[] result = new byte[text.length + 2];
        System.arraycopy(HEX.parseHex(capture.prefix),0,result,0,2);
        System.arraycopy(text,0,result,2,text.length);
        return result;
    }
}
