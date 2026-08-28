package com.cryptocarver.crypto.icsf;

/**
 * A coherence warning raised while decoding a token, field by field.
 *
 * <p>Carries a code as well as a message so callers can react to a specific
 * warning without matching on its wording. The Python original recovered the
 * byte-59 warning with {@code "byte 59" in w}, which stops working the moment
 * the sentence changes or is translated.</p>
 *
 * @param code    stable identifier of what went wrong
 * @param message the explanation, resolved against a locale when rendered
 */
public record Diagnostic(DiagnosticCode code, IcsfText message) {

    public Diagnostic {
        if (code == null) throw new IllegalArgumentException("A diagnostic needs a code");
        message = message == null ? IcsfText.EMPTY : message;
    }

    public static Diagnostic of(DiagnosticCode code, IcsfText message) {
        return new Diagnostic(code, message);
    }

    /** The message in English, for the CLI and for tests. */
    public String text() {
        return IcsfMessages.resolve(message);
    }
}
