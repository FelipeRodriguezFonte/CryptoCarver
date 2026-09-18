package com.cryptocarver.ui;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputControl;
import javafx.stage.Window;

/**
 * A menu item for the standard text-editing commands.
 *
 * <p>The command is deliberately implemented outside the main controller so
 * that Edit keeps the platform's native text semantics wherever the focused
 * control is a {@link TextInputControl}.  It has no accelerator: JavaFX and
 * the operating system already provide those shortcuts to text controls.</p>
 */
public final class NativeTextEditMenuItem extends MenuItem {
    public enum Operation { UNDO, REDO, CUT, COPY, PASTE, SELECT_ALL }

    private Operation operation;

    public NativeTextEditMenuItem() {
        setOnAction(this::perform);
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    private void perform(ActionEvent ignored) {
        TextInputControl input = focusedTextInput();
        if (input == null || operation == null) return;
        switch (operation) {
            case UNDO -> input.undo();
            case REDO -> input.redo();
            case CUT -> input.cut();
            case COPY -> input.copy();
            case PASTE -> input.paste();
            case SELECT_ALL -> input.selectAll();
        }
    }

    private TextInputControl focusedTextInput() {
        if (getParentMenu() == null || getParentMenu().getParentPopup() == null) return null;
        Window owner = getParentMenu().getParentPopup().getOwnerWindow();
        if (owner == null) return null;
        Scene scene = owner.getScene();
        if (scene == null) return null;
        Node focusOwner = scene.getFocusOwner();
        return focusOwner instanceof TextInputControl input ? input : null;
    }
}
