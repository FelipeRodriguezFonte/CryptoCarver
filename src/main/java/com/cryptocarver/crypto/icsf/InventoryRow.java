package com.cryptocarver.crypto.icsf;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** One inventory row: the countable code of every column, for one token. */
public final class InventoryRow {

    /** Value used when a dimension does not apply, or the token did not parse. */
    public static final String NONE = "-";

    private final Map<InventoryColumn, String> values = new EnumMap<>(InventoryColumn.class);

    InventoryRow() {
        for (InventoryColumn column : InventoryColumn.values()) values.put(column, NONE);
    }

    public String get(InventoryColumn column) {
        return values.getOrDefault(column, NONE);
    }

    InventoryRow set(InventoryColumn column, String value) {
        values.put(column, value == null || value.isBlank() ? NONE : value);
        return this;
    }

    public Map<InventoryColumn, String> values() {
        return Collections.unmodifiableMap(values);
    }

    /** True when the token could not be read or analysed. */
    public boolean failed() {
        return "ERROR".equals(get(InventoryColumn.STATUS));
    }

    /** Whether any column contains the given text, case-insensitively. Drives the table filter. */
    public boolean matches(String filter) {
        if (filter == null || filter.isBlank()) return true;
        String needle = filter.strip().toLowerCase(java.util.Locale.ROOT);
        for (String value : values.values()) {
            if (value.toLowerCase(java.util.Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }
}
