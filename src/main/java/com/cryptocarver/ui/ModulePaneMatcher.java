package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.scene.control.TitledPane;

import java.util.Map;

/** Shared matching for canonical navigation names and live-localized pane titles. */
final class ModulePaneMatcher {
    private ModulePaneMatcher() { }

    static boolean matches(TitledPane pane, String canonical, Map<String, String> catalog) {
        if (pane == null || canonical == null || canonical.isBlank()) return false;
        String visible = pane.getText() == null ? "" : pane.getText();
        if (containsEither(visible, canonical)) return true;
        if (matchesCatalog(visible, canonical, catalog)) return true;
        // A pane can be an included FXML that carries its own slice, so the caller's
        // module catalog does not necessarily hold that pane's title. Falling back to
        // every slice keeps navigation working across those boundaries instead of
        // silently degrading to English-only literal matching.
        for (Map<String, String> module : ModuleTextCatalog.allModules()) {
            if (matchesCatalog(visible, canonical, module)) return true;
        }
        return false;
    }

    private static boolean matchesCatalog(String visible, String canonical, Map<String, String> catalog) {
        if (catalog == null) return false;
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            if (containsEither(entry.getKey(), canonical)
                    && visible.equals(I18nService.getInstance().text(entry.getValue()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsEither(String left, String right) {
        String normalizedLeft = stripDecorations(left);
        String normalizedRight = stripDecorations(right);
        return normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft);
    }

    private static String stripDecorations(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "").trim();
    }
}
