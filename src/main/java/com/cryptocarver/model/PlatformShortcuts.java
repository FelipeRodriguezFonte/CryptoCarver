package com.cryptocarver.model;

import java.util.Locale;

/**
 * Renders JavaFX accelerator strings ("Shortcut+Shift+F") the way the host platform
 * writes them.
 *
 * <p>The macOS glyphs have no counterpart in the default Windows and Linux UI fonts,
 * so baking them into labels or resource bundles leaves missing-glyph boxes in the
 * toolbar and tooltips of every non-Apple desktop.</p>
 */
public final class PlatformShortcuts {

    private PlatformShortcuts() {
    }

    /** Converts a JavaFX accelerator token into the platform's user-facing notation. */
    public static String display(String accelerator) {
        if (accelerator == null || accelerator.isBlank()) {
            return "";
        }
        if (!isMac()) {
            return accelerator.replace("Shortcut+", "Ctrl+");
        }
        return accelerator
                .replace("Shortcut+", "⌘")
                .replace("Shift+", "⇧");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
