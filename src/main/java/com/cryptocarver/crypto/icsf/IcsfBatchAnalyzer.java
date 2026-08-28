package com.cryptocarver.crypto.icsf;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analyses many ICSF / CCA key tokens at once.
 *
 * <p>Reads the input, hands every token to {@link IcsfTokenParser}, and builds
 * the inventory, the statistics and the audit findings on top of what that
 * analyser decided. It reinterprets nothing: family, scope, type, length,
 * exportability and TVV all come from the single-token analysis, which is why
 * the batch and the one-by-one view cannot contradict each other.</p>
 *
 * <p><b>Security.</b> Nothing here decrypts. Protected key material is only
 * recoverable inside the cryptographic coprocessor under its master key. The
 * outputs do carry whole tokens in hexadecimal, so treat the files this produces
 * with the same care as the dump they came from.</p>
 */
public final class IcsfBatchAnalyzer {

    private IcsfBatchAnalyzer() { }

    /** Reads the input, analyses every token in it and builds the full report. */
    public static IcsfBatchReport analyse(String text, BatchInputFormat format, Origin origin) {
        BatchInputFormat requested = format == null ? BatchInputFormat.AUTO : format;
        Origin resolved = origin == null ? Origin.INFER : origin;
        return analyse(BatchReader.read(text, requested), requested, resolved);
    }

    public static IcsfBatchReport analyse(String text) {
        return analyse(text, BatchInputFormat.AUTO, Origin.INFER);
    }

    /** Analyses entries that have already been read. */
    public static IcsfBatchReport analyse(List<BatchEntry> entries, BatchInputFormat requested,
                                          Origin origin) {
        List<BatchItem> items = new ArrayList<>(entries.size());
        for (BatchEntry entry : entries) {
            ParseResult result = entry.failedToRead() ? null
                    : IcsfTokenParser.parse(entry.data(), origin);
            InventoryRow row = InventoryMapper.map(entry, result);
            items.add(new BatchItem(entry, result, row, FindingDetector.detect(entry, result)));
        }

        markDuplicates(items);

        for (BatchItem item : items) {
            String codes = item.findings().stream()
                    .map(finding -> finding.code().code())
                    .collect(Collectors.joining("; "));
            item.row().set(InventoryColumn.FINDINGS, codes.isEmpty() ? InventoryRow.NONE : codes);
        }

        String generated = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return new IcsfBatchReport(items, requested, origin, generated,
                statistics(items), aggregate(items));
    }

    /** Flags tokens whose bytes appear more than once in the batch. */
    private static void markDuplicates(List<BatchItem> items) {
        Map<String, List<BatchItem>> seen = new LinkedHashMap<>();
        for (BatchItem item : items) {
            if (item.isOk()) seen.computeIfAbsent(item.entry().hex(), key -> new ArrayList<>()).add(item);
        }
        for (List<BatchItem> group : seen.values()) {
            if (group.size() <= 1) continue;
            String which = group.stream().map(BatchItem::display).collect(Collectors.joining(", "));
            for (BatchItem item : group) {
                item.addFinding(Finding.of(FindingCode.DUPLICADO,
                        IcsfText.of("icsf.note.duplicate", which)));
            }
        }
    }

    /**
     * Counts each aggregated dimension over the tokens that analysed.
     *
     * <p>A dimension nothing in the batch actually uses is dropped rather than
     * printed as a column of "not applicable".</p>
     */
    private static List<IcsfBatchReport.Group> statistics(List<BatchItem> items) {
        List<BatchItem> analysed = items.stream().filter(BatchItem::isOk).toList();
        List<IcsfBatchReport.Group> groups = new ArrayList<>();

        for (InventoryColumn column : InventoryColumn.values()) {
            SummaryKey dimension = column.summaryKey();
            if (dimension == null || !dimension.aggregated()) continue;

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (BatchItem item : analysed) {
                String code = item.row().get(column);
                counts.merge(code, 1, Integer::sum);
            }
            if (counts.isEmpty()) continue;
            if (counts.size() == 1) {
                String only = counts.keySet().iterator().next();
                if (only.equals(IcsfVocabulary.Scope.NOT_APPLICABLE.name())
                        || only.equals("NOT_APPLICABLE") || only.equals(InventoryRow.NONE)) {
                    continue;
                }
            }

            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            List<IcsfBatchReport.Group.Value> values = counts.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -e.getValue())
                            .thenComparing(Map.Entry::getKey))
                    .map(entry -> new IcsfBatchReport.Group.Value(entry.getKey(), entry.getValue(),
                            100.0 * entry.getValue() / Math.max(1, total)))
                    .toList();
            groups.add(new IcsfBatchReport.Group(dimension, values));
        }
        return groups;
    }

    /** Groups findings by code, most severe first, then by how many tokens raised them. */
    private static List<IcsfBatchReport.AggregatedFinding> aggregate(List<BatchItem> items) {
        Map<FindingCode, List<Integer>> tokens = new LinkedHashMap<>();
        Map<FindingCode, List<IcsfText>> notes = new LinkedHashMap<>();

        for (BatchItem item : items) {
            for (Finding finding : item.findings()) {
                tokens.computeIfAbsent(finding.code(), key -> new ArrayList<>())
                        .add(item.entry().index());
                if (!finding.note().isEmpty()) {
                    // Composed, not concatenated: the note is still translatable at this point.
                    notes.computeIfAbsent(finding.code(), key -> new ArrayList<>())
                            .add(IcsfText.of("icsf.note.forToken", item.display(), finding.note()));
                }
            }
        }

        return tokens.entrySet().stream()
                .map(entry -> new IcsfBatchReport.AggregatedFinding(entry.getKey(),
                        List.copyOf(entry.getValue()),
                        List.copyOf(notes.getOrDefault(entry.getKey(), List.of()))))
                .sorted(Comparator
                        .comparingInt((IcsfBatchReport.AggregatedFinding finding) ->
                                finding.code().severity().ordinal())
                        .thenComparingInt(finding -> -finding.count())
                        .thenComparing(finding -> finding.code().code()))
                .toList();
    }
}
