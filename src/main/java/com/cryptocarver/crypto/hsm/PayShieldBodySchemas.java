package com.cryptocarver.crypto.hsm;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.Direction.COMMAND;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.Direction.RESPONSE;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.EvidenceStatus.EXTERNAL_REQUEST;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.EvidenceStatus.THIRD_PARTY_SIMULATOR;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.FieldType.*;

/** Registry of captured body layouts. Parsing remains declarative and offline. */
public final class PayShieldBodySchemas {
    private static final List<PayShieldBodySchema> SCHEMAS = List.of(
            command("A0", "SIM-A0-01", "0000U", f("mode", 1, DECIMAL), f("keyType", 3, DECIMAL), f("scheme", 1, PRINTABLE_ASCII)),
            response("A1", "SIM-A1-01", "UF95168C4319FCCC3F9577272B7FDF14E36CBAB", f("key", 0, SCHEME_KEY), f("kcv", 6, HEX)),
            command("BU", "SIM-BU-01", "001U294E6024662EB037097C4C8D5CEEA6ED", f("keyType", 2, DECIMAL), f("lengthIndicator", 1, DECIMAL), f("key", 0, SCHEME_KEY)),
            response("BV", "SIM-BV-01", "BB7158", f("kcv", 6, HEX)),
            command("CW", "SIM-CW-01", "UEB0F2056EDC79C4BFB52B5B4D3E68D881234567890123456;1510109", f("scheme", 1, PRINTABLE_ASCII), f("cvkA", 16, HEX), f("cvkB", 16, HEX), f("pan", 0, UNTIL_SEMICOLON), f("delimiter", 1, SEMICOLON), f("expiry", 4, DECIMAL), f("serviceCode", 3, DECIMAL)),
            response("CX", "SIM-CX-01", "079", f("cvv", 3, DECIMAL)),
            command("CY", "SIM-CY-01", "UEB0F2056EDC79C4BFB52B5B4D3E68D886841234567890123456;1510109", f("cvk", 0, SCHEME_KEY), f("cvv", 3, DECIMAL), f("pan", 0, UNTIL_SEMICOLON), f("delimiter", 1, SEMICOLON), f("expiry", 4, DECIMAL), f("serviceCode", 3, DECIMAL)),
            response("CZ", "01", "SIM-CZ-01", ""),
            response("CZ", "00", "SIM-CZ-02", ""),
            command("NC", "SIM-NC-01", ""),
            response("ND", "SIM-ND-01", "08D7B4FB629D08850007-E000", f("lmkCheckValue", 16, HEX), f("firmwareVersion", 9, PRINTABLE_ASCII)),
            command("A6", "SIM-A6-01", "000U1BF1879107A29B475E07CB594A8D67A4XB38BBEBCEE6C5A5484393BBCC4F0D9F8U", f("keyType", 3, DECIMAL), f("zmk", 0, SCHEME_KEY), f("keyUnderZmk", 0, SCHEME_KEY), f("scheme", 1, PRINTABLE_ASCII)),
            response("A7", "SIM-A7-01", "UA987CC2719103EB11FCA7463273B2A6ED6A020", f("key", 0, SCHEME_KEY), f("kcv", 6, HEX)),
            command("A8", "SIM-A8-01", "002UBB839220AE2F70A754F05D356107D6E3U98DCBCBB630FF4831E05A912D1B042C8U", f("keyType", 3, DECIMAL), f("zmkOrTmk", 0, SCHEME_KEY), f("key", 0, SCHEME_KEY), f("scheme", 1, PRINTABLE_ASCII)));

    static { validate(SCHEMAS); }

    private static PayShieldBodySchema.Field f(String name, int length, PayShieldBodySchema.FieldType type) {
        return new PayShieldBodySchema.Field(name, name, length, type);
    }

    private static PayShieldBodySchema command(String code, String id, String body, PayShieldBodySchema.Field... fields) {
        return new PayShieldBodySchema(COMMAND, code, null, EXTERNAL_REQUEST, id,
                body.getBytes(StandardCharsets.US_ASCII), List.of(fields));
    }

    private static PayShieldBodySchema response(String code, String id, String body, PayShieldBodySchema.Field... fields) {
        return response(code, "00", id, body, fields);
    }

    private static PayShieldBodySchema response(String code, String error, String id, String body, PayShieldBodySchema.Field... fields) {
        return new PayShieldBodySchema(RESPONSE, code, error, THIRD_PARTY_SIMULATOR, id,
                body.getBytes(StandardCharsets.US_ASCII), List.of(fields));
    }

    static void validate(List<PayShieldBodySchema> schemas) {
        Set<String> keys = new HashSet<>();
        for (PayShieldBodySchema schema : schemas) {
            String key = schema.direction() + ":" + schema.code() + ":"
                    + schema.errorCode() + ":" + schema.bodyLength();
            String matchKey = schema.direction() + ":" + schema.code() + ":" + schema.errorCode();
            if (!keys.add(matchKey)) throw new IllegalStateException("Duplicate or ambiguous payShield body schema: " + key);
        }
    }

    private PayShieldBodySchemas() { }

    public static List<PayShieldBodySchema> all() { return SCHEMAS; }

    public static List<PayShieldBodySchema> commands(String code) {
        return SCHEMAS.stream().filter(s -> s.direction() == COMMAND && s.code().equals(code)).toList();
    }

    public static List<PayShieldBodySchema> responses(String code, String errorCode) {
        return SCHEMAS.stream().filter(s -> s.direction() == RESPONSE
                && s.code().equals(code) && s.errorCode().equals(errorCode)).toList();
    }
}
