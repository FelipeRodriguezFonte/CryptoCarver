package com.cryptocarver.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for all application keyboard shortcuts.
 */
public class KeyboardShortcutRegistry {

    private static final List<KeyboardShortcutEntry> SHORTCUTS = List.of(
            new KeyboardShortcutEntry("Command Palette", "Shortcut+K", "Open operation search and quick palette", "Navigation"),
            new KeyboardShortcutEntry("Keyboard Shortcuts", "F1", "Show application keyboard shortcuts reference", "Help"),
            new KeyboardShortcutEntry("Save Session", "Shortcut+S", "Save current workspace state to session file", "File"),
            new KeyboardShortcutEntry("Import Key", "Shortcut+O", "Import cryptographic key or certificate file", "File"),
            new KeyboardShortcutEntry("Exit Application", "Shortcut+Q", "Close CryptoCarver workbench", "File"),
            new KeyboardShortcutEntry("Clear Input", "Shortcut+Shift+I", "Clear all input fields in active view", "Edit"),
            new KeyboardShortcutEntry("Clear Output", "Shortcut+Shift+O", "Clear output results in active view", "Edit"),
            new KeyboardShortcutEntry("Copy Output", "Shortcut+Shift+C", "Copy operation result text to system clipboard", "Edit"),
            new KeyboardShortcutEntry("Toggle Side Panel", "Shortcut+B", "Show/hide navigation sidebar", "View"),
            new KeyboardShortcutEntry("Toggle Inspector", "Shortcut+I", "Show/hide result inspector panel", "View"),
            new KeyboardShortcutEntry("Expand Result", "Shortcut+Shift+E", "Open expanded result text viewer", "View"),
            new KeyboardShortcutEntry("Expand Table", "Shortcut+Shift+T", "Open expanded table data viewer", "View"),
            new KeyboardShortcutEntry("Zoom In (Font)", "Shortcut+PLUS", "Increase application interface font size", "View"),
            new KeyboardShortcutEntry("Zoom Out (Font)", "Shortcut+MINUS", "Decrease application interface font size", "View"),
            new KeyboardShortcutEntry("Epoch Converter", "Shortcut+T", "Open Unix timestamp epoch conversion tool", "Tools"),
            new KeyboardShortcutEntry("JSON Formatter", "Shortcut+J", "Open JSON formatter and validator tool", "Tools"),
            new KeyboardShortcutEntry("Quick Start", "Shortcut+Shift+H", "Navigate to Laboratory Quick Start dashboard", "View"),
            new KeyboardShortcutEntry("Toggle Favorite", "Shortcut+Shift+F", "Toggle favorite star for current operation", "Navigation")
    );

    public static List<KeyboardShortcutEntry> getShortcuts() {
        return Collections.unmodifiableList(SHORTCUTS);
    }

    public static Optional<KeyboardShortcutEntry> findShortcutByAction(String actionName) {
        if (actionName == null || actionName.isBlank()) return Optional.empty();
        String clean = actionName.replace("...", "").trim().toLowerCase();
        return SHORTCUTS.stream()
                .filter(s -> {
                    String regClean = s.getActionName().toLowerCase();
                    return regClean.equalsIgnoreCase(clean)
                            || regClean.startsWith(clean)
                            || clean.startsWith(regClean);
                })
                .findFirst();
    }
}
