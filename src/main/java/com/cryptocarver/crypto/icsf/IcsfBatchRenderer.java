package com.cryptocarver.crypto.icsf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The batch outputs: the on-screen report, the .txt file, the .csv file and JSON. */
public final class IcsfBatchRenderer {

    private IcsfBatchRenderer() { }

    private static final int WIDTH = 78;

    /** Inventory columns that fit in a text report. The CSV carries all of them. */
    private static final List<InventoryColumn> TEXT_COLUMNS = List.of(
            InventoryColumn.INDEX, InventoryColumn.LABEL, InventoryColumn.FAMILY,
            InventoryColumn.KEY_TYPE, InventoryColumn.KEY_LENGTH, InventoryColumn.MATERIAL,
            InventoryColumn.WRAPPING, InventoryColumn.EXPORTABLE, InventoryColumn.TVV,
            InventoryColumn.FINDINGS);

    private static final Map<InventoryColumn, Integer> MAX_WIDTH = buildWidths();

    private static Map<InventoryColumn, Integer> buildWidths() {
        Map<InventoryColumn, Integer> map = new EnumMap<>(InventoryColumn.class);
        map.put(InventoryColumn.INDEX, 4);
        map.put(InventoryColumn.LABEL, 18);
        map.put(InventoryColumn.FAMILY, 24);
        map.put(InventoryColumn.KEY_TYPE, 16);
        map.put(InventoryColumn.KEY_LENGTH, 18);
        map.put(InventoryColumn.MATERIAL, 12);
        map.put(InventoryColumn.WRAPPING, 10);
        map.put(InventoryColumn.EXPORTABLE, 17);
        map.put(InventoryColumn.TVV, 14);
        map.put(InventoryColumn.FINDINGS, 40);
        return map;
    }

    // =====================================================================
    // Text
    // =====================================================================
    /**
     * The report WITHOUT the per-token detail: cover, statistics, findings and
     * inventory. This is what the screen shows.
     */
    public static String renderSummary(IcsfBatchReport report) {
        return renderSummary(report, IcsfMessages.DEFAULT_LOCALE);
    }

    /** The on-screen report, in the given language. */
    public static String renderSummary(IcsfBatchReport report, Locale locale) {
        List<String> lines = new ArrayList<>(cover(report, locale));

        lines.addAll(section("1.  " + m("icsf.report.statistics", locale)));
        if (report.analysed().isEmpty()) lines.add("  (" + m("icsf.report.noneAnalysable", locale) + ")");
        for (IcsfBatchReport.Group group : report.statistics()) {
            lines.add("");
            lines.add("  ### " + IcsfMessages.resolve(
                    IcsfText.of(group.dimension().labelKey()), locale));
            for (IcsfBatchReport.Group.Value value : group.values()) {
                lines.add(String.format(java.util.Locale.ROOT, "    %-46s %5d   %5.1f%%",
                        cut(value.code(), 46), value.count(), value.percentage()));
            }
        }
        lines.add("");

        lines.addAll(section("2.  " + m("icsf.report.findings", locale)));
        if (report.findings().isEmpty()) lines.add("  (" + m("icsf.report.none", locale) + ")");
        for (IcsfBatchReport.AggregatedFinding finding : report.findings()) {
            lines.add("");
            lines.add(String.format(java.util.Locale.ROOT, "  [%-6s] %-24s %d token(s)  -  %s",
                    finding.code().severity().name(), finding.code().code(),
                    finding.count(), IcsfMessages.resolve(
                            IcsfText.of(finding.code().titleKey()), locale)));
            for (String piece : wrap(IcsfMessages.resolve(
                    IcsfText.of(finding.code().detailKey()), locale), WIDTH - 8)) {
                lines.add("        " + piece);
            }
            lines.add("        " + m("icsf.report.tokensLabel", locale) + ": "
                    + shortList(finding.tokens()));
            List<IcsfText> notes = finding.notes();
            for (int index = 0; index < Math.min(8, notes.size()); index++) {
                lines.add("          - " + IcsfMessages.resolve(notes.get(index), locale));
            }
            if (notes.size() > 8) {
                lines.add("          - " + IcsfMessages.resolve(
                        IcsfText.of("icsf.report.andMore", notes.size() - 8), locale));
            }
        }
        lines.add("");

        lines.addAll(section("3.  " + m("icsf.report.inventory", locale)));
        lines.add("");
        for (String row : table(report)) lines.add("  " + row);
        lines.add("");

        if (!report.failed().isEmpty()) {
            lines.addAll(section("4.  " + m("icsf.report.unrecognised", locale)));
            for (BatchItem item : report.failed()) {
                lines.add("  " + item.display() + "  " + IcsfMessages.resolve(
                        IcsfText.of("icsf.report.failedEntry", item.entry().line(),
                                item.entry().data().length, item.failureReason()), locale));
            }
            lines.add("");
        }
        return String.join(System.lineSeparator(), lines);
    }

    /**
     * The FULL report: the summary, and behind it the whole card for every token.
     *
     * @param detail with a large batch this has to be skippable: the full card for a
     *               whole CKDS runs to tens of megabytes
     */
    public static String renderFull(IcsfBatchReport report, boolean detail) {
        return renderFull(report, detail, IcsfMessages.DEFAULT_LOCALE);
    }

    /** The full report, in the given language. */
    public static String renderFull(IcsfBatchReport report, boolean detail, Locale locale) {
        String summary = renderSummary(report, locale);
        if (!detail) return summary;

        List<String> lines = new ArrayList<>();
        lines.add(summary);
        lines.add("");
        lines.add("=".repeat(WIDTH));
        lines.add("  " + m("icsf.report.fullDetail", locale));
        lines.add("=".repeat(WIDTH));
        lines.add("");

        for (BatchItem item : report.items()) {
            lines.add("");
            lines.add("#".repeat(WIDTH));
            lines.add("##  " + IcsfMessages.resolve(IcsfText.of("icsf.report.tokenHeading",
                    item.display(), item.entry().line(), item.entry().format().value()), locale));
            if (!item.findings().isEmpty()) {
                List<String> codes = new ArrayList<>();
                for (Finding finding : item.findings()) codes.add(finding.code().code());
                lines.add("##  " + m("icsf.report.findingsLabel", locale) + ": "
                        + String.join(", ", codes));
            }
            lines.add("#".repeat(WIDTH));
            lines.add("");
            if (item.entry().failedToRead()) {
                lines.add(m("icsf.report.readError", locale) + ": " + item.entry().error());
                lines.add("");
                continue;
            }
            lines.add(IcsfTokenReport.renderText(item.result(), report.origin(),
                    item.entry().data(), locale));
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static List<String> cover(IcsfBatchReport report, Locale locale) {
        List<String> lines = new ArrayList<>();
        lines.add("=".repeat(WIDTH));
        lines.add("  " + m("icsf.report.batchTitle", locale));
        lines.add("  " + IcsfMessages.resolve(
                IcsfText.of("icsf.report.generated", report.generated()), locale));
        lines.add("=".repeat(WIDTH));
        lines.add("");
        lines.add(IcsfMessages.resolve(IcsfText.of("icsf.report.tokensRead",
                report.total(), report.analysed().size(), report.failed().size()), locale));
        lines.add(m("icsf.report.inputFormat", locale) + ": " + IcsfMessages.resolve(
                IcsfText.of("icsf.format." + report.requestedFormat().name()), locale));
        lines.add(m("icsf.report.provenance", locale) + ": " + IcsfMessages.resolve(
                IcsfProvenance.label(report.origin()), locale));
        lines.add("");
        for (String piece : wrap(IcsfTokenReport.securityNotice(locale), WIDTH - 2)) lines.add(piece);
        lines.add("");
        return lines;
    }

    private static String m(String key, Locale locale) {
        return IcsfMessages.resolve(IcsfText.of(key), locale);
    }

    private static List<String> section(String title) {
        return List.of("-".repeat(WIDTH), title, "-".repeat(WIDTH));
    }

    private static List<String> table(IcsfBatchReport report) {
        Map<InventoryColumn, Integer> width = new EnumMap<>(InventoryColumn.class);
        for (InventoryColumn column : TEXT_COLUMNS) {
            int longest = column.header().length();
            for (InventoryRow row : report.rows()) {
                longest = Math.max(longest, row.get(column).length());
            }
            width.put(column, Math.min(longest, MAX_WIDTH.getOrDefault(column, 20)));
        }

        List<String> lines = new ArrayList<>();
        List<String> header = new ArrayList<>();
        List<String> rule = new ArrayList<>();
        for (InventoryColumn column : TEXT_COLUMNS) {
            header.add(pad(column.header(), width.get(column)));
            rule.add("-".repeat(width.get(column)));
        }
        lines.add(String.join("  ", header).stripTrailing());
        lines.add(String.join("  ", rule));
        for (InventoryRow row : report.rows()) {
            List<String> cells = new ArrayList<>();
            for (InventoryColumn column : TEXT_COLUMNS) {
                cells.add(pad(row.get(column), width.get(column)));
            }
            lines.add(String.join("  ", cells).stripTrailing());
        }
        return lines;
    }

    private static String pad(String value, int width) {
        String cut = cut(value, width);
        return cut + " ".repeat(Math.max(0, width - cut.length()));
    }

    private static String cut(String value, int width) {
        return value.length() <= width ? value : value.substring(0, Math.max(0, width - 1)) + "…";
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static String shortList(List<Integer> indexes) {
        int limit = 30;
        List<String> out = new ArrayList<>();
        for (int index = 0; index < Math.min(limit, indexes.size()); index++) {
            out.add("#" + indexes.get(index));
        }
        String joined = String.join(", ", out);
        return indexes.size() <= limit ? joined
                : joined + " ... (+" + (indexes.size() - limit) + ")";
    }

    // =====================================================================
    // CSV
    // =====================================================================
    /** The full inventory as CSV: one row per token, every column, plus the token in hex. */
    public static String toCsv(IcsfBatchReport report, char separator) {
        StringBuilder out = new StringBuilder();
        List<String> header = new ArrayList<>();
        for (InventoryColumn column : InventoryColumn.values()) header.add(column.header());
        header.add("Token (hex)");
        out.append(csvLine(header, separator));

        for (BatchItem item : report.items()) {
            List<String> cells = new ArrayList<>();
            for (InventoryColumn column : InventoryColumn.values()) {
                cells.add(item.row().get(column));
            }
            cells.add(item.entry().hex());
            out.append(csvLine(cells, separator));
        }
        return out.toString();
    }

    public static String toCsv(IcsfBatchReport report) {
        return toCsv(report, ';');
    }

    /**
     * The CSV as bytes, UTF-8 with a byte-order mark.
     *
     * <p>The BOM is what makes Excel open the file correctly on a double click
     * instead of mangling every accented character.</p>
     */
    public static byte[] toCsvBytes(IcsfBatchReport report, char separator) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        out.writeBytes(toCsv(report, separator).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    public static byte[] toCsvBytes(IcsfBatchReport report) {
        return toCsvBytes(report, ';');
    }

    private static String csvLine(List<String> cells, char separator) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) line.append(separator);
            line.append(csvCell(cells.get(index), separator));
        }
        return line.append('\n').toString();
    }

    private static String csvCell(String value, char separator) {
        boolean quote = value.indexOf(separator) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!quote) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    // =====================================================================
    // JSON
    // =====================================================================
    /** A JSON-serializable view of the whole report. */
    public static Map<String, Object> toMap(IcsfBatchReport report, boolean detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("generated", report.generated());
        map.put("format", report.requestedFormat().value());
        map.put("formatLabel", report.requestedFormat().label());
        map.put("origin", report.origin().value());
        map.put("originLabel", IcsfMessages.resolve(IcsfProvenance.label(report.origin())));
        map.put("total", report.total());
        map.put("analysed", report.analysed().size());
        map.put("failed", report.failed().size());
        map.put("securityNotice", IcsfTokenReport.securityNotice(IcsfMessages.DEFAULT_LOCALE));

        List<Map<String, Object>> columns = new ArrayList<>();
        for (InventoryColumn column : InventoryColumn.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", column.name());
            entry.put("header", column.header());
            entry.put("summaryKey", column.summaryKey() == null ? null : column.summaryKey().name());
            columns.add(entry);
        }
        map.put("columns", columns);

        List<Map<String, String>> rows = new ArrayList<>();
        for (BatchItem item : report.items()) {
            Map<String, String> row = new LinkedHashMap<>();
            for (InventoryColumn column : InventoryColumn.values()) {
                row.put(column.name(), item.row().get(column));
            }
            rows.add(row);
        }
        map.put("rows", rows);

        List<Map<String, Object>> statistics = new ArrayList<>();
        for (IcsfBatchReport.Group group : report.statistics()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dimension", group.dimension().name());
            entry.put("label", group.dimension().displayName());
            List<Map<String, Object>> values = new ArrayList<>();
            for (IcsfBatchReport.Group.Value value : group.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", value.code());
                item.put("count", value.count());
                item.put("percentage", Math.round(value.percentage() * 10.0) / 10.0);
                values.add(item);
            }
            entry.put("values", values);
            statistics.add(entry);
        }
        map.put("statistics", statistics);

        List<Map<String, Object>> findings = new ArrayList<>();
        for (IcsfBatchReport.AggregatedFinding finding : report.findings()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", finding.code().code());
            entry.put("severity", finding.code().severity().value());
            entry.put("title", finding.code().title());
            entry.put("detail", finding.code().detail());
            entry.put("count", finding.count());
            entry.put("tokens", finding.tokens());
            List<String> notes = new ArrayList<>();
            for (IcsfText note : finding.notes()) notes.add(IcsfMessages.resolve(note));
            entry.put("notes", notes);
            findings.add(entry);
        }
        map.put("findings", findings);

        List<Map<String, Object>> entries = new ArrayList<>();
        for (BatchItem item : report.items()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", item.entry().index());
            entry.put("label", item.entry().label());
            entry.put("line", item.entry().line());
            entry.put("format", item.entry().format().value());
            entry.put("bytes", item.entry().data().length);
            entry.put("hex", item.entry().hex());
            entry.put("ok", item.isOk());
            entry.put("error", item.isOk() ? "" : item.failureReason());
            List<Map<String, Object>> itemFindings = new ArrayList<>();
            for (Finding finding : item.findings()) {
                Map<String, Object> found = new LinkedHashMap<>();
                found.put("code", finding.code().code());
                found.put("severity", finding.code().severity().value());
                found.put("title", finding.code().title());
                found.put("note", finding.text());
                itemFindings.add(found);
            }
            entry.put("findings", itemFindings);
            if (detail && item.result() != null) {
                entry.put("detail", IcsfTokenReport.toMap(item.result()));
            }
            entries.add(entry);
        }
        map.put("entries", entries);
        return map;
    }

    public static Map<String, Object> toMap(IcsfBatchReport report) {
        return toMap(report, false);
    }
}
