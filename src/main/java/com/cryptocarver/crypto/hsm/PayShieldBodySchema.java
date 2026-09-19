package com.cryptocarver.crypto.hsm;

import java.util.List;

/** Declarative description of one exact command or response body shape. */
public record PayShieldBodySchema(
        Direction direction,
        String code,
        String errorCode,
        EvidenceStatus evidenceStatus,
        String evidenceId,
        List<Field> fields) {

    public enum Direction {
        COMMAND,
        RESPONSE
    }

    public enum EvidenceStatus {
        PENDING_CAPTURE,
        VERIFIED
    }

    public enum FieldType {
        PRINTABLE_ASCII {
            @Override
            boolean accepts(String value) {
                return value.chars().allMatch(character -> character >= 0x20 && character <= 0x7e);
            }
        },
        HEX {
            @Override
            boolean accepts(String value) {
                return value.matches("[0-9A-Fa-f]*");
            }
        },
        DECIMAL {
            @Override
            boolean accepts(String value) {
                return value.matches("[0-9]*");
            }
        };

        abstract boolean accepts(String value);
    }

    public record Field(String name, String displayName, int length, FieldType type) {
        public Field {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("field name must not be blank");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("field display name must not be blank");
            }
            if (length < 1) {
                throw new IllegalArgumentException("field length must be positive");
            }
            if (type == null) {
                throw new IllegalArgumentException("field type must not be null");
            }
        }
    }

    public PayShieldBodySchema {
        if (direction == null) {
            throw new IllegalArgumentException("schema direction must not be null");
        }
        requireCode(code, "message code");
        if (direction == Direction.RESPONSE) {
            requireCode(errorCode, "response error code");
        } else if (errorCode != null) {
            throw new IllegalArgumentException("command schemas must not declare an error code");
        }
        if (evidenceStatus == null) {
            throw new IllegalArgumentException("evidence status must not be null");
        }
        if (evidenceId == null || evidenceId.isBlank()) {
            throw new IllegalArgumentException("evidence id must not be blank");
        }
        fields = List.copyOf(fields);
    }

    public int bodyLength() {
        return fields.stream().mapToInt(Field::length).sum();
    }

    private static void requireCode(String code, String label) {
        if (code == null || !code.matches("[0-9A-Z]{2}")) {
            throw new IllegalArgumentException(label + " must be two uppercase ASCII characters");
        }
    }
}
