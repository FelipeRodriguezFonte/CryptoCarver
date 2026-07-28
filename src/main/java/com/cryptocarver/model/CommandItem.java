package com.cryptocarver.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Represents a safe UI command item for the Command Palette.
 */
public final class CommandItem {
    private final String id;
    private final String title;
    private final String category;
    private final String description;
    private final List<String> keywords;
    private final String shortcut;
    private final BooleanSupplier enabledSupplier;
    private final Runnable action;

    public CommandItem(
            String id,
            String title,
            String category,
            String description,
            List<String> keywords,
            String shortcut,
            BooleanSupplier enabledSupplier,
            Runnable action
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.keywords = Collections.unmodifiableList(Objects.requireNonNull(keywords, "keywords must not be null"));
        this.shortcut = shortcut;
        this.enabledSupplier = enabledSupplier != null ? enabledSupplier : () -> true;
        this.action = Objects.requireNonNull(action, "action must not be null");
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public String getShortcut() {
        return shortcut;
    }

    public boolean isEnabled() {
        return enabledSupplier.getAsBoolean();
    }

    public void execute() {
        if (isEnabled()) {
            action.run();
        }
    }

    @Override
    public String toString() {
        return "[" + category + "] " + title + (shortcut != null ? " (" + shortcut + ")" : "");
    }
}
