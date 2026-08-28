package com.cryptocarver.crypto.icsf;

import java.util.List;

/**
 * A piece of report text, stored as what it means rather than as words.
 *
 * <p>The analyser records a bundle key and its arguments; the words are chosen
 * when the report is rendered, against a locale the caller supplies. That keeps
 * the core free of any presentation decision and, more usefully, means switching
 * language does not require re-analysing anything: the same {@link ParseResult}
 * renders in either language.</p>
 *
 * <p>{@link #raw(String)} exists for text that genuinely is not translatable — a
 * key name decoded from the token, a hex value, a component pattern like
 * {@code K1 = K3 != K2}. Passing those through verbatim is correct, not lazy:
 * they are data, and the application already treats technical values as
 * language-invariant.</p>
 *
 * @param key       bundle key, or {@code null} when {@link #literal} carries the text
 * @param arguments values interpolated into the message, may be empty
 * @param literal   verbatim text, or {@code null} when {@link #key} is used
 */
public record IcsfText(String key, List<Object> arguments, String literal) {

    /** Nothing to show. */
    public static final IcsfText EMPTY = new IcsfText(null, List.of(), "");

    public IcsfText {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        if (key == null && literal == null) {
            throw new IllegalArgumentException("An IcsfText needs either a key or a literal");
        }
    }

    /** Translatable text, with the values to interpolate. */
    public static IcsfText of(String key, Object... arguments) {
        return new IcsfText(key, arguments == null ? List.of() : List.of(arguments), null);
    }

    /** Text that must read the same in every language: decoded data, hex, patterns. */
    public static IcsfText raw(String literal) {
        return literal == null || literal.isEmpty() ? EMPTY : new IcsfText(null, List.of(), literal);
    }

    /** Verbatim text when {@code condition}, otherwise nothing. */
    public static IcsfText rawIf(boolean condition, String literal) {
        return condition ? raw(literal) : EMPTY;
    }

    public boolean isEmpty() {
        return key == null && (literal == null || literal.isEmpty());
    }

    /** True when this text is translated rather than passed through. */
    public boolean isTranslatable() {
        return key != null;
    }
}
