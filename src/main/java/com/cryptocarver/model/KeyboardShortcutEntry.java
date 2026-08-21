package com.cryptocarver.model;

/**
 * Immutable model representing a system keyboard shortcut entry.
 */
public class KeyboardShortcutEntry {

    private final String actionName;
    private final String keyCombination;
    private final String description;
    private final String category;

    public KeyboardShortcutEntry(String actionName, String keyCombination, String description, String category) {
        this.actionName = actionName;
        this.keyCombination = keyCombination;
        this.description = description;
        this.category = category;
    }

    public String getActionName() {
        return actionName;
    }

    public String getKeyCombination() {
        return keyCombination;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getDisplayCombination() {
        return PlatformShortcuts.display(keyCombination);
    }
}
