package com.cryptocarver.crypto.icsf.keywrap;

import com.cryptocarver.crypto.icsf.IcsfMessages;
import com.cryptocarver.crypto.icsf.IcsfText;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link KeyWrapResult} into the report the pane shows and the .txt it saves.
 *
 * <p>Rendering happens here and nowhere else, against a locale the caller supplies, so
 * switching language re-renders without re-running the operation.</p>
 */
public final class KeyWrapReport {

    private KeyWrapReport() { }

    private static final String RULE = "=".repeat(78);
    private static final String THIN = "-".repeat(78);

    private static String t(IcsfText text, Locale locale) {
        return text == null ? "" : IcsfMessages.resolve(text, locale);
    }

    /** The whole report: summary, steps, notes and, for Resolve, the candidate table. */
    public static String render(KeyWrapResult result, Locale locale) {
        StringBuilder out = new StringBuilder();
        out.append(RULE).append('\n');
        out.append(t(IcsfText.of("icsf.keywrap.report.title"), locale)).append('\n');
        out.append(RULE).append("\n\n");

        if (!result.ok()) {
            out.append(t(IcsfText.of("icsf.keywrap.report.failed"), locale)).append('\n');
            out.append("  ").append(t(result.error(), locale)).append('\n');
            return out.toString();
        }

        int labelWidth = result.summary().stream()
                .mapToInt(row -> t(row.label(), locale).length())
                .max().orElse(0);
        for (KeyWrapResult.Row row : result.summary()) {
            out.append(pad(t(row.label(), locale), labelWidth))
                    .append(" : ").append(t(row.value(), locale)).append('\n');
        }

        if (!result.steps().isEmpty()) {
            out.append('\n').append(THIN).append('\n');
            out.append(t(IcsfText.of("icsf.keywrap.report.steps"), locale)).append('\n');
            out.append(THIN).append('\n');
            for (KeyWrapResult.Step step : result.steps()) {
                out.append(t(step.title(), locale)).append('\n');
                out.append("    ").append(step.hexValue()).append('\n');
                String detail = t(step.detail(), locale);
                if (!detail.isEmpty()) out.append("    (").append(detail).append(")\n");
            }
        }

        if (!result.candidates().isEmpty()) {
            out.append('\n').append(THIN).append('\n');
            out.append(t(IcsfText.of("icsf.keywrap.report.candidates"), locale)).append('\n');
            out.append(THIN).append('\n');
            for (KeyWrapResult.Candidate candidate : result.candidates()) {
                out.append("[").append(verdictWord(candidate.verdict(), locale)).append("] ")
                        .append(t(candidate.scheme(), locale)).append('\n');
                out.append("    ").append(t(IcsfText.of("icsf.keywrap.report.candidateKey"), locale))
                        .append(' ').append(candidate.keyHex())
                        .append("   KCV ").append(candidate.kcvHex())
                        .append("   ").append(parityWord(candidate, locale)).append('\n');
                for (IcsfText equivalent : candidate.equivalentSchemes()) {
                    out.append("    = ").append(t(IcsfText.of("icsf.keywrap.report.alsoScheme"), locale))
                            .append(' ').append(t(equivalent, locale)).append('\n');
                }
            }
        }

        if (!result.notes().isEmpty()) {
            out.append('\n').append(THIN).append('\n');
            out.append(t(IcsfText.of("icsf.keywrap.report.notes"), locale)).append('\n');
            out.append(THIN).append('\n');
            for (KeyWrapResult.Note note : result.notes()) {
                out.append('[').append(levelWord(note.level(), locale)).append("] ")
                        .append(t(note.title(), locale)).append('\n');
                out.append(wrap(t(note.text(), locale), 74, "    ")).append('\n');
            }
        }

        out.append('\n').append(RULE).append('\n');
        out.append(wrap(t(IcsfText.of("icsf.keywrap.report.securityNotice"), locale), 74, "")).append('\n');
        out.append(RULE).append('\n');
        return out.toString();
    }

    private static String verdictWord(KeyWrapResult.Verdict verdict, Locale locale) {
        return t(IcsfText.of("icsf.keywrap.verdict." + verdict.name()), locale);
    }

    private static String levelWord(KeyWrapResult.Level level, Locale locale) {
        return t(IcsfText.of("icsf.keywrap.level." + level.name()), locale);
    }

    private static String parityWord(KeyWrapResult.Candidate candidate, Locale locale) {
        return switch (candidate.parity()) {
            case ODD_OK -> t(IcsfText.of("icsf.keywrap.parity.oddOk"), locale);
            case ALL_EVEN -> t(IcsfText.of("icsf.keywrap.parity.allEven",
                    candidate.keyHex().length() / 2), locale);
            case MIXED -> t(IcsfText.of("icsf.keywrap.parity.mixed", candidate.wrongParityBytes()), locale);
        };
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    /** Wraps prose to a column, so a long note stays readable in a monospaced pane. */
    private static String wrap(String text, int width, String indent) {
        StringBuilder out = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.append(indent).append(line).append('\n');
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.append(indent).append(line);
        return out.toString();
    }

    /** The named outputs, for the pane's copy buttons. */
    public static List<String> outputNames(KeyWrapResult result) {
        return List.copyOf(result.outputs().keySet());
    }

    /**
     * The result as plain data, for {@code --json}.
     *
     * <p>Codes travel beside the words: a caller piping this into something else needs
     * {@code MATCHES_KEY}, not a sentence that changes with the locale.</p>
     */
    public static java.util.Map<String, Object> toMap(KeyWrapResult result, Locale locale) {
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("ok", result.ok());
        root.put("operation", result.operation().name());
        if (!result.ok()) {
            root.put("error", t(result.error(), locale));
            return root;
        }
        List<java.util.Map<String, Object>> summary = new java.util.ArrayList<>();
        for (KeyWrapResult.Row row : result.summary()) {
            summary.add(java.util.Map.of("label", t(row.label(), locale), "value", t(row.value(), locale)));
        }
        root.put("summary", summary);

        List<java.util.Map<String, Object>> steps = new java.util.ArrayList<>();
        for (KeyWrapResult.Step step : result.steps()) {
            steps.add(java.util.Map.of("title", t(step.title(), locale),
                    "value", step.hexValue(), "detail", t(step.detail(), locale)));
        }
        root.put("steps", steps);

        List<java.util.Map<String, Object>> notes = new java.util.ArrayList<>();
        for (KeyWrapResult.Note note : result.notes()) {
            notes.add(java.util.Map.of("level", note.level().name(), "code", note.code(),
                    "title", t(note.title(), locale), "text", t(note.text(), locale)));
        }
        root.put("notes", notes);

        if (!result.candidates().isEmpty()) {
            List<java.util.Map<String, Object>> candidates = new java.util.ArrayList<>();
            for (KeyWrapResult.Candidate candidate : result.candidates()) {
                List<String> equivalents = new java.util.ArrayList<>();
                for (IcsfText equivalent : candidate.equivalentSchemes()) {
                    equivalents.add(t(equivalent, locale));
                }
                candidates.add(java.util.Map.of(
                        "schemeCode", candidate.schemeCode(),
                        "scheme", t(candidate.scheme(), locale),
                        "key", candidate.keyHex(),
                        "kcv", candidate.kcvHex(),
                        "parity", candidate.parity().name(),
                        "verdict", candidate.verdict().name(),
                        "equivalentSchemes", equivalents));
            }
            root.put("candidates", candidates);
        }
        root.put("outputs", result.outputs());
        root.put("securityNotice", t(IcsfText.of("icsf.keywrap.report.securityNotice"), locale));
        return root;
    }
}
