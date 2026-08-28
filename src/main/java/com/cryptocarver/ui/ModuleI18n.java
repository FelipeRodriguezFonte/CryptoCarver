package com.cryptocarver.ui;

import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Accordion;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Applies a module's static UI translations while leaving runtime values untouched. */
public final class ModuleI18n {
    private ModuleI18n() { }

    public static Binding bind(Node root, Map<String, String> keys) {
        return bind(root, keys, new Node[0]);
    }

    public static Binding bind(Node root, Map<String, String> keys, Node... excludedRoots) {
        Binding binding = new Binding(root, keys == null ? Map.of() : keys, excludedRoots);
        binding.refresh();
        I18nService.getInstance().addLocaleChangeListener(locale -> {
            if (Platform.isFxApplicationThread()) {
                binding.refresh();
                return;
            }
            // Preferences can be changed by a background task (and by headless
            // tests). Scene-graph mutations must always return to the FX thread.
            try {
                Platform.runLater(binding::refresh);
            } catch (IllegalStateException ignored) {
                // No toolkit is available yet; the next FXML initialization
                // performs the initial refresh on the FX thread.
            }
        });
        return binding;
    }

    public static final class Binding {
        private final Node root;
        private final Map<String, String> keys;
        private final Set<Node> excludedRoots;
        private final List<TextEntry> entries = new ArrayList<>();
        private boolean indexed;

        private Binding(Node root, Map<String, String> keys, Node... excludedRoots) {
            this.root = root;
            this.keys = keys;
            this.excludedRoots = new HashSet<>();
            if (excludedRoots != null) {
                for (Node excluded : excludedRoots) if (excluded != null) this.excludedRoots.add(excluded);
            }
        }

        public void refresh() {
            Node focused = focusedNodeWithinRoot();
            if (!indexed) {
                index(root);
                indexed = true;
            }
            for (TextEntry entry : entries) entry.refresh();
            if (focused != null && focused.isFocusTraversable()) {
                Platform.runLater(() -> {
                    if (focused.getScene() != null && focused.isVisible() && !focused.isDisabled()) {
                        focused.requestFocus();
                    }
                });
            }
        }

        private Node focusedNodeWithinRoot() {
            if (root == null || root.getScene() == null) return null;
            Node focused = root.getScene().getFocusOwner();
            if (focused == null || focused == root) return null;
            for (Node current = focused; current != null; current = current.getParent()) {
                if (current == root) return focused;
            }
            return null;
        }

        private void index(Node node) {
            if (node == null) return;
            if (node != root && excludedRoots.contains(node)) return;
            if (node instanceof Labeled labeled) {
                addText(labeled.getText(), labeled::getText, labeled::setText);
            }
            if (node instanceof TextInputControl input) {
                addText(input.getPromptText(), input::getPromptText, input::setPromptText);
            }
            if (node instanceof Control control && control.getTooltip() != null) {
                Tooltip tooltip = control.getTooltip();
                addText(tooltip.getText(), tooltip::getText, tooltip::setText);
            }
            if (node.getAccessibleText() != null) {
                addText(node.getAccessibleText(), node::getAccessibleText, node::setAccessibleText);
            }
            if (node.getAccessibleHelp() != null) {
                addText(node.getAccessibleHelp(), node::getAccessibleHelp, node::setAccessibleHelp);
            }
            if (node instanceof MenuButton menuButton) {
                for (MenuItem item : menuButton.getItems()) index(item);
            }
            if (node instanceof TitledPane titledPane) {
                // A TitledPane's content is not among its children until the skin is
                // built, which has not happened when a controller binds in initialize().
                // Without this, binding a pane translates its title and nothing inside it.
                index(titledPane.getContent());
            }
            if (node instanceof Parent parent) {
                for (Node child : parent.getChildrenUnmodifiable()) index(child);
            }
            if (node instanceof TableView<?> tableView) {
                for (TableColumn<?, ?> column : tableView.getColumns()) index(column);
            }
            if (node instanceof TabPane tabPane) {
                for (Tab tab : tabPane.getTabs()) index(tab);
            }
            if (node instanceof Accordion accordion) {
                for (var pane : accordion.getPanes()) index(pane);
            }
        }

        private void index(MenuItem item) {
            if (item == null) return;
            addText(item.getText(), item::getText, item::setText);
            if (item instanceof Menu menu) {
                for (MenuItem child : menu.getItems()) index(child);
            }
        }

        private void index(Tab tab) {
            if (tab == null) return;
            addText(tab.getText(), tab::getText, tab::setText);
            // Same story as TitledPane: tab content is reachable through getContent()
            // but not through the TabPane's children before the skin exists.
            index(tab.getContent());
        }

        private void index(TableColumn<?, ?> column) {
            if (column == null) return;
            addText(column.getText(), column::getText, column::setText);
            for (TableColumn<?, ?> child : column.getColumns()) index(child);
        }

        private void addText(String original, java.util.function.Supplier<String> getter,
                             java.util.function.Consumer<String> setter) {
            if (original != null && !original.isBlank() && keys.containsKey(original)) {
                entries.add(new TextEntry(original, getter, setter, keys.get(original)));
            }
        }

        private final class TextEntry {
            private final String original;
            private final java.util.function.Supplier<String> getter;
            private final java.util.function.Consumer<String> setter;
            private final String key;
            private String translated;

            private TextEntry(String original, java.util.function.Supplier<String> getter,
                              java.util.function.Consumer<String> setter, String key) {
                this.original = original;
                this.getter = getter;
                this.setter = setter;
                this.key = key;
            }

            private void refresh() {
                String value = I18nService.getInstance().text(key);
                String current = getter.get();
                if (translated == null || original.equals(current) || translated.equals(current)) {
                    setter.accept(value);
                    translated = value;
                }
            }
        }
    }
}
