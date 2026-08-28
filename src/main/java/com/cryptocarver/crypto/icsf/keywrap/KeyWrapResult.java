package com.cryptocarver.crypto.icsf.keywrap;

import com.cryptocarver.crypto.icsf.IcsfText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one wrap or unwrap produced, stored as meaning rather than as words.
 *
 * <p>Every label and every note is an {@link IcsfText}, so the report renders in Spanish
 * or English from the same result without re-running the operation. Verdicts are codes and
 * never prose, which is what keeps them countable: recovering a verdict by matching on its
 * wording would stop working the moment that wording is translated.</p>
 */
public final class KeyWrapResult {

    /** Which operation produced this. */
    public enum Operation { EXPORT, IMPORT, INSPECT, RESOLVE }

    /** How much attention a note deserves. */
    public enum Level { CRITICAL, WARNING, OK, INFO }

    /** One line of the summary table. */
    public record Row(IcsfText label, IcsfText value) { }

    /** One intermediate value, so the arithmetic can be followed and checked. */
    public record Step(IcsfText title, String hexValue, IcsfText detail) { }

    /** An interoperability observation. */
    public record Note(Level level, String code, IcsfText title, IcsfText text) { }

    /**
     * One scheme the resolver tried, and what it produced.
     *
     * <p>{@code equivalentSchemes} names the other schemes that produce this very same key.
     * That is not redundancy: a zero Control Vector under the CV variant and a NOCV KEK are
     * the same arithmetic (KEK XOR 0 = KEK), so listing them as separate findings would
     * suggest the evidence points two ways when it points one.</p>
     */
    public record Candidate(String schemeCode, IcsfText scheme, String keyHex, String kcvHex,
                            Parity parity, Verdict verdict, int wrongParityBytes,
                            List<IcsfText> equivalentSchemes) {
        public Candidate {
            equivalentSchemes = equivalentSchemes == null ? List.of() : List.copyOf(equivalentSchemes);
        }
    }

    /** Parity of a candidate key, which is the only evidence available without a reference. */
    public enum Parity { ODD_OK, ALL_EVEN, MIXED }

    /** How well a candidate matches. Ordered best first, which is also the sort order. */
    public enum Verdict { MATCHES_KEY, MATCHES_KCV, POSSIBLE_ODD, POSSIBLE_EVEN, REJECTED }

    private final boolean ok;
    private final Operation operation;
    private final IcsfText error;
    private final List<Row> summary = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private final Map<String, String> outputs = new LinkedHashMap<>();
    private final List<Candidate> candidates = new ArrayList<>();

    private KeyWrapResult(boolean ok, Operation operation, IcsfText error) {
        this.ok = ok;
        this.operation = operation;
        this.error = error;
    }

    public static KeyWrapResult success(Operation operation) {
        return new KeyWrapResult(true, operation, null);
    }

    public static KeyWrapResult failure(Operation operation, IcsfText error) {
        return new KeyWrapResult(false, operation, error);
    }

    public boolean ok() {
        return ok;
    }

    public Operation operation() {
        return operation;
    }

    /** The reason the operation could not run, or {@code null} when it did. */
    public IcsfText error() {
        return error;
    }

    public KeyWrapResult add(IcsfText label, IcsfText value) {
        summary.add(new Row(label, value));
        return this;
    }

    public KeyWrapResult step(IcsfText title, String hexValue, IcsfText detail) {
        steps.add(new Step(title, hexValue, detail));
        return this;
    }

    public KeyWrapResult note(Level level, String code, IcsfText title, IcsfText text) {
        notes.add(new Note(level, code, title, text));
        return this;
    }

    public KeyWrapResult output(String name, String value) {
        outputs.put(name, value);
        return this;
    }

    public KeyWrapResult candidate(Candidate candidate) {
        candidates.add(candidate);
        return this;
    }

    public List<Row> summary() {
        return List.copyOf(summary);
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    public List<Note> notes() {
        return List.copyOf(notes);
    }

    /** Named results a caller can copy out: the token, the cryptogram, the key, the KCVs. */
    public Map<String, String> outputs() {
        return Map.copyOf(outputs);
    }

    public List<Candidate> candidates() {
        return List.copyOf(candidates);
    }

    /** Whether any note reached a given level, for deciding how to colour the pane. */
    public boolean hasNoteAtLeast(Level level) {
        return notes.stream().anyMatch(n -> n.level().ordinal() <= level.ordinal());
    }
}
