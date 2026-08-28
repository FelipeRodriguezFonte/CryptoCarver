package com.cryptocarver.crypto.icsf;

import java.util.Locale;

/**
 * One finding raised against one token.
 *
 * @param code the catalogue entry, which carries the severity and the explanation
 * @param note the concrete datum for THIS token, or empty
 */
public record Finding(FindingCode code, IcsfText note) {

    public Finding {
        if (code == null) throw new IllegalArgumentException("A finding needs a code");
        note = note == null ? IcsfText.EMPTY : note;
    }

    public static Finding of(FindingCode code) {
        return new Finding(code, IcsfText.EMPTY);
    }

    public static Finding of(FindingCode code, IcsfText note) {
        return new Finding(code, note);
    }

    /** The note in English, for the CLI and for tests. */
    public String text() {
        return IcsfMessages.resolve(note);
    }

    /** The note, trimmed for a report line that has limited room. */
    public String text(Locale locale, int limit) {
        return trim(IcsfMessages.resolve(note, locale), limit);
    }

    /**
     * Trims at the last whole word: a note cut mid-sentence reads as though the
     * datum itself were incomplete.
     */
    static String trim(String text, int limit) {
        if (text == null) return "";
        String collapsed = text.replaceAll("\\s+", " ").strip();
        if (collapsed.length() <= limit) return collapsed;
        int lastSpace = collapsed.substring(0, limit).lastIndexOf(' ');
        return (lastSpace > 0 ? collapsed.substring(0, lastSpace) : collapsed.substring(0, limit)) + " ...";
    }
}
