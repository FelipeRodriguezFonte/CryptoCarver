package com.cryptocarver.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Finds controls by their contextual-help binding id and drives help the way a user does (focus, Shift+F1). */
final class ContextualHelpTestSupport {
    private ContextualHelpTestSupport() { }

    /** All controls carrying the binding id (with or without a leading '#'), in scene-graph order. */
    static List<Node> controls(Node root, String bindingId) {
        String id = bindingId.startsWith("#") ? bindingId.substring(1) : bindingId;
        List<Node> found = new ArrayList<>();
        walk(root, node -> {
            if (node.getProperties().get(ContextualHelpButtonBinder.BINDING_IDS_KEY) instanceof Set<?> ids && ids.contains(id))
                found.add(node);
        });
        return found;
    }

    /** First control carrying the binding id, or null. */
    static Node control(Node root, String bindingId) {
        List<Node> found = controls(root, bindingId);
        return found.isEmpty() ? null : found.get(0);
    }

    static Set<String> bindingIds(Node node) {
        return node.getProperties().get(ContextualHelpButtonBinder.BINDING_IDS_KEY) instanceof Set<?> ids
                ? new java.util.LinkedHashSet<>(ids.stream().map(String::valueOf).toList()) : Set.of();
    }

    /** Every node that has at least one help binding. */
    static List<Node> boundControls(Node root) {
        List<Node> found = new ArrayList<>();
        walk(root, node -> {
            if (node.getProperties().get(ContextualHelpButtonBinder.BINDING_IDS_KEY) instanceof Set<?> ids && !ids.isEmpty())
                found.add(node);
        });
        return found;
    }

    /** Dispatches Shift+F1 to the control, as the keyboard would when it owns the focus. */
    static void shiftF1(Node control) {
        Event.fireEvent(control, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F1, true, false, false, false));
    }

    static List<Node> contextHelpButtons(Node root) {
        List<Node> found = new ArrayList<>();
        walk(root, node -> { if (node.getStyleClass().contains("context-help-button")) found.add(node); });
        return found;
    }

    private static void walk(Node node, java.util.function.Consumer<Node> visitor) {
        walk(node, visitor, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    /** Also descends into content that a skin may not have attached yet (titled panes, tabs, scroll panes). */
    private static void walk(Node node, java.util.function.Consumer<Node> visitor, Set<Node> seen) {
        if (node == null || !seen.add(node)) return;
        visitor.accept(node);
        if (node instanceof javafx.scene.control.TitledPane pane) walk(pane.getContent(), visitor, seen);
        if (node instanceof javafx.scene.control.Accordion accordion)
            for (javafx.scene.control.TitledPane pane : accordion.getPanes()) walk(pane, visitor, seen);
        if (node instanceof javafx.scene.control.ScrollPane scroll) walk(scroll.getContent(), visitor, seen);
        if (node instanceof javafx.scene.control.TabPane tabs)
            for (javafx.scene.control.Tab tab : tabs.getTabs()) walk(tab.getContent(), visitor, seen);
        if (node instanceof javafx.scene.control.SplitPane split) for (Node item : split.getItems()) walk(item, visitor, seen);
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) walk(child, visitor, seen);
    }
}
