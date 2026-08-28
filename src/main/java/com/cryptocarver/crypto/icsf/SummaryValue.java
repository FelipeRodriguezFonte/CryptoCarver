package com.cryptocarver.crypto.icsf;

/**
 * One entry of the summary card: a countable verdict plus its explanation.
 *
 * @param code   language-invariant identifier. This is what statistics count,
 *               what the CSV carries and what the batch compares. Never blank.
 * @param detail the reasoning behind the verdict, resolved when rendered.
 *               Presentation only; nothing branches on it.
 */
public record SummaryValue(String code, IcsfText detail) {

    public SummaryValue {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("A summary value needs a countable code");
        }
        detail = detail == null ? IcsfText.EMPTY : detail;
    }

    public static SummaryValue of(String code) {
        return new SummaryValue(code, IcsfText.EMPTY);
    }

    public static SummaryValue of(String code, IcsfText detail) {
        return new SummaryValue(code, detail);
    }

    /** Convenience for the closed vocabularies, whose {@code code()} is their enum name. */
    public static SummaryValue of(Enum<?> value) {
        return new SummaryValue(value.name(), IcsfText.EMPTY);
    }

    public static SummaryValue of(Enum<?> value, IcsfText detail) {
        return new SummaryValue(value.name(), detail);
    }

    /** The explanation in English, for the CLI and for tests. */
    public String text() {
        return IcsfMessages.resolve(detail);
    }
}
