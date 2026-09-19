package com.cryptocarver.crypto.hsm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.Direction.COMMAND;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.Direction.RESPONSE;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.EvidenceStatus.PENDING_CAPTURE;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.FieldType.HEX;
import static com.cryptocarver.crypto.hsm.PayShieldBodySchema.FieldType.PRINTABLE_ASCII;

/** Registry of body layouts. A capture adds a schema row; parsing code stays unchanged. */
public final class PayShieldBodySchemas {
    private static final List<PayShieldBodySchema> SCHEMAS = List.of(
            new PayShieldBodySchema(
                    COMMAND,
                    "NC",
                    null,
                    PENDING_CAPTURE,
                    "NC-00",
                    List.of()),
            new PayShieldBodySchema(
                    RESPONSE,
                    "ND",
                    "00",
                    PENDING_CAPTURE,
                    "NC-00",
                    List.of(
                            new PayShieldBodySchema.Field(
                                    "lmkCheckValue", "LMK check value", 16, HEX),
                            new PayShieldBodySchema.Field(
                                    "firmwareVersion", "Firmware version", 9, PRINTABLE_ASCII))));

    static {
        Set<String> keys = new HashSet<>();
        for (PayShieldBodySchema schema : SCHEMAS) {
            String key = schema.direction() + ":" + schema.code() + ":"
                    + schema.errorCode() + ":" + schema.bodyLength();
            if (!keys.add(key)) {
                throw new IllegalStateException("Duplicate payShield body schema: " + key);
            }
        }
    }

    private PayShieldBodySchemas() {
    }

    public static List<PayShieldBodySchema> all() {
        return SCHEMAS;
    }

    public static List<PayShieldBodySchema> commands(String code) {
        return SCHEMAS.stream()
                .filter(schema -> schema.direction() == COMMAND)
                .filter(schema -> schema.code().equals(code))
                .toList();
    }

    public static List<PayShieldBodySchema> responses(String code, String errorCode) {
        return SCHEMAS.stream()
                .filter(schema -> schema.direction() == RESPONSE)
                .filter(schema -> schema.code().equals(code))
                .filter(schema -> schema.errorCode().equals(errorCode))
                .toList();
    }
}
