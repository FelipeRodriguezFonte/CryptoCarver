package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

/** Supplies a safe accessible name and hit target for icon-only controls. */
public final class AccessibilitySupport {
    private AccessibilitySupport() { }

    public static void enrich(Node root) {
        if (root == null) return;
        if (root instanceof Button button && (button.getText() == null || button.getText().isBlank())
                && (button.getAccessibleText() == null || button.getAccessibleText().isBlank())) {
            String name = humanize(button.getId());
            button.setAccessibleText(name);
            if (button.getTooltip() == null) button.setTooltip(new Tooltip(name));
            button.setMinWidth(Math.max(24, button.getMinWidth()));
            button.setMinHeight(Math.max(24, button.getMinHeight()));
        }
        if (root instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(AccessibilitySupport::enrich);
    }

    static String humanize(String id) {
        if (id == null || id.isBlank()) return "Button";
        String value = id.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return value.isEmpty() ? "Button" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
