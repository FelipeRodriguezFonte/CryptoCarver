package com.cryptocarver.crypto.icsf;

import java.time.LocalDateTime;
import java.util.Locale;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders one token's analysis: the text report, and a map ready for JSON. */
public final class IcsfTokenReport {

    private IcsfTokenReport() { }

    private static final int WIDTH = 74;

    /**
     * The standing warning that has to travel with anything this tool writes out.
     *
     * <p>Nothing here decrypts: protected key material is only recoverable inside
     * the coprocessor under its master key. But the output carries whole tokens in
     * hexadecimal, so the file deserves the same handling as the dump it came from.</p>
     */
    /** Bundle key of the standing warning, so it reads in the viewer's language too. */
    public static final String SECURITY_NOTICE_KEY = "icsf.report.security";

    /** The standing warning in the given language. */
    public static String securityNotice(Locale locale) {
        return IcsfMessages.resolve(IcsfText.of(SECURITY_NOTICE_KEY), locale);
    }

    public static final String SECURITY_NOTICE =
            "SECURITY: this analysis decrypts nothing. Protected key material is only recoverable "
                    + "inside the cryptographic coprocessor, under its master key. This report does, "
                    + "however, carry the tokens in full in hexadecimal, so handle the file with the "
                    + "same care as the dump it came from.";

    /** The full text report for one token, in English. */
    public static String renderText(ParseResult result, Origin origin, byte[] input) {
        return renderText(result, origin, input, IcsfMessages.DEFAULT_LOCALE);
    }

    /** The full text report for one token, suitable for saving, in the given language. */
    public static String renderText(ParseResult result, Origin origin, byte[] input, Locale locale) {
        List<String> lines = new ArrayList<>();
        lines.add("=".repeat(WIDTH));
        lines.add("  " + m("icsf.report.tokenTitle", locale));
        lines.add("  " + IcsfMessages.resolve(IcsfText.of("icsf.report.generated",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))),
                locale));
        lines.add("=".repeat(WIDTH));
        lines.add("");
        lines.add(pad(m("icsf.report.provenance", locale)) + ": "
                + IcsfMessages.resolve(IcsfProvenance.label(origin, result.tokenFamily()), locale));
        lines.add(pad(m("icsf.report.tokenLength", locale)) + ": "
                + IcsfMessages.resolve(IcsfText.of("icsf.value.bytes", input.length), locale));
        lines.add("");
        lines.add(m("icsf.report.tokenHex", locale) + ":");
        lines.add(IcsfHex.grouped(input).stripTrailing());
        lines.add("");

        if (!result.isOk()) {
            lines.add(m("icsf.report.error", locale) + ": " + result.error());
            lines.add("");
            lines.add(securityNotice(locale));
            return String.join(System.lineSeparator(), lines);
        }

        lines.add("-".repeat(WIDTH));
        lines.add(m("icsf.report.summary", locale));
        lines.add("-".repeat(WIDTH));
        for (Map.Entry<SummaryKey, SummaryValue> entry : result.summary().entrySet()) {
            SummaryValue value = entry.getValue();
            String detail = IcsfMessages.resolve(value.detail(), locale);
            String rendered = detail.isEmpty() ? value.code() : value.code() + " — " + detail;
            lines.add(String.format(java.util.Locale.ROOT, "  %-26s: %s",
                    m(entry.getKey().labelKey(), locale), rendered));
        }
        lines.add("");

        lines.add("-".repeat(WIDTH));
        lines.add(m("icsf.report.detail", locale));
        lines.add("-".repeat(WIDTH));
        for (IcsfSection section : result.sections()) {
            lines.add("");
            lines.add("### " + IcsfMessages.resolve(section.title(), locale));
            for (IcsfSection.Field field : section.fields()) {
                lines.add(String.format(java.util.Locale.ROOT, "  [off %-4d len %-3d] %-42s %s",
                        field.offset(), field.length(),
                        IcsfMessages.resolve(field.name(), locale), field.rawHex()));
                String value = IcsfMessages.resolve(field.value(), locale);
                if (!value.isEmpty()) lines.add("      -> " + value);
            }
            for (IcsfSection.Flag flag : section.flags()) {
                String help = IcsfMessages.resolve(flag.detail(), locale);
                lines.add(String.format(java.util.Locale.ROOT, "  [flag %s] %s%s", flag.on() ? "ON " : "off",
                        IcsfMessages.resolve(flag.name(), locale), help.isEmpty() ? "" : "  " + help));
            }
        }
        lines.add("");

        lines.add("-".repeat(WIDTH));
        lines.add(m("icsf.report.provenanceSection", locale) + " (" + origin.value() + ")");
        lines.add("-".repeat(WIDTH));
        if (result.provenanceNotes().isEmpty()) {
            lines.add("  (" + m("icsf.report.noNotes", locale) + ")");
        } else {
            for (IcsfText note : result.provenanceNotes()) {
                lines.add("  * " + IcsfMessages.resolve(note, locale));
            }
        }
        lines.add("");

        lines.add("-".repeat(WIDTH));
        lines.add(m("icsf.report.warnings", locale));
        lines.add("-".repeat(WIDTH));
        if (result.warnings().isEmpty()) {
            lines.add("  (" + m("icsf.report.none", locale) + ")");
        } else {
            for (Diagnostic warning : result.warnings()) {
                lines.add("  ! [" + warning.code() + "] "
                        + IcsfMessages.resolve(warning.message(), locale));
            }
        }
        lines.add("");
        lines.add(securityNotice(locale));
        return String.join(System.lineSeparator(), lines);
    }

    private static String m(String key, Locale locale) {
        return IcsfMessages.resolve(IcsfText.of(key), locale);
    }

    /** Pads a label so the colons line up whatever the language. */
    private static String pad(String label) {
        return label.length() >= 20 ? label : label + " ".repeat(20 - label.length());
    }

    /** A JSON-serializable view of the analysis. */
    public static Map<String, Object> toMap(ParseResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", result.isOk());
        map.put("error", result.error());
        map.put("tokenFamily", result.tokenFamily().code());
        map.put("rawLength", result.rawLength());

        List<Map<String, Object>> summary = new ArrayList<>();
        for (Map.Entry<SummaryKey, SummaryValue> entry : result.summary().entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey().name());
            item.put("label", entry.getKey().displayName());
            item.put("code", entry.getValue().code());
            item.put("detail", entry.getValue().text());
            item.put("aggregated", entry.getKey().aggregated());
            summary.add(item);
        }
        map.put("summary", summary);

        List<Map<String, Object>> sections = new ArrayList<>();
        for (IcsfSection section : result.sections()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", IcsfMessages.resolve(section.title()));
            List<Map<String, Object>> fields = new ArrayList<>();
            for (IcsfSection.Field field : section.fields()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("offset", field.offset());
                entry.put("length", field.length());
                entry.put("name", IcsfMessages.resolve(field.name()));
                entry.put("rawHex", field.rawHex());
                entry.put("value", IcsfMessages.resolve(field.value()));
                fields.add(entry);
            }
            item.put("fields", fields);
            List<Map<String, Object>> flags = new ArrayList<>();
            for (IcsfSection.Flag flag : section.flags()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", IcsfMessages.resolve(flag.name()));
                entry.put("on", flag.on());
                entry.put("detail", IcsfMessages.resolve(flag.detail()));
                flags.add(entry);
            }
            item.put("flags", flags);
            sections.add(item);
        }
        map.put("sections", sections);

        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Diagnostic warning : result.warnings()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", warning.code().name());
            entry.put("message", warning.text());
            warnings.add(entry);
        }
        map.put("warnings", warnings);
        map.put("provenanceNotes", result.provenanceTexts());
        map.put("securityNotice", securityNotice(IcsfMessages.DEFAULT_LOCALE));
        return map;
    }
}
