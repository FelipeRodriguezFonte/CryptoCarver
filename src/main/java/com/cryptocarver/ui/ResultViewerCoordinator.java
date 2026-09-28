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
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.ClipboardEntry;

/** Coordinates result-area menus and expanded table viewers. */
final class ResultViewerCoordinator {
    private final ResultAreaTracker tracker;
    private final ExpandedTableViewer tableViewer;
    private final Consumer<TextArea> expandResult;
    private final Consumer<TextArea> addToShelf;
    private final Consumer<TextArea> copyAll;
    private final Consumer<TextArea> copySelection;
    private final Supplier<String> operation;
    private final BooleanSupplier hasPublishedSnapshot;
    private final Function<TextArea, String> resolveResultText;
    private final Function<TextArea, OperationDetail.Classification> classification;
    private final BooleanSupplier fullLab;
    private final Consumer<String> status;
    private final Consumer<String> clipboard;
    private final Consumer<ClipboardEntry> revealShelfEntry;
    private TableView<?> lastFocusedTable;

    ResultViewerCoordinator(ResultAreaTracker tracker, ExpandedTableViewer tableViewer,
                            Consumer<TextArea> expandResult, Consumer<TextArea> addToShelf,
                            Consumer<TextArea> copyAll, Consumer<TextArea> copySelection,
                            Supplier<String> operation, BooleanSupplier hasPublishedSnapshot,
                            Function<TextArea, String> resolveResultText,
                            Function<TextArea, OperationDetail.Classification> classification,
                            BooleanSupplier fullLab, Consumer<String> status,
                            Consumer<String> clipboard, Consumer<ClipboardEntry> revealShelfEntry) {
        this.tracker = tracker; this.tableViewer = tableViewer; this.expandResult = expandResult;
        this.addToShelf = addToShelf; this.copyAll = copyAll; this.copySelection = copySelection;
        this.operation = operation;
        this.hasPublishedSnapshot = hasPublishedSnapshot; this.resolveResultText = resolveResultText;
        this.classification = classification; this.fullLab = fullLab; this.status = status; this.clipboard = clipboard;
        this.revealShelfEntry = revealShelfEntry;
    }

    void revealShelfEntry(ClipboardEntry entry) { revealShelfEntry.accept(entry); }

    void copySecure(TextArea area, String textToCopy, boolean selection) {
        if (!selection) {
            String content = resolveResultText.apply(area);
            if (content == null || content.isEmpty()) { status.accept("Action blocked: No current output available to copy."); return; }
            if (content.equals("***MASKED***")) { status.accept("Action blocked: Secret cannot be copied in current visibility mode."); return; }
            clipboard.accept(content); return;
        }
        if (textToCopy == null || textToCopy.isEmpty()) return;
        if (!tracker.isCurrentSelection(area, hasPublishedSnapshot.getAsBoolean())) {
            status.accept("Action blocked: Cannot securely copy selection from old or unknown result."); return;
        }
        OperationDetail.Classification cls = classification.apply(area);
        if ((cls == OperationDetail.Classification.SECRET || cls == OperationDetail.Classification.SENSITIVE) && !fullLab.getAsBoolean()) {
            status.accept("Action blocked: Cannot copy partial selection of protected text in current visibility mode."); return;
        }
        clipboard.accept(textToCopy);
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
