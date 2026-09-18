package com.cryptocarver.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ordered, hash-linked operation trail that belongs to one saved workspace session.
 * The trail intentionally contains clear-text laboratory values, including secrets.
 */
public final class OperationSessionLog implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String id;
    private String createdAt;
    private List<SessionOperationStep> steps;

    public OperationSessionLog() {
        id = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        steps = new ArrayList<>();
    }

    private OperationSessionLog(OperationSessionLog source) {
        id = source != null && source.id != null ? source.id : UUID.randomUUID().toString();
        createdAt = source != null && source.createdAt != null
                ? source.createdAt : LocalDateTime.now().format(TIMESTAMP_FORMAT);
        steps = new ArrayList<>();
        if (source != null && source.steps != null) {
            for (SessionOperationStep step : source.steps) {
                if (step != null) steps.add(SessionOperationStep.copyOf(step));
            }
        }
    }

    public synchronized SessionOperationStep add(OperationResult result, String title, List<String> tags) {
        return add(result, title, tags, Map.of());
    }

    public synchronized SessionOperationStep add(OperationResult result, String title, List<String> tags,
                                                 Map<String, ?> parameters) {
        ensureSteps();
        String previousHash = steps.isEmpty()
                ? SessionOperationStep.GENESIS_HASH
                : steps.get(steps.size() - 1).getEntryHash();
        SessionOperationStep step = SessionOperationStep.capture(result, title, tags, parameters, previousHash);
        steps.add(step);
        return SessionOperationStep.copyOf(step);
    }

    public synchronized boolean remove(String stepId) {
        ensureSteps();
        if (stepId == null) return false;
        boolean removed = steps.removeIf(step -> step != null && stepId.equals(step.getId()));
        if (removed) relink();
        return removed;
    }

    public synchronized void clear() {
        ensureSteps();
        steps.clear();
    }

    public synchronized int size() {
        ensureSteps();
        return steps.size();
    }

    public synchronized boolean isEmpty() {
        return size() == 0;
    }

    public synchronized boolean verifyChain() {
        ensureSteps();
        String previous = SessionOperationStep.GENESIS_HASH;
        for (SessionOperationStep step : steps) {
            if (step == null || !step.hasValidHash(previous)) return false;
            previous = step.getEntryHash();
        }
        return true;
    }

    public synchronized List<SessionOperationStep> getSteps() {
        ensureSteps();
        List<SessionOperationStep> copy = new ArrayList<>();
        for (SessionOperationStep step : steps) {
            if (step != null) copy.add(SessionOperationStep.copyOf(step));
        }
        return Collections.unmodifiableList(copy);
    }

    public synchronized OperationSessionLog copy() {
        return new OperationSessionLog(this);
    }

    public synchronized String toText() {
        ensureSteps();
        StringBuilder report = new StringBuilder();
        report.append("CRYPTOCARVER SESSION OPERATION TRAIL\n")
                .append("*** WARNING: UNSAFE CLEAR-TEXT LABORATORY RECORD ***\n")
                .append("This file can contain keys, PINs, passwords, payloads and cryptographic results in clear text.\n")
                .append("Protect it as secret material.\n\n")
                .append("Session ID: ").append(id).append('\n')
                .append("Created: ").append(createdAt).append('\n')
                .append("Steps: ").append(steps.size()).append('\n')
                .append("Chain: ").append(verifyChain() ? "VALID" : "INVALID").append("\n\n");

        for (int index = 0; index < steps.size(); index++) {
            SessionOperationStep step = steps.get(index);
            report.append('[').append(index + 1).append("] ").append(step.getTitle()).append('\n')
                    .append("Timestamp: ").append(step.getTimestamp()).append('\n')
                    .append("Operation: ").append(step.getOperation()).append('\n');
            if (!step.getTags().isEmpty()) {
                report.append("Tags: ").append(String.join(", ", step.getTags())).append('\n');
            }
            if (step.getStatus() != null && !step.getStatus().isBlank()) {
                report.append("Status: ").append(step.getStatus()).append('\n');
            }
            appendPayload(report, "Input", step.isInputPresent(), step.getInputLength(),
                    step.getInputHex(), step.getInputText(), step.getInputFingerprint(), null);
            appendPayload(report, "Output", step.isOutputPresent(), step.getOutputLength(),
                    step.getOutputHex(), step.getOutputText(), step.getOutputFingerprint(),
                    step.getOutputClassification());
            if (step.getEnrichedOutput() != null) {
                report.append("Enriched output [")
                        .append(step.getEnrichedOutputClassification()).append("]:\n")
                        .append(step.getEnrichedOutput()).append('\n')
                        .append("Enriched output SHA-256: ")
                        .append(step.getEnrichedOutputFingerprint()).append('\n');
            }
            if (!step.getDetails().isEmpty()) {
                report.append("Details:\n");
                for (OperationDetail detail : step.getDetails()) {
                    report.append("  - ").append(detail.name()).append(" [")
                            .append(detail.classification()).append("]:\n")
                            .append(detail.value() == null ? "" : detail.value()).append('\n');
                }
            }
            if (!step.getParameters().isEmpty()) {
                report.append("Screen parameters (clear text):\n");
                step.getParameters().forEach((name, value) -> report.append("  - ")
                        .append(name).append(":\n").append(value).append('\n'));
            }
            report.append("Previous hash: ").append(step.getPreviousHash()).append('\n')
                    .append("Step hash: ").append(step.getEntryHash()).append("\n\n");
        }
        return report.toString();
    }

    public String getId() { return id; }
    public String getCreatedAt() { return createdAt; }

    private static void appendPayload(StringBuilder report, String label, boolean present, int length,
                                      String hex, String text, String fingerprint,
                                      OperationDetail.Classification classification) {
        report.append(label);
        if (classification != null) report.append(" [").append(classification).append(']');
        report.append(": ");
        if (!present) {
            report.append("(not present)\n");
            return;
        }
        report.append(length).append(" bytes\n")
                .append("  HEX: ").append(hex == null ? "" : hex).append('\n');
        if (text != null) {
            report.append("  UTF-8: ").append(text).append('\n');
        }
        report.append("  SHA-256: ").append(fingerprint).append('\n');
    }

    private void ensureSteps() {
        if (steps == null) steps = new ArrayList<>();
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (createdAt == null || createdAt.isBlank()) {
            createdAt = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        }
    }

    private void relink() {
        List<SessionOperationStep> linked = new ArrayList<>(steps.size());
        String previous = SessionOperationStep.GENESIS_HASH;
        for (SessionOperationStep step : steps) {
            if (step == null) continue;
            SessionOperationStep relinked = step.relink(previous);
            linked.add(relinked);
            previous = relinked.getEntryHash();
        }
        steps = linked;
    }
}
