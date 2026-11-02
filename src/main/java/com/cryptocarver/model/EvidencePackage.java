package com.cryptocarver.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Self-describing record of one exercise session, handed in by a student for review.
 *
 * <p>The seal is an unkeyed hash: it detects accidental or careless edits, not a
 * determined author, who can recompute it. It says nothing about authorship.</p>
 */
public final class EvidencePackage {
    public static final String FORMAT = "cryptocarver-evidence";
    public static final int FORMAT_VERSION = 1;

    public enum Integrity { VALID, CHAIN_BROKEN, SEAL_MISMATCH }

    private String format;
    private int formatVersion;
    private String student;
    private String exercise;
    private String appVersion;
    private String startedAt;
    private String exportedAt;
    private boolean clearTextValues;
    private int stepCount;
    private String seal;
    private OperationSessionLog trail;

    private EvidencePackage() {
        // Gson constructor.
    }

    /** Public results only unless {@code clearTextValues} asks for the complete trail. */
    public static EvidencePackage create(ExerciseSession session, OperationSessionLog log, String exportedAt,
                                         String appVersion, boolean clearTextValues) {
        if (session == null) throw new IllegalArgumentException("Exercise session is required");
        if (log == null) throw new IllegalArgumentException("Operation log is required");
        EvidencePackage evidence = new EvidencePackage();
        evidence.format = FORMAT;
        evidence.formatVersion = FORMAT_VERSION;
        evidence.student = session.student();
        evidence.exercise = session.exercise();
        evidence.appVersion = appVersion;
        evidence.startedAt = session.startedAt();
        evidence.exportedAt = exportedAt;
        evidence.clearTextValues = clearTextValues;
        evidence.trail = clearTextValues ? log.copy() : RedactedTrail.from(log);
        evidence.stepCount = evidence.trail.size();
        evidence.seal = evidence.calculateSeal();
        return evidence;
    }

    public Integrity verify() {
        try {
            if (trail == null || !trail.verifyChain() || trail.size() != stepCount) return Integrity.CHAIN_BROKEN;
            return calculateSeal().equals(seal) ? Integrity.VALID : Integrity.SEAL_MISMATCH;
        } catch (RuntimeException malformedTrail) {
            return Integrity.CHAIN_BROKEN;
        }
    }

    /** Covers the header and, through the last step's hash, the whole chain. */
    private String calculateSeal() {
        List<SessionOperationStep> steps = trail.getSteps();
        String head = steps.isEmpty() ? SessionOperationStep.GENESIS_HASH : steps.get(steps.size() - 1).getEntryHash();
        StringBuilder canonical = new StringBuilder();
        for (String value : List.of(text(format), Integer.toString(formatVersion), text(student), text(exercise),
                text(appVersion), text(startedAt), text(exportedAt), Boolean.toString(clearTextValues),
                Integer.toString(stepCount), text(trail.getId()), text(head))) {
            canonical.append(value.length()).append(':').append(value).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    private static String text(String value) { return value == null ? "" : value; }

    public String getFormat() { return format; }
    public int getFormatVersion() { return formatVersion; }
    public String getStudent() { return text(student); }
    public String getExercise() { return text(exercise); }
    public String getAppVersion() { return text(appVersion); }
    public String getStartedAt() { return text(startedAt); }
    public String getExportedAt() { return text(exportedAt); }
    public boolean isClearTextValues() { return clearTextValues; }
    public int getStepCount() { return stepCount; }
    public List<SessionOperationStep> getSteps() { return trail == null ? List.of() : trail.getSteps(); }
}
