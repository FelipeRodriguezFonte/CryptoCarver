package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextInputControl;

/** Reset helpers that intentionally operate only on the current module scene graph. */
final class ModuleResetSupport {
    private ModuleResetSupport() { }

    static Node clearInputsAndKeepFocus(Parent root) {
        if (root == null) return null;
        Node focused = ModuleResetPolicy.focusOwner(root);
        ModuleResetPolicy.clearTextInputs(root);
        ModuleResetPolicy.restoreFocus(focused);
        return focused;
    }
}
