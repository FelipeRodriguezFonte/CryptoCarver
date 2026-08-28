package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One token of the batch: how it was read, what it is, and what is worth looking at. */
public final class BatchItem {

    private final BatchEntry entry;
    private final ParseResult result;
    private final InventoryRow row;
    private final List<Finding> findings = new ArrayList<>();

    BatchItem(BatchEntry entry, ParseResult result, InventoryRow row, List<Finding> findings) {
        this.entry = entry;
        this.result = result;
        this.row = row;
        this.findings.addAll(findings);
    }

    public BatchEntry entry() {
        return entry;
    }

    /** The analysis, or {@code null} when the bytes could not even be read. */
    public ParseResult result() {
        return result;
    }

    public InventoryRow row() {
        return row;
    }

    public List<Finding> findings() {
        return Collections.unmodifiableList(findings);
    }

    void addFinding(Finding finding) {
        findings.add(finding);
    }

    public boolean isOk() {
        return result != null && result.isOk() && !entry.failedToRead();
    }

    /** "#3" or "#3 (MY.KEY.01)". */
    public String display() {
        return entry.display();
    }

    /** Why this token failed, for the unrecognised-entries section. */
    public String failureReason() {
        if (!entry.error().isEmpty()) return entry.error();
        if (result != null && result.error() != null) return result.error();
        return "?";
    }
}
