package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;

import java.util.Objects;

/**
 * Shared contract for specialized-module state actions.
 *
 * <p>The policy deliberately receives only the module scene graph and local
 * callbacks. It has no access to history, Clipboard Shelf, profiles, key
 * stores or shared secrets; those artifacts therefore cannot be erased by a
 * module reset.</p>
 */
public final class ModuleResetPolicy {
    public enum Action { CLEAR, RESET_DEFAULTS }

    public record Result(Action action, Node focusOwner, boolean sharedStatePreserved) { }

    private ModuleResetPolicy() { }

    public static Result apply(Node moduleRoot, Action action, Runnable clearLocalData,
                               Runnable restoreSafeDefaults) {
        Objects.requireNonNull(action, "action");
        Node focused = focusOwner(moduleRoot);
        if (action == Action.CLEAR && clearLocalData != null) clearLocalData.run();
        if (action == Action.RESET_DEFAULTS && restoreSafeDefaults != null) restoreSafeDefaults.run();
        restoreFocus(focused);
        return new Result(action, focused, true);
    }

    public static Node focusOwner(Node moduleRoot) {
        if (moduleRoot == null || moduleRoot.getScene() == null) return null;
        Node focused = moduleRoot.getScene().getFocusOwner();
        if (focused == null) return null;
        for (Node current = focused; current != null; current = current.getParent()) {
            if (current == moduleRoot) return focused;
        }
        return null;
    }

    public static void restoreFocus(Node focused) {
        if (focused == null || !focused.isFocusTraversable()) return;
        Runnable request = () -> {
            if (focused.getScene() != null && focused.isVisible() && focused.isManaged() && !focused.isDisabled()) {
                focused.requestFocus();
            }
        };
        request.run();
        if (Platform.isFxApplicationThread()) Platform.runLater(request);
    }

    /** Clears only text inputs below the supplied module root. */
    public static void clearTextInputs(Node node) {
        if (node == null) return;
        if (node instanceof javafx.scene.control.TextInputControl input) input.clear();
        if (node instanceof Accordion accordion) {
            accordion.getPanes().forEach(pane -> clearTextInputs(pane.getContent()));
        } else if (node instanceof TitledPane titledPane) {
            clearTextInputs(titledPane.getContent());
        } else if (node instanceof ScrollPane scrollPane) {
            clearTextInputs(scrollPane.getContent());
        } else if (node instanceof TabPane tabPane) {
            for (Tab tab : tabPane.getTabs()) clearTextInputs(tab.getContent());
        } else if (node instanceof SplitPane splitPane) {
            splitPane.getItems().forEach(ModuleResetPolicy::clearTextInputs);
        } else if (node instanceof DialogPane dialogPane) {
            clearTextInputs(dialogPane.getContent());
        } else if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) clearTextInputs(child);
        }
    }
}
