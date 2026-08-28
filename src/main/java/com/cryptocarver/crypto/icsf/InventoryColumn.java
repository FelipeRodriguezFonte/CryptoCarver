package com.cryptocarver.crypto.icsf;

/**
 * The columns of the batch inventory, one row per token.
 *
 * <p>Twelve of them are bound to a {@link SummaryKey}, and those are filled by
 * copying the single-token analyser's verdict verbatim. That binding is the
 * mechanism behind the rule that batch and single-token analysis cannot
 * disagree: there is no second decision to drift from the first.</p>
 */
public enum InventoryColumn {

    INDEX("#", null),
    LABEL("Label", null),
    BYTES("Bytes", null),
    STATUS("Status", null),

    FAMILY("Family", SummaryKey.FAMILY),
    SCOPE("Scope", SummaryKey.SCOPE),
    ALGORITHM("Algorithm", SummaryKey.ALGORITHM),
    KEY_TYPE("Key type", SummaryKey.KEY_TYPE),
    KEY_LENGTH("Key length", SummaryKey.KEY_LENGTH),
    EFFECTIVE_STRENGTH("Effective strength", SummaryKey.EFFECTIVE_STRENGTH),
    MATERIAL("Material", SummaryKey.MATERIAL_STATE),
    WRAPPING("Wrapping", SummaryKey.WRAPPING),
    EXPORTABLE("Exportable", SummaryKey.EXPORTABILITY),
    CONTROL_VECTOR("Control Vector", SummaryKey.CONTROL_VECTOR),
    TVV("TVV", SummaryKey.TVV),
    MKVP("MKVP", SummaryKey.MKVP),

    WARNINGS("Warnings", null),
    FINDINGS("Findings", null);

    private final String header;
    private final SummaryKey summaryKey;

    InventoryColumn(String header, SummaryKey summaryKey) {
        this.header = header;
        this.summaryKey = summaryKey;
    }

    /** English column header; the UI resolves {@code icsf.column.<name>} instead. */
    public String header() {
        return header;
    }

    /** The analyser verdict this column copies, or {@code null} for the computed columns. */
    public SummaryKey summaryKey() {
        return summaryKey;
    }

    /** Bundle key for this column's header. */
    public String headerKey() {
        return "icsf.column." + name();
    }
}
