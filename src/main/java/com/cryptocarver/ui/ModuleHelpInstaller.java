package com.cryptocarver.ui;

import com.cryptocarver.model.help.HelpContext;
import com.cryptocarver.model.help.HelpContextSource;
import com.cryptocarver.model.help.ModuleHelpCatalog;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Attaches focus-following help to the controls that {@link ModuleHelpCatalog} declares for the active operation. */
final class ModuleHelpInstaller implements AutoCloseable {
    private final HelpContextSource state;
    private final Consumer<HelpContext> opener;
    private ContextualHelpButtonBinder binder;
    private String operationId = "";

    ModuleHelpInstaller(HelpContextSource state, Consumer<HelpContext> opener) {
        this.state = state;
        this.opener = opener;
    }

    /** Rebinds for {@code operation}; a blank or undeclared operation only releases the previous bindings. */
    void activate(String operation, Node root, boolean enabled) {
        close();
        ModuleHelpCatalog.Spec spec = operation == null ? null : ModuleHelpCatalog.spec(operation);
        if (spec == null || root == null) return;
        operationId = operation;
        binder = new ContextualHelpButtonBinder(state, opener);
        Map<String, List<Node>> nodes = nodesById(root);
        for (ModuleHelpCatalog.Field field : spec.fields()) {
            for (String id : field.controls()) for (Node node : nodes.getOrDefault(id, List.of()))
                binder.bind(operation, operation, () -> spec.actionFor(field.id(), currentAction()), field.id(), node);
        }
        for (ModuleHelpCatalog.Action action : spec.actions()) {
            for (String id : action.controls()) for (Node node : nodes.getOrDefault(id, List.of()))
                binder.bind(operation, operation, action.id(), "", node);
        }
        binder.setActiveRoute(operation);
        binder.setEnabled(enabled);
    }

    void setEnabled(boolean enabled) { if (binder != null) binder.setEnabled(enabled); }
    void refreshLocalization() { if (binder != null) binder.refreshLocalization(); }
    String operationId() { return operationId; }

    private String currentAction() {
        HelpContext current = state.current();
        return operationId.equals(current.operationId()) ? current.actionId() : "";
    }

    /** Every node with an id, including content of tabs and panes that has not been attached to the scene yet. */
    static Map<String, List<Node>> nodesById(Node root) {
        Map<String, List<Node>> found = new HashMap<>();
        collect(root, found, Collections.newSetFromMap(new IdentityHashMap<>()));
        return found;
    }

    private static void collect(Node node, Map<String, List<Node>> found, Set<Node> visited) {
        if (node == null || !visited.add(node)) return;
        if (node.getId() != null && !node.getId().isBlank()) found.computeIfAbsent(node.getId(), id -> new ArrayList<>()).add(node);
        if (node instanceof TabPane tabs) for (Tab tab : tabs.getTabs()) collect(tab.getContent(), found, visited);
        if (node instanceof Accordion accordion) accordion.getPanes().forEach(pane -> collect(pane, found, visited));
        if (node instanceof TitledPane pane) collect(pane.getContent(), found, visited);
        if (node instanceof ScrollPane scroll) collect(scroll.getContent(), found, visited);
        if (node instanceof SplitPane split) split.getItems().forEach(item -> collect(item, found, visited));
        if (node instanceof ToolBar bar) bar.getItems().forEach(item -> collect(item, found, visited));
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> collect(child, found, visited));
    }

    @Override public void close() {
        if (binder != null) { binder.close(); binder = null; }
        operationId = "";
    }
}
