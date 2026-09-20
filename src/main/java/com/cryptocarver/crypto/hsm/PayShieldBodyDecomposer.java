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
        if (body.length != schema.bodyLength()) {
            return Optional.empty();
        }

        List<DecodedField> decoded = new ArrayList<>();
        int offset = 0;
        for (PayShieldBodySchema.Field field : schema.fields()) {
            String value = new String(body, offset, field.length(), StandardCharsets.US_ASCII);
            if (!field.type().accepts(value)) {
                return Optional.empty();
            }
            decoded.add(new DecodedField(field, value));
            offset += field.length();
        }
        return Optional.of(new Decomposition(schema, decoded));
    }
}
