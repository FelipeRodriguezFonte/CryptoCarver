package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The result of analysing a batch: the items, the statistics and the aggregated findings. */
public final class IcsfBatchReport {

    /** One dimension's tally across the batch. */
    public record Group(SummaryKey dimension, List<Value> values) {
        /** One value of a dimension, with how many tokens carry it. */
        public record Value(String code, int count, double percentage) { }
    }

    /** One finding code, with every token that raised it. */
    public record AggregatedFinding(FindingCode code, List<Integer> tokens, List<IcsfText> notes) {
        public int count() {
            return tokens.size();
        }
    }

    private final List<BatchItem> items;
    private final BatchInputFormat requestedFormat;
    private final Origin origin;
    private final String generated;
    private final List<Group> statistics;
    private final List<AggregatedFinding> findings;

    IcsfBatchReport(List<BatchItem> items, BatchInputFormat requestedFormat, Origin origin,
                    String generated, List<Group> statistics, List<AggregatedFinding> findings) {
        this.items = List.copyOf(items);
        this.requestedFormat = requestedFormat;
        this.origin = origin;
        this.generated = generated;
        this.statistics = List.copyOf(statistics);
        this.findings = List.copyOf(findings);
    }

    public List<BatchItem> items() {
        return items;
    }

    public BatchInputFormat requestedFormat() {
        return requestedFormat;
    }

    public Origin origin() {
        return origin;
    }

    public String generated() {
        return generated;
    }

    public List<Group> statistics() {
        return statistics;
    }

    public List<AggregatedFinding> findings() {
        return findings;
    }

    public int total() {
        return items.size();
    }

    /** The tokens that analysed cleanly; statistics are computed over exactly these. */
    public List<BatchItem> analysed() {
        List<BatchItem> out = new ArrayList<>();
        for (BatchItem item : items) {
            if (item.isOk()) out.add(item);
        }
        return Collections.unmodifiableList(out);
    }

    /** The entries that could not be read or analysed. */
    public List<BatchItem> failed() {
        List<BatchItem> out = new ArrayList<>();
        for (BatchItem item : items) {
            if (!item.isOk()) out.add(item);
        }
        return Collections.unmodifiableList(out);
    }

    /** The inventory rows, in batch order. */
    public List<InventoryRow> rows() {
        List<InventoryRow> out = new ArrayList<>(items.size());
        for (BatchItem item : items) out.add(item.row());
        return Collections.unmodifiableList(out);
    }

    /** The inventory restricted to the tokens that raised a given finding. Drives the UI filter. */
    public List<InventoryRow> rowsWith(FindingCode code) {
        List<InventoryRow> out = new ArrayList<>();
        for (BatchItem item : items) {
            for (Finding finding : item.findings()) {
                if (finding.code() == code) {
                    out.add(item.row());
                    break;
                }
            }
        }
        return Collections.unmodifiableList(out);
    }
}
