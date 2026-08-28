package com.cryptocarver.crypto.icsf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the analyser concluded about one key token.
 *
 * <p>Three layers, deliberately separated:</p>
 * <ul>
 *   <li>the <b>summary</b>, a map of typed verdicts. This is the contract with
 *       the batch layer and with statistics: countable codes, never prose;</li>
 *   <li>the <b>sections</b>, the field-by-field technical detail, for reading;</li>
 *   <li>the <b>facts</b>, a handful of byte-level observations that the audit
 *       findings need and that would otherwise force a caller to re-read the
 *       token. Exposing them here is what lets the batch layer run without ever
 *       touching a byte, so it cannot reach a different conclusion.</li>
 * </ul>
 */
public final class ParseResult {

    private final boolean ok;
    private final String error;
    private final int rawLength;

    private TokenFamily tokenFamily;
    private final Map<SummaryKey, SummaryValue> summary = new LinkedHashMap<>();
    private final List<IcsfSection> sections = new ArrayList<>();
    private final List<Diagnostic> warnings = new ArrayList<>();
    private final List<IcsfText> provenanceNotes = new ArrayList<>();

    // --- byte-level facts the findings layer needs ------------------------
    private Integer byte59;
    private Boolean controlVectorStructureValid;
    private boolean controlVectorEnhOnly;
    private boolean compliantTagged;
    private int desComponentCount;
    private boolean desComponentsReliable;
    private boolean securityHistoryDegraded;

    private ParseResult(boolean ok, TokenFamily tokenFamily, String error, int rawLength) {
        this.ok = ok;
        this.tokenFamily = tokenFamily;
        this.error = error;
        this.rawLength = rawLength;
    }

    public static ParseResult ok(TokenFamily family, int rawLength) {
        return new ParseResult(true, family, null, rawLength);
    }

    public static ParseResult failure(String error, int rawLength) {
        return new ParseResult(false, TokenFamily.UNKNOWN, error, rawLength);
    }

    // --- state ------------------------------------------------------------
    public boolean isOk() {
        return ok;
    }

    public String error() {
        return error;
    }

    public int rawLength() {
        return rawLength;
    }

    public TokenFamily tokenFamily() {
        return tokenFamily;
    }

    public ParseResult tokenFamily(TokenFamily family) {
        this.tokenFamily = family;
        return this;
    }

    // --- summary ----------------------------------------------------------
    public Map<SummaryKey, SummaryValue> summary() {
        return Collections.unmodifiableMap(summary);
    }

    public ParseResult summary(SummaryKey key, SummaryValue value) {
        summary.put(key, value);
        return this;
    }

    public ParseResult summary(SummaryKey key, Enum<?> value) {
        return summary(key, SummaryValue.of(value));
    }

    public ParseResult summary(SummaryKey key, Enum<?> value, IcsfText detail) {
        return summary(key, SummaryValue.of(value, detail));
    }

    public ParseResult summary(SummaryKey key, String code, IcsfText detail) {
        return summary(key, SummaryValue.of(code, detail));
    }

    public ParseResult summary(SummaryKey key, String code) {
        return summary(key, SummaryValue.of(code));
    }

    /** The countable code of a dimension, or {@code fallback} when the token has no such dimension. */
    public String code(SummaryKey key, String fallback) {
        SummaryValue value = summary.get(key);
        return value == null ? fallback : value.code();
    }

    public Optional<SummaryValue> value(SummaryKey key) {
        return Optional.ofNullable(summary.get(key));
    }

    /** True when the dimension carries exactly this code. */
    public boolean is(SummaryKey key, Enum<?> expected) {
        SummaryValue value = summary.get(key);
        return value != null && value.code().equals(expected.name());
    }

    // --- sections ---------------------------------------------------------
    public List<IcsfSection> sections() {
        return Collections.unmodifiableList(sections);
    }

    public ParseResult section(IcsfSection section) {
        sections.add(section);
        return this;
    }

    // --- warnings and provenance -----------------------------------------
    public List<Diagnostic> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public ParseResult warn(DiagnosticCode code, IcsfText message) {
        warnings.add(Diagnostic.of(code, message));
        return this;
    }

    /** True if any warning carries this code. */
    public boolean warned(DiagnosticCode code) {
        for (Diagnostic warning : warnings) {
            if (warning.code() == code) return true;
        }
        return false;
    }

    public Optional<Diagnostic> warning(DiagnosticCode code) {
        for (Diagnostic warning : warnings) {
            if (warning.code() == code) return Optional.of(warning);
        }
        return Optional.empty();
    }

    public List<IcsfText> provenanceNotes() {
        return Collections.unmodifiableList(provenanceNotes);
    }

    public ParseResult provenanceNote(IcsfText note) {
        provenanceNotes.add(note);
        return this;
    }

    /** The provenance notes in English, for the CLI and for tests. */
    public List<String> provenanceTexts() {
        List<String> out = new ArrayList<>(provenanceNotes.size());
        for (IcsfText note : provenanceNotes) out.add(IcsfMessages.resolve(note));
        return out;
    }

    // --- byte-level facts -------------------------------------------------

    /** Byte 59 of a DES fixed-length token, or empty for every other format. */
    public Optional<Integer> byte59() {
        return Optional.ofNullable(byte59);
    }

    public ParseResult byte59(int value) {
        this.byte59 = value;
        return this;
    }

    /**
     * Whether the Control Vector satisfies the structural rules of p. 1678.
     * Empty when the token carries no readable CV.
     */
    public Optional<Boolean> controlVectorStructureValid() {
        return Optional.ofNullable(controlVectorStructureValid);
    }

    public ParseResult controlVectorStructureValid(boolean valid) {
        this.controlVectorStructureValid = valid;
        return this;
    }

    /** CV bit 56: the coprocessor will no longer hand back a legacy ECB-wrapped token for this key. */
    public boolean controlVectorEnhOnly() {
        return controlVectorEnhOnly;
    }

    public ParseResult controlVectorEnhOnly(boolean value) {
        this.controlVectorEnhOnly = value;
        return this;
    }

    /** Compliant-tagged, from CV bit 58, the variable-length management fields, or a PKA section. */
    public boolean compliantTagged() {
        return compliantTagged;
    }

    public ParseResult compliantTagged(boolean value) {
        this.compliantTagged = value;
        return this;
    }

    /** How many 8-byte components were compared: 2, 3, or 0 when the question does not arise. */
    public int desComponentCount() {
        return desComponentCount;
    }

    /** Whether comparing those components proves anything about effective strength. */
    public boolean desComponentsReliable() {
        return desComponentsReliable;
    }

    public ParseResult desComponents(int count, boolean reliable) {
        this.desComponentCount = count;
        this.desComponentsReliable = reliable;
        return this;
    }

    /**
     * The management fields declare the key was once wrapped under an untrusted KEK,
     * in ECB, under a weaker key, or in a format without type and usage attributes.
     */
    public boolean securityHistoryDegraded() {
        return securityHistoryDegraded;
    }

    public ParseResult securityHistoryDegraded(boolean value) {
        this.securityHistoryDegraded = value;
        return this;
    }
}
