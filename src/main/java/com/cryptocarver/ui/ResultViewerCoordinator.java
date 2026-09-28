package com.cryptocarver.ui;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import javafx.stage.Window;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates result-area menus and expanded table viewers. */
final class ResultViewerCoordinator {
    private final ResultAreaTracker tracker;
    private final ExpandedTableViewer tableViewer;
    private final Consumer<TextArea> expandResult;
    private final Consumer<TextArea> addToShelf;
    private final Consumer<TextArea> copyAll;
    private final Consumer<TextArea> copySelection;
    private final Supplier<String> operation;
    private TableView<?> lastFocusedTable;

    ResultViewerCoordinator(ResultAreaTracker tracker, ExpandedTableViewer tableViewer,
                            Consumer<TextArea> expandResult, Consumer<TextArea> addToShelf,
                            Consumer<TextArea> copyAll, Consumer<TextArea> copySelection,
                            Supplier<String> operation) {
        this.tracker = tracker; this.tableViewer = tableViewer; this.expandResult = expandResult;
        this.addToShelf = addToShelf; this.copyAll = copyAll; this.copySelection = copySelection;
        this.operation = operation;
    }

    ContextMenu createContextMenu(TextArea area) {
        MenuItem expand = new MenuItem("Open in Expanded Viewer");
        expand.setOnAction(event -> { tracker.focus(area); expandResult.accept(area); });
        MenuItem shelf = new MenuItem("Add to Clipboard Shelf");
        shelf.setOnAction(event -> { tracker.focus(area); addToShelf.accept(area); });
        MenuItem copySystem = new MenuItem("Copy to System Clipboard");
        copySystem.setOnAction(event -> { tracker.focus(area); copyAll.accept(area); });
        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(event -> { tracker.focus(area); copySelection.accept(area); });
        MenuItem selectAll = new MenuItem("Select All"); selectAll.setOnAction(event -> area.selectAll());
        return new ContextMenu(expand, shelf, copySystem, new SeparatorMenuItem(), copy, selectAll);
    }

    void installTables(Pane mainPane) {
        if (mainPane == null) return;
        for (Node node : mainPane.lookupAll(".table-view")) if (node instanceof TableView<?> table) attachTable(table);
    }

    private void attachTable(TableView<?> table) {
        table.focusedProperty().addListener((observable, wasFocused, isFocused) -> { if (isFocused) lastFocusedTable = table; });
        MenuItem expand = new MenuItem("Open table in expanded viewer");
        expand.setOnAction(event -> openExpandedTable(table));
        table.setContextMenu(new ContextMenu(expand));
    }

    boolean hasFocusedTable() { return lastFocusedTable != null; }
    void openFocusedTable() { if (lastFocusedTable != null) openExpandedTable(lastFocusedTable); }

    private void openExpandedTable(TableView<?> table) {
        lastFocusedTable = table;
        Window owner = table.getScene() == null ? null : table.getScene().getWindow();
        tableViewer.show(owner, "Expanded Table — " + operation.get(), table);
    }
}
