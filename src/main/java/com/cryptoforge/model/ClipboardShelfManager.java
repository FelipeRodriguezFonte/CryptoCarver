package com.cryptoforge.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import com.cryptoforge.ui.StatusReporter;

public class ClipboardShelfManager {
    private static final int MAX_ENTRIES = 100;

    // LinkedList to easily add to front and remove from back
    private final LinkedList<ClipboardEntry> entries = new LinkedList<>();
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private StatusReporter reporter;

    private static final ClipboardShelfManager INSTANCE = new ClipboardShelfManager();

    private ClipboardShelfManager() {
        // Private constructor for singleton
    }

    public static ClipboardShelfManager getInstance() {
        return INSTANCE;
    }

    public synchronized void setReporter(StatusReporter reporter) {
        this.reporter = reporter;
    }

    /** Registers a session-only listener used by open Clipboard Shelf views. */
    public void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.addIfAbsent(listener);
        }
    }

    /** Removes an open view's listener when that view is closed. */
    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    public synchronized void addEntry(ClipboardEntry entry) {
        if (entry == null) return;
        entries.addFirst(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        reportAction("Entry Added", "Added item to Clipboard Shelf");
        notifyChanged();
    }

    public synchronized void removeEntry(UUID id) {
        if (entries.removeIf(e -> e.getId().equals(id))) {
            reportAction("Entry Removed", "Removed item from Clipboard Shelf");
            notifyChanged();
        }
    }

    public synchronized void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            reportAction("Shelf Cleared", "Cleared all items from Clipboard Shelf");
            notifyChanged();
        }
    }

    public synchronized boolean renameEntry(UUID id, String newLabel) {
        for (int i = 0; i < entries.size(); i++) {
            ClipboardEntry e = entries.get(i);
            if (e.getId().equals(id)) {
                entries.set(i, e.withLabel(newLabel));
                reportAction("Entry Renamed", "Renamed item to: " + newLabel);
                notifyChanged();
                return true;
            }
        }
        return false;
    }

    private void reportAction(String operation, String message) {
        if (reporter != null) {
            reporter.publish(OperationResult.forOperation("Clipboard Shelf")
                .status(message)
                .build());
        }
    }

    private void notifyChanged() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A closed or faulty view must never prevent shelf storage.
            }
        }
    }

    public synchronized List<ClipboardEntry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized List<ClipboardEntry> search(String query, ClipboardEntry.Format format, OperationDetail.Classification classification) {
        return entries.stream()
                .filter(e -> {
                    if (query != null && !query.isBlank()) {
                        String q = query.toLowerCase();
                        boolean matchesLabel = e.getLabel() != null && e.getLabel().toLowerCase().contains(q);
                        boolean matchesSource = e.getSourceOperation() != null && e.getSourceOperation().toLowerCase().contains(q);
                        if (!matchesLabel && !matchesSource) return false;
                    }
                    if (format != null && e.getFormat() != format) {
                        return false;
                    }
                    if (classification != null && e.getClassification() != classification) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }
}
