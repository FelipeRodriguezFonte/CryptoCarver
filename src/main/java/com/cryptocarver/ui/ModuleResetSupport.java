package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextInputControl;

/** Reset helpers that intentionally operate only on the current module scene graph. */
final class ModuleResetSupport {
    private ModuleResetSupport() { }

    static Node clearInputsAndKeepFocus(Parent root) {
        if (root == null) return null;
        Node focused = root.getScene() == null ? null : root.getScene().getFocusOwner();
        clear(root);
        if (focused != null && focused.isFocusTraversable() && focused.isVisible() && focused.isManaged()) {
            focused.requestFocus();
        }
        return focused;
    }

    private static void clear(Node node) {
        if (node instanceof TextInputControl input) input.clear();
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) clear(child);
        }
    }
}
