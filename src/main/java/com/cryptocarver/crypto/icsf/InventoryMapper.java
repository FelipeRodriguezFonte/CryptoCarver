package com.cryptocarver.crypto.icsf;

/**
 * Turns one analysed token into one inventory row.
 *
 * <p>Note the signature: it is handed the analysis, not the token. It reads
 * {@code entry.data().length} and nothing else about the bytes, so every
 * dimension in the inventory is the single-token analyser's own verdict, copied.
 * There is no second reading of the token here that could drift away from the
 * first one.</p>
 */
public final class InventoryMapper {

    private InventoryMapper() { }

    public static InventoryRow map(BatchEntry entry, ParseResult result) {
        InventoryRow row = new InventoryRow();
        row.set(InventoryColumn.INDEX, String.valueOf(entry.index()));
        row.set(InventoryColumn.LABEL, entry.label());
        row.set(InventoryColumn.BYTES, String.valueOf(entry.data().length));

        if (entry.failedToRead() || result == null || !result.isOk()) {
            row.set(InventoryColumn.STATUS, "ERROR");
            return row;
        }

        row.set(InventoryColumn.STATUS, "OK");
        row.set(InventoryColumn.WARNINGS, String.valueOf(result.warnings().size()));
        for (InventoryColumn column : InventoryColumn.values()) {
            SummaryKey key = column.summaryKey();
            if (key != null) row.set(column, result.code(key, InventoryRow.NONE));
        }
        return row;
    }
}
