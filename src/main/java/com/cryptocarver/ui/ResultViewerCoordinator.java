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
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.ShelfPackage;

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
    private final ShelfServices shelfServices;
    private TableView<?> lastFocusedTable;

    ResultViewerCoordinator(ResultAreaTracker tracker, ExpandedTableViewer tableViewer,
                            Consumer<TextArea> expandResult, Consumer<TextArea> addToShelf,
                            Consumer<TextArea> copyAll, Consumer<TextArea> copySelection,
                            Supplier<String> operation, BooleanSupplier hasPublishedSnapshot,
                            Function<TextArea, String> resolveResultText,
                            Function<TextArea, OperationDetail.Classification> classification,
                            BooleanSupplier fullLab, Consumer<String> status,
                            Consumer<String> clipboard, Consumer<ClipboardEntry> revealShelfEntry,
                            ShelfServices shelfServices) {
        this.tracker = tracker; this.tableViewer = tableViewer; this.expandResult = expandResult;
        this.addToShelf = addToShelf; this.copyAll = copyAll; this.copySelection = copySelection;
        this.operation = operation;
        this.hasPublishedSnapshot = hasPublishedSnapshot; this.resolveResultText = resolveResultText;
        this.classification = classification; this.fullLab = fullLab; this.status = status; this.clipboard = clipboard;
        this.revealShelfEntry = revealShelfEntry;
        this.shelfServices = shelfServices;
    }

    interface ShelfServices {
        String capture(TextArea area);
        boolean blockedByVisibility(TextArea area);
        boolean isCurrentSelection(TextArea area);
        OperationResult snapshot();
        String activeOperation();
        ClipboardShelfManager manager();
        boolean isPrimaryCipherOutput(TextArea area);
        ShelfPackage createCipherPackage();
    }

    void addToClipboardShelfSecure(TextArea area, String selectedText) {
        String text;
        if (selectedText != null) {
            if (!shelfServices.isCurrentSelection(area)) { status.accept("Action blocked: Cannot securely add selection from old or unknown result."); return; }
            OperationDetail.Classification cls = classification.apply(area);
            if ((cls == OperationDetail.Classification.SECRET || cls == OperationDetail.Classification.SENSITIVE) && !fullLab.getAsBoolean()) {
                status.accept("Action blocked: Cannot add partial selection of protected text in current visibility mode."); return;
            }
            text = selectedText;
        } else text = shelfServices.capture(area);
        if (text == null || text.isEmpty()) {
            status.accept(shelfServices.blockedByVisibility(area) ? "Action blocked: output hidden by visibility policy." : "No current output available."); return;
        }
        if (text.equals("***MASKED***") || com.cryptocarver.model.ResultPresentationPolicy.isPrivateMaterialPlaceholder(text)) {
            status.accept(text.equals("***MASKED***") ? "Action blocked: output hidden by visibility policy." : "Action blocked: private output is not an explicit complete private-key area."); return;
        }
        ClipboardEntry.Format format = ClipboardEntry.Format.inferFormat(text);
        OperationDetail.Classification cls = classification.apply(area);
        boolean privateKeyMaterial = selectedText == null && (ResultAreaTracker.isPrivateKeyResultArea(area)
                || com.cryptocarver.model.ResultPresentationPolicy.isCompletePrivateKeyMaterial(text));
        OperationResult snapshot = shelfServices.snapshot();
        if (cls == OperationDetail.Classification.SECRET && privateKeyMaterial) {
            if (com.cryptocarver.model.ResultPresentationPolicy.isCompletePrivateKeyMaterial(text) && fullLab.getAsBoolean()) {
                String sourceOp = snapshot != null ? snapshot.getOperation() : (shelfServices.activeOperation() != null ? shelfServices.activeOperation() : "Unknown");
                ClipboardEntry sessionEntry = shelfServices.manager().addSessionOnlyPrivateKey(text, sourceOp, algorithm(snapshot));
                status.accept(sessionEntry != null ? "Added private key to Clipboard Shelf (session only)." : "Action blocked: session-only private keys require FULL_LAB.");
                revealShelfEntry.accept(sessionEntry); return;
            }
            status.accept(com.cryptocarver.model.ResultPresentationPolicy.isCompletePrivateKeyMaterial(text)
                    ? "Action blocked: private keys can only be added to the Shelf (session only) under FULL_LAB visibility."
                    : "Action blocked: private-key area does not contain complete, exportable key material."); return;
        }
        String sourceOp = snapshot != null ? snapshot.getOperation() : (shelfServices.activeOperation() != null ? shelfServices.activeOperation() : "Unknown");
        String algorithm = algorithm(snapshot);
        java.util.Optional<ClipboardEntry> duplicate = shelfServices.manager().findDuplicate(text, sourceOp);
        if (duplicate.isPresent()) { status.accept("Item already in Clipboard Shelf: " + duplicate.get().getLabel()); return; }
        ClipboardEntry entry = new ClipboardEntry("Copied from " + sourceOp, text, format, cls, sourceOp, algorithm);
        if (selectedText == null && shelfServices.isPrimaryCipherOutput(area)) {
            ShelfPackage packageData = shelfServices.createCipherPackage();
            if (packageData != null) {
                text = packageData.artifact("ciphertext"); format = ClipboardEntry.Format.inferFormat(text);
                entry = new ClipboardEntry("Authenticated ciphertext from " + sourceOp, text, format, cls, sourceOp, algorithm).withShelfPackage(packageData);
            }
        }
        shelfServices.manager().addEntry(entry); revealShelfEntry.accept(entry); status.accept("Added public output to Clipboard Shelf.");
    }

    private static String algorithm(OperationResult snapshot) {
        if (snapshot == null || snapshot.getDetails() == null) return null;
        for (OperationDetail detail : snapshot.getDetails()) if (detail != null
                && ("Algorithm".equalsIgnoreCase(detail.name()) || "Type".equalsIgnoreCase(detail.name()))) return detail.value();
        return null;
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
