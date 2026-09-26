package com.cryptocarver.crypto.hsm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Applies exact declarative schemas without guessing from partial matches. */
public final class PayShieldBodyDecomposer {
    public record DecodedField(PayShieldBodySchema.Field definition, String value) {
    }

    public record Decomposition(PayShieldBodySchema schema, List<DecodedField> fields) {
        public Decomposition {
            fields = List.copyOf(fields);
        }

        public Optional<String> value(String fieldName) {
            return fields.stream()
                    .filter(field -> field.definition().name().equals(fieldName))
                    .map(DecodedField::value)
                    .findFirst();
        }
    }

    private PayShieldBodyDecomposer() {
    }

    public static Optional<Decomposition> decompose(PayShieldMessage command) {
        return firstExactMatch(PayShieldBodySchemas.commands(command.code()), command.body());
    }

    public static Optional<Decomposition> decompose(PayShieldResponse response) {
        return firstExactMatch(
                PayShieldBodySchemas.responses(response.responseCode(), response.errorCode()),
                response.data());
    }

    private static Optional<Decomposition> firstExactMatch(
            List<PayShieldBodySchema> candidates,
            byte[] body) {
        // PayShieldBodySchemas rejects ambiguous direction/code/error/length keys at startup.
        return candidates.stream()
                .map(schema -> decompose(schema, body))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Length and every declared type must match before any field is returned.
     * This all-or-nothing rule is what prevents a nearby firmware shape from
     * being presented as if it were the captured one.
     */
    static Optional<Decomposition> decompose(PayShieldBodySchema schema, byte[] body) {
        if (schema.bodyLength() >= 0 && body.length != schema.bodyLength()) {
            return Optional.empty();
        }

        List<DecodedField> decoded = new ArrayList<>();
        int offset = 0;
        for (PayShieldBodySchema.Field field : schema.fields()) {
            int length = field.length();
            if (field.type() == PayShieldBodySchema.FieldType.SCHEME_KEY) {
                if (offset >= body.length) return Optional.empty();
                char scheme = (char) body[offset];
                length = switch (scheme) {
                    case 'U', 'X' -> 33;
                    case 'T', 'Y' -> 49;
                    case 'Z' -> 17;
                    default -> throw new IllegalArgumentException("Unknown payShield key scheme: " + scheme);
                };
            } else if (field.type() == PayShieldBodySchema.FieldType.UNTIL_SEMICOLON) {
                int end = offset;
                while (end < body.length && body[end] != ';') end++;
                if (end == body.length) return Optional.empty();
                length = end - offset;
            }
            if (offset + length > body.length) return Optional.empty();
            String value = new String(body, offset, length, StandardCharsets.US_ASCII);
            if (!field.type().accepts(value)) return Optional.empty();
            decoded.add(new DecodedField(field, value));
            offset += length;
        }
        return offset == body.length ? Optional.of(new Decomposition(schema, decoded)) : Optional.empty();
    }
}
