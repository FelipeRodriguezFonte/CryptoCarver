package com.cryptocarver.model;

import java.util.List;
import java.util.Objects;

/** Owns the mutable navigation state associated with an operation session trail. */
public final class SessionTrailState {
    private OperationSessionLog log = new OperationSessionLog();
    private int selectedIndex = -1;
    private boolean unsavedResult;

    public synchronized OperationSessionLog log() { return log; }
    public synchronized int selectedIndex() { return selectedIndex; }
    public synchronized boolean hasUnsavedResult() { return unsavedResult; }
    public synchronized int size() { return log.size(); }
    public synchronized List<SessionOperationStep> steps() { return log.getSteps(); }

    public synchronized SessionOperationStep add(OperationResult result, String title, List<String> tags,
                                                  java.util.Map<String, ?> parameters) {
        SessionOperationStep step = log.add(result, title, tags, parameters);
        unsavedResult = false;
        selectedIndex = log.size() - 1;
        return step;
    }

    public synchronized void resultPublished() { selectedIndex = -1; unsavedResult = true; }
    public synchronized void clearPublishedResult() { selectedIndex = -1; unsavedResult = false; }

    /** Previous navigation from the unsaved result selects the latest saved step. */
    public synchronized int previous() {
        if (log.isEmpty()) return -1;
        if (selectedIndex < 0) selectedIndex = log.size() - 1;
        else if (selectedIndex > 0) selectedIndex--;
        return selectedIndex;
    }

    /** Next navigation returns -1 for the unsaved/current-result position or no step. */
    public synchronized int next() {
        if (selectedIndex < 0) return -1;
        if (selectedIndex < log.size() - 1) selectedIndex++;
        else if (unsavedResult) selectedIndex = -1;
        return selectedIndex;
    }

    public synchronized boolean select(int index) {
        if (index < 0 || index >= log.size()) return false;
        selectedIndex = index;
        return true;
    }

    public synchronized void showCurrentResult() { selectedIndex = -1; }

    public synchronized void clear() {
        log.clear();
        selectedIndex = -1;
    }

    public synchronized void replace(OperationSessionLog replacement) {
        log = Objects.requireNonNullElseGet(replacement, OperationSessionLog::new);
        selectedIndex = -1;
    }
}
