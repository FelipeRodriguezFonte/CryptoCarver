package com.cryptocarver.ui;

import com.cryptocarver.model.CommandItem;
import com.cryptocarver.model.CommandSearchEngine;
import com.cryptocarver.model.PaletteCommandCatalog;
import java.util.List;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Owns the command palette overlay, result rendering, key handling, and focus. */
public final class CommandPaletteCoordinator {
    private final VBox overlay;
    private final TextField searchField;
    private final ListView<CommandItem> results;
    private final Label emptyLabel;
    private final Supplier<PaletteCommandCatalog.Actions> actionsProvider;
    private final javafx.collections.ObservableList<CommandItem> filteredCommands =
            javafx.collections.FXCollections.observableArrayList();
    private List<CommandItem> allCommands = List.of();
    private Node previousFocus;

    public CommandPaletteCoordinator(VBox overlay, TextField searchField, ListView<CommandItem> results,
            Label emptyLabel, Supplier<PaletteCommandCatalog.Actions> actionsProvider) {
        this.overlay = overlay;
        this.searchField = searchField;
        this.results = results;
        this.emptyLabel = emptyLabel;
        this.actionsProvider = actionsProvider;
    }

    public void initialize(Node sceneRoot) {
        if (sceneRoot != null) {
            sceneRoot.sceneProperty().addListener((observable, oldScene, newScene) -> {
                if (newScene != null) {
                    newScene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                            this::open);
                }
            });
        }
        if (overlay != null) {
            // A click on the dimmed backdrop (not on the card) closes the palette.
            overlay.setOnMouseClicked(event -> {
                if (event.getTarget() == overlay) {
                    close();
                    event.consume();
                }
            });
        }
        if (results == null || searchField == null) {
            return;
        }
        results.setItems(filteredCommands);
        results.setCellFactory(list -> new PaletteCommandCell());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> filter(newValue));
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                if (!filteredCommands.isEmpty()) {
                    results.getSelectionModel().select(0);
                    results.requestFocus();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                executeSelected();
                event.consume();
            }
        });
        results.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                executeSelected();
                event.consume();
            }
        });
        results.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                executeSelected();
            }
        });
    }

    public void open() {
        if (overlay == null) {
            return;
        }
        if (!overlay.isVisible()) {
            previousFocus = overlay.getScene() == null ? null : overlay.getScene().getFocusOwner();
        }
        allCommands = PaletteCommandCatalog.build(actionsProvider.get());
        overlay.setManaged(true);
        overlay.setVisible(true);
        if (searchField != null) {
            searchField.setText("");
            filter("");
            searchField.requestFocus();
        }
    }

    public void close() {
        if (overlay == null) {
            return;
        }
        overlay.setManaged(false);
        overlay.setVisible(false);
        if (searchField != null) {
            searchField.setText("");
        }
        Node target = previousFocus;
        previousFocus = null;
        if (target != null && target.getScene() != null) {
            target.requestFocus();
        }
    }

    public void executeSelected() {
        if (results == null) {
            return;
        }
        CommandItem selected = results.getSelectionModel().getSelectedItem();
        if (selected != null && selected.isEnabled()) {
            close();
            selected.execute();
        }
    }

    private void filter(String query) {
        List<CommandItem> matched = CommandSearchEngine.search(allCommands, query);
        filteredCommands.setAll(matched);
        if (emptyLabel != null) {
            boolean empty = matched.isEmpty();
            emptyLabel.setManaged(empty);
            emptyLabel.setVisible(empty);
        }
        if (results != null && !matched.isEmpty()) {
            results.getSelectionModel().select(0);
        }
    }

    private static final class PaletteCommandCell extends ListCell<CommandItem> {
        @Override
        protected void updateItem(CommandItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
                return;
            }
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("command-palette-item");
            Label category = new Label(item.getCategory());
            category.getStyleClass().add("command-palette-category");
            VBox text = new VBox(2);
            Label title = new Label(item.getTitle());
            title.getStyleClass().add("command-palette-item-title");
            Label description = new Label(item.getDescription());
            description.getStyleClass().add("command-palette-item-desc");
            text.getChildren().addAll(title, description);
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(category, text);
            if (item.getShortcut() != null && !item.getShortcut().isEmpty()) {
                Label shortcut = new Label(item.getShortcut());
                shortcut.getStyleClass().add("command-palette-shortcut");
                row.getChildren().add(shortcut);
            }
            row.setOpacity(item.isEnabled() ? 1.0 : 0.45);
            setGraphic(row);
        }
    }
}
