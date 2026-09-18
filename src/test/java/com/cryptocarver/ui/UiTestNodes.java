package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;

/**
 * Scene-graph lookups shared by the UI tests.
 *
 * <p>A module's container used to be the module content itself, so tests reached its accordion
 * by casting the container or taking its first child. Containers are {@link ModuleHost} nodes
 * now and the module's FXML root sits underneath, which puts the accordion a level or two
 * deeper. Searching for it keeps the tests describing what they mean — "this module's
 * accordion" — instead of a fixed depth that the shell is free to change again.
 */
final class UiTestNodes {

    private UiTestNodes() {
    }

    /** The accordion of a module, whether {@code node} is one or merely contains one. */
    static Accordion accordionIn(Node node) {
        Accordion found = findAccordion(node);
        if (found == null) {
            throw new AssertionError("No accordion found under " + node);
        }
        return found;
    }

    private static Accordion findAccordion(Node node) {
        if (node instanceof Accordion accordion) return accordion;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Accordion found = findAccordion(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
