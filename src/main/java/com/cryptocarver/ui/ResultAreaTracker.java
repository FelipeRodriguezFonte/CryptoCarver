package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Tracks result controls independently from operation publication and keyboard focus order. */
final class ResultAreaTracker {

    private final Set<TextArea> registered = Collections.newSetFromMap(new IdentityHashMap<>());
    private TextArea focused;
    private TextArea updated;

    void install(Node root, Collection<TextArea> additionalAreas, Consumer<TextArea> configureArea) {
        if (root == null) return;
        Set<Node> candidates = new LinkedHashSet<>(root.lookupAll(".text-area"));
        if (additionalAreas != null) candidates.addAll(additionalAreas);
        for (Node node : candidates) {
            if (!(node instanceof TextArea area) || !isLikelyResultArea(area)) continue;
            if (!register(area)) continue;
            area.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (isFocused) focus(area);
            });
            area.textProperty().addListener((observable, previous, current) -> {
                if (current != null && !current.isBlank()) markUpdated(area);
            });
            if (configureArea != null) configureArea.accept(area);
        }
    }

    TextArea preferred(Node root, boolean publishedPayloadIsAuthoritative) {
        if (focused != null && isEffectivelyVisible(focused)) return focused;
        return publishedPayloadIsAuthoritative ? null : findVisible(root);
    }

    TextArea findVisible(Node root) {
        if (root == null) return null;
        return root.lookupAll(".text-area").stream()
                .filter(TextArea.class::isInstance)
                .map(TextArea.class::cast)
                .filter(this::isLikelyResultArea)
                .filter(ResultAreaTracker::isEffectivelyVisible)
                .filter(area -> area.getText() != null && !area.getText().isBlank())
                .max(Comparator.comparingInt(area -> area.getText().length()))
                .orElse(null);
    }

    boolean register(TextArea area) {
        return area != null && registered.add(area);
    }

    boolean isRegistered(TextArea area) {
        return area != null && registered.contains(area);
    }

    void focus(TextArea area) {
        focused = area;
    }

    void markUpdated(TextArea area) {
        updated = area;
    }

    boolean isCurrentSelection(TextArea area, boolean hasPublishedSnapshot) {
        return isKeyPairResultArea(area) || (hasPublishedSnapshot && area != null && area == updated);
    }

    void clearSelection() {
        focused = null;
        updated = null;
    }

    static boolean isKeyPairResultArea(TextArea area) {
        String id = area == null ? null : area.getId();
        if (id == null || area.isEditable()) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        return normalized.contains("publickeyarea") || normalized.contains("privatekeyarea");
    }

    static boolean isPrivateKeyResultArea(TextArea area) {
        String id = area == null ? null : area.getId();
        return id != null && !area.isEditable()
                && id.toLowerCase(Locale.ROOT).contains("privatekeyarea");
    }

    private boolean isLikelyResultArea(TextArea area) {
        String id = area == null ? null : area.getId();
        if (id == null) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        return normalized.contains("output") || normalized.contains("result")
                || normalized.contains("report") || normalized.contains("details")
                || normalized.contains("ciphertext") || isKeyPairResultArea(area);
    }

    private static boolean isEffectivelyVisible(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
            if (current instanceof TitledPane pane && !pane.isExpanded()) return false;
        }
        return true;
    }
}
