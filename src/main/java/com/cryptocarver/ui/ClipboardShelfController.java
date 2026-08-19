package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.ResultComparator;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ClipboardShelfController {

    @FXML private javafx.scene.layout.VBox clipboardShelfRoot;
    private ModuleI18n.Binding moduleI18n;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> pinnedFilterCombo;
    @FXML private ComboBox<String> sourceFilterCombo;
    @FXML private ComboBox<ClipboardEntry.Format> formatFilterCombo;
    @FXML private ComboBox<OperationDetail.Classification> classFilterCombo;
    @FXML private Label itemCountLabel;

    @FXML TableView<ClipboardEntry> shelfTable;
    @FXML private TableColumn<ClipboardEntry, String> pinCol;
    @FXML private TableColumn<ClipboardEntry, String> dateCol;
    @FXML private TableColumn<ClipboardEntry, String> labelCol;
    @FXML private TableColumn<ClipboardEntry, String> sourceCol;
    @FXML private TableColumn<ClipboardEntry, String> algCol;
    @FXML private TableColumn<ClipboardEntry, String> formatCol;
    @FXML private TableColumn<ClipboardEntry, String> classCol;
    @FXML private TableColumn<ClipboardEntry, String> tagsCol;
    @FXML private TableColumn<ClipboardEntry, String> previewCol;

    @FXML private Label warningLabel;
    @FXML private Button pinBtn;
    @FXML private Button editTagsNoteBtn;
    @FXML Button compareBtn;
    @FXML private MenuButton useInMenu;
    @FXML Button copyBtn;
    @FXML private Button expandBtn;
    @FXML private Button renameBtn;
    @FXML private Button deleteBtn;
    @FXML TextArea detailsArea;

    private ClipboardShelfManager manager;
    private final ObservableList<ClipboardEntry> tableData = FXCollections.observableArrayList();
    private OperationNavigator navigator;
    private ModernMainController mainController;
    /** One-shot override used to reveal a just-created entry despite filters. */
    private UUID entryToReveal;
    private Runnable shelfChangeListener;
    private java.util.function.Consumer<java.util.Locale> localeChangeListener;
    private javafx.beans.value.ChangeListener<javafx.scene.Scene> shelfSceneListener;
    private javafx.beans.value.ChangeListener<javafx.stage.Window> shelfWindowListener;
    private javafx.stage.Window observedShelfWindow;
    private javafx.event.EventHandler<javafx.stage.WindowEvent> shelfWindowHiddenHandler;
    private boolean shelfChangeListenerAttached;
    private boolean disposed;

    private String t(String key, Object... args) {
        return I18nService.getInstance().text(key, args);
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        moduleI18n = ModuleI18n.bind(clipboardShelfRoot, ModuleTextCatalog.clipboardShelf());
        localeChangeListener = locale -> {
            if (disposed) return;
            pinnedFilterCombo.getItems().setAll(t("module.shelf.allPinned"), t("module.shelf.pinnedOnly"), t("module.shelf.unpinnedOnly"));
            pinnedFilterCombo.setValue(t("module.shelf.allPinned"));
            populateUseInMenu();
            refresh();
        };
        I18nService.getInstance().addLocaleChangeListener(localeChangeListener);
        manager = ClipboardShelfManager.getInstance();
        shelfChangeListener = () -> {
            if (disposed) return;
            Runnable refreshTask = () -> {
                if (disposed) return;
                ClipboardEntry newest = manager.getEntries().stream()
                        .max(java.util.Comparator.comparing(ClipboardEntry::getCreatedAt))
                        .orElse(null);
                if (newest != null) refreshAndReveal(newest.getId());
                else refresh();
            };
            if (javafx.application.Platform.isFxApplicationThread()) refreshTask.run();
            else javafx.application.Platform.runLater(refreshTask);
        };

        shelfWindowListener = (observable, oldWindow, newWindow) -> attachShelfWindow(newWindow);
        shelfSceneListener = (observable, oldScene, newScene) -> {
            if (oldScene != null && oldScene != newScene) detachShelfScene(oldScene);
            if (newScene != null) attachShelfScene(newScene);
        };
        clipboardShelfRoot.sceneProperty().addListener(shelfSceneListener);
        if (clipboardShelfRoot.getScene() != null) attachShelfScene(clipboardShelfRoot.getScene());

        pinnedFilterCombo.getItems().setAll(t("module.shelf.allPinned"), t("module.shelf.pinnedOnly"), t("module.shelf.unpinnedOnly"));
        pinnedFilterCombo.setValue(t("module.shelf.allPinned"));

        formatFilterCombo.getItems().add(null);
        formatFilterCombo.getItems().addAll(ClipboardEntry.Format.values());

        classFilterCombo.getItems().add(null);
        classFilterCombo.getItems().addAll(OperationDetail.Classification.values());

        shelfTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        pinCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().isPinned() ? "📌" : ""));
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt().format(TIME_FORMATTER)));
        labelCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLabel()));
        sourceCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSourceOperation() != null ? cellData.getValue().getSourceOperation() : "—"));
        algCol.setCellValueFactory(cellData -> {
            ClipboardEntry entry = cellData.getValue();
            if (entry.getShelfPackage() != null) {
                return new SimpleStringProperty(entry.getShelfPackage().artifact("algorithm") + "/"
                        + entry.getShelfPackage().artifact("mode"));
            }
            return new SimpleStringProperty(entry.getAlgorithm() != null ? entry.getAlgorithm() : "—");
        });
        formatCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().isSessionOnlyPrivateKey()
                        ? "SESSION_ONLY_PRIVATE_KEY/" + cellData.getValue().getFormat().name()
                : cellData.getValue().getEntryKind() == ClipboardEntry.EntryKind.STRUCTURED
                        ? "PACKAGE/" + cellData.getValue().getFormat().name()
                        : cellData.getValue().getFormat().name()));
        classCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getClassification().name()));
        tagsCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.join(", ", cellData.getValue().getTags())));
        previewCol.setCellValueFactory(cellData -> new SimpleStringProperty(getMaskedValue(cellData.getValue(), true)));

        shelfTable.setItems(tableData);
        shelfTable.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<ClipboardEntry>) c -> updateSelectionUi());

        searchField.textProperty().addListener((obs, oldVal, newVal) -> refresh());
        pinnedFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> refresh());
        sourceFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> refresh());
        formatFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> refresh());
        classFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> refresh());

        populateUseInMenu();
        refresh();
    }

    private void attachShelfScene(javafx.scene.Scene scene) {
        scene.windowProperty().addListener(shelfWindowListener);
        attachShelfWindow(scene.getWindow());
        if (!shelfChangeListenerAttached) {
            manager.addChangeListener(shelfChangeListener);
            shelfChangeListenerAttached = true;
        }
    }

    private void detachShelfScene(javafx.scene.Scene scene) {
        if (scene != null) scene.windowProperty().removeListener(shelfWindowListener);
        detachShelfWindow();
        if (shelfChangeListenerAttached) {
            manager.removeChangeListener(shelfChangeListener);
            shelfChangeListenerAttached = false;
        }
    }

    private void attachShelfWindow(javafx.stage.Window window) {
        if (observedShelfWindow == window) return;
        detachShelfWindow();
        if (window == null) return;
        shelfWindowHiddenHandler = event -> dispose();
        window.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDING, shelfWindowHiddenHandler);
        observedShelfWindow = window;
    }

    private void detachShelfWindow() {
        if (observedShelfWindow != null && shelfWindowHiddenHandler != null) {
            observedShelfWindow.removeEventHandler(javafx.stage.WindowEvent.WINDOW_HIDING, shelfWindowHiddenHandler);
        }
        observedShelfWindow = null;
        shelfWindowHiddenHandler = null;
    }

    /** Releases the Shelf's manager/locale listeners when its view is closed. */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (clipboardShelfRoot != null && shelfSceneListener != null) {
            javafx.scene.Scene scene = clipboardShelfRoot.getScene();
            clipboardShelfRoot.sceneProperty().removeListener(shelfSceneListener);
            detachShelfScene(scene);
        } else if (shelfChangeListenerAttached) {
            manager.removeChangeListener(shelfChangeListener);
            shelfChangeListenerAttached = false;
        }
        if (localeChangeListener != null) {
            I18nService.getInstance().removeLocaleChangeListener(localeChangeListener);
        }
    }

    public void setNavigator(OperationNavigator navigator, ModernMainController mainController) {
        this.navigator = navigator;
        this.mainController = mainController;
    }

    public void refresh() {
        String query = searchField.getText();
        String pinnedSelection = pinnedFilterCombo.getValue();
        Boolean pinnedFilter = null;
        if (t("module.shelf.pinnedOnly").equalsIgnoreCase(pinnedSelection)) pinnedFilter = true;
        else if (t("module.shelf.unpinnedOnly").equalsIgnoreCase(pinnedSelection)) pinnedFilter = false;

        String sourceFilter = sourceFilterCombo.getValue();
        if (t("module.shelf.allOperations").equalsIgnoreCase(sourceFilter) || "All".equalsIgnoreCase(sourceFilter)) {
            sourceFilter = null;
        }
        ClipboardEntry.Format fmt = formatFilterCombo.getValue();
        OperationDetail.Classification cls = classFilterCombo.getValue();

        List<ClipboardEntry> allEntries = manager.getEntries();
        refreshSourceFilterOptions(allEntries);

        List<ClipboardEntry> filtered = manager.search(query, pinnedFilter, sourceFilter, fmt, cls);
        tableData.setAll(filtered);

        UUID requestedReveal = entryToReveal;
        ClipboardEntry entryToSelect = null;
        if (requestedReveal != null) {
            entryToSelect = manager.getEntries().stream()
                    .filter(entry -> requestedReveal.equals(entry.getId()))
                    .findFirst().orElse(null);
            if (entryToSelect != null && !tableData.contains(entryToSelect)) {
                // Preserve the user's filters while making the new result
                // visible and selected for this one refresh only.
                tableData.add(0, entryToSelect);
            }
        }
        // Keep the one-shot reveal pinned until another entry is requested.
        // Clearing it merely because the Shelf is visible lets a filter refresh
        // immediately discard the freshly-added row and its selection.
        entryToReveal = entryToSelect != null ? requestedReveal : null;
        itemCountLabel.setText(t("module.shelf.itemCount", tableData.size()));

        updateSelectionUi();
        if (entryToSelect != null) {
            shelfTable.getSelectionModel().clearAndSelect(tableData.indexOf(entryToSelect));
            shelfTable.scrollTo(entryToSelect);
        }
    }

    /** Refreshes the integrated view and selects a newly-created entry once. */
    public void refreshAndReveal(UUID entryId) {
        if (entryId == null) {
            refresh();
            return;
        }
        entryToReveal = entryId;
        refresh();
    }

    private boolean isShelfViewEffectivelyVisible() {
        for (javafx.scene.Node node = clipboardShelfRoot; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
            if (node instanceof TitledPane pane && !pane.isExpanded()) return false;
        }
        return true;
    }

    private void refreshSourceFilterOptions(List<ClipboardEntry> entries) {
        String current = sourceFilterCombo.getValue();
        List<String> sources = new ArrayList<>();
        sources.add(t("module.shelf.allOperations"));
        entries.stream()
                .map(ClipboardEntry::getSourceOperation)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .forEach(sources::add);

        if (!sourceFilterCombo.getItems().equals(sources)) {
            sourceFilterCombo.getItems().setAll(sources);
            if (current != null && sources.contains(current)) {
                sourceFilterCombo.setValue(current);
            } else {
                sourceFilterCombo.setValue(t("module.shelf.allOperations"));
            }
        }
    }

    private void updateSelectionUi() {
        ObservableList<ClipboardEntry> selected = shelfTable.getSelectionModel().getSelectedItems();
        int size = selected.size();

        if (size == 0) {
            clearDetails();
        } else if (size == 1) {
            showDetails(selected.get(0));
            populateUseInMenu(selected.get(0));
            setActionAvailability(compareBtn, false, "Open comparison", "Select exactly two items to compare");
        } else if (size == 2) {
            showComparisonSummary(selected.get(0), selected.get(1));
        } else {
            detailsArea.setText(t("module.shelf.multiSelection", size));
            warningLabel.setVisible(false);
            setActionAvailability(pinBtn, false, "Pin selected entry", "Select one entry to pin or unpin");
            setActionAvailability(editTagsNoteBtn, false, "Edit note and tags", "Select one entry to edit its note and tags");
            setActionAvailability(compareBtn, false, "Open comparison", "Select exactly two items to compare");
            setActionAvailability(useInMenu, false, "Use selected result in an operation", "Select one entry to use its result");
            setActionAvailability(copyBtn, false, "Copy result", "Select one entry to copy its result");
            setActionAvailability(expandBtn, false, "Open full result", "Select one entry to open its result");
            setActionAvailability(renameBtn, false, "Rename entry", "Select one entry to rename it");
            setActionAvailability(deleteBtn, true, "Delete selected entries", "Select an entry to delete it");
        }
    }

    private void clearDetails() {
        detailsArea.clear();
        warningLabel.setVisible(false);
        setActionAvailability(pinBtn, false, "Pin selected entry", "Select one entry to pin or unpin");
        setActionAvailability(editTagsNoteBtn, false, "Edit note and tags", "Select one entry to edit its note and tags");
        setActionAvailability(compareBtn, false, "Open comparison", "Select exactly two items to compare");
        setActionAvailability(useInMenu, false, "Use selected result in an operation", "Select one entry to use its result");
        setActionAvailability(copyBtn, false, "Copy result", "Select one entry to copy its result");
        setActionAvailability(expandBtn, false, "Open full result", "Select one entry to open its result");
        setActionAvailability(renameBtn, false, "Rename entry", "Select one entry to rename it");
        setActionAvailability(deleteBtn, false, "Delete selected entries", "Select an entry to delete it");
    }

    private void showDetails(ClipboardEntry entry) {
        if (entry == null) {
            clearDetails();
            return;
        }

        boolean isSensitive = entry.getClassification() == OperationDetail.Classification.SECRET ||
                              entry.getClassification() == OperationDetail.Classification.SENSITIVE;

        SecretVisibilityProfile visibility = AppSettings.getInstance().getSecretVisibilityProfile();
        boolean isRedacted = isSensitive && visibility == SecretVisibilityProfile.REDACTED;
        boolean isMasked = isSensitive && visibility == SecretVisibilityProfile.MASKED;
        boolean canCopy = !entry.isSessionOnlyPrivateKey() && !isRedacted && !isMasked;
        boolean canUse = entry.isSessionOnlyPrivateKey()
                ? visibility == SecretVisibilityProfile.FULL_LAB
                : canCopy;

        StringBuilder sb = new StringBuilder();
        sb.append("Label: ").append(entry.getLabel()).append("\n");
        sb.append("Source: ").append(entry.getSourceOperation() != null ? entry.getSourceOperation() : "—").append("\n");
        sb.append("Algorithm: ").append(entry.getAlgorithm() != null ? entry.getAlgorithm() : "—").append("\n");
        sb.append("Format: ").append(entry.getFormat()).append(" · Size: ").append(entry.getByteLength() != null ? entry.getByteLength() + " bytes" : "—").append("\n");
        sb.append("Shelf contract: ").append(entry.getEntryKind());
        if (entry.isSessionOnlyPrivateKey()) {
            sb.append(" · SESSION_ONLY_PRIVATE_KEY · session-only in memory; disappears when the application closes");
        }
        if (entry.getShelfPackage() != null) sb.append(" · ").append(entry.getShelfPackage().displaySummary());
        if (entry.getNonReusableReason() != null) sb.append(" · ").append(entry.getNonReusableReason());
        sb.append("\n");
        sb.append("Classification: ").append(entry.getClassification()).append("\n");
        sb.append("Pinned: ").append(entry.isPinned() ? "Yes 📌" : "No").append("\n");
        if (!entry.getTags().isEmpty()) {
            sb.append("Tags: ").append(projectAnnotation(String.join(", ", entry.getTags()), visibility, isSensitive)).append("\n");
        }
        if (!entry.getNote().isBlank()) {
            sb.append("Note: ").append(projectAnnotation(entry.getNote(), visibility, isSensitive)).append("\n");
        }
        sb.append("----------------------------------------\n");
        sb.append(getMaskedValue(entry, false));

        detailsArea.setText(sb.toString());

        warningLabel.setVisible(isSensitive);
        if (isSensitive) {
            warningLabel.setText(entry.isSessionOnlyPrivateKey()
                ? (visibility == SecretVisibilityProfile.FULL_LAB
                    ? "⚠️ Private key — session only. In memory only; disappears when the application closes."
                    : "🔒 Private key — session only is blocked by the active visibility policy.")
                : (visibility == SecretVisibilityProfile.FULL_LAB
                    ? "⚠️ Sensitive data displayed (Unsafe Lab mode)"
                    : "⚠️ Sensitive data (Masked/Redacted)"));
        }

        setActionAvailability(pinBtn, true, entry.isPinned() ? "Unpin entry" : "Pin entry", "Select one entry to pin or unpin");
        pinBtn.setText(entry.isPinned() ? t("module.shelf.unpin") : t("module.shelf.pin"));
        setActionAvailability(editTagsNoteBtn, true, "Edit note and tags", "Select one entry to edit its note and tags");
        setActionAvailability(useInMenu, canUse, "Use selected result in an operation",
                "Unavailable under the active visibility profile");
        setActionAvailability(copyBtn, canCopy, "Copy result", "Unavailable under the active visibility profile");
        setActionAvailability(expandBtn, canCopy, "Open full result", "Unavailable under the active visibility profile");
        setActionAvailability(renameBtn, true, "Rename entry", "Select one entry to rename it");
        setActionAvailability(deleteBtn, true, "Delete selected entries", "Select an entry to delete it");
        populateUseInMenu(entry);
    }

    private void showComparisonSummary(ClipboardEntry e1, ClipboardEntry e2) {
        if (e1.isSessionOnlyPrivateKey() || e2.isSessionOnlyPrivateKey()) {
            detailsArea.setText("Comparison blocked: session-only private-key entries cannot be compared or exported.");
            warningLabel.setVisible(true);
            warningLabel.setText("🔒 Session-only private keys are excluded from comparison and reports.");
            setActionAvailability(pinBtn, false, "Pin selected entry", "Select one entry to pin or unpin");
            setActionAvailability(editTagsNoteBtn, false, "Edit note and tags", "Select one entry to edit its note and tags");
            setActionAvailability(useInMenu, false, "Use selected result in an operation", "Select one entry to use its result");
            setActionAvailability(copyBtn, false, "Copy result", "Session-only private keys cannot be copied");
            setActionAvailability(expandBtn, false, "Open full result", "Session-only private keys cannot be opened here");
            setActionAvailability(renameBtn, false, "Rename entry", "Select one entry to rename it");
            setActionAvailability(deleteBtn, true, "Delete selected entries", "Select an entry to delete it");
            compareBtn.setDisable(true);
            compareBtn.setTooltip(new Tooltip("Session-only private keys cannot be compared or exported"));
            return;
        }
        SecretVisibilityProfile profile = AppSettings.getInstance().getSecretVisibilityProfile();
        ResultComparator.ComparisonDetails details = ResultComparator.compare(e1, e2, profile);

        StringBuilder sb = new StringBuilder();
        sb.append("Selected 2 items for Comparison:\n");
        sb.append("1) ").append(e1.getLabel()).append(" (").append(e1.getFormat()).append(")\n");
        sb.append("2) ").append(e2.getLabel()).append(" (").append(e2.getFormat()).append(")\n");
        sb.append("----------------------------------------\n");
        sb.append("Status: ").append(details.status().getLabel()).append("\n");
        sb.append("Summary: ").append(details.summary()).append("\n");

        detailsArea.setText(sb.toString());

        warningLabel.setVisible(false);
        setActionAvailability(pinBtn, false, "Pin selected entry", "Select one entry to pin or unpin");
        setActionAvailability(editTagsNoteBtn, false, "Edit note and tags", "Select one entry to edit its note and tags");
        setActionAvailability(useInMenu, false, "Use selected result in an operation", "Select one entry to use its result");
        setActionAvailability(copyBtn, false, "Copy result", "Select one entry to copy its result");
        setActionAvailability(expandBtn, false, "Open full result", "Select one entry to open its result");
        setActionAvailability(renameBtn, false, "Rename entry", "Select one entry to rename it");
        setActionAvailability(deleteBtn, true, "Delete selected entries", "Select an entry to delete it");

        boolean comparable = details.status() != ResultComparator.Status.NOT_COMPARABLE;
        compareBtn.setDisable(!comparable);
        if (comparable) {
            compareBtn.setTooltip(new Tooltip(t("module.shelf.compareTooltip")));
        } else {
            compareBtn.setTooltip(new Tooltip(details.summary()));
        }
    }

    private void setActionAvailability(Control control, boolean enabled, String enabledTip, String disabledTip) {
        if (control == null) return;
        control.setDisable(!enabled);
        control.setTooltip(new Tooltip(enabled ? enabledTip : disabledTip));
    }

    private String getMaskedValue(ClipboardEntry entry, boolean truncate) {
        if (entry == null || entry.getValue() == null) return "";
        String val = entry.getValue();

        boolean isSensitive = entry.getClassification() == OperationDetail.Classification.SECRET ||
                              entry.getClassification() == OperationDetail.Classification.SENSITIVE;

        if (isSensitive) {
            SecretVisibilityProfile visibility = AppSettings.getInstance().getSecretVisibilityProfile();
            if (visibility == SecretVisibilityProfile.REDACTED) {
                return "[REDACTED]";
            } else if (visibility == SecretVisibilityProfile.MASKED) {
                if (val.length() <= 8) return "********";
                return val.substring(0, 4) + "...[MASKED]..." + val.substring(val.length() - 4);
            }
        }

        if (truncate && val.length() > 50) {
            return val.substring(0, 47) + "...";
        }
        return val;
    }

    private String projectAnnotation(String annotation, SecretVisibilityProfile visibility, boolean sensitive) {
        if (annotation == null || annotation.isBlank() || !sensitive || visibility == SecretVisibilityProfile.FULL_LAB) {
            return annotation == null ? "" : annotation;
        }
        return visibility == SecretVisibilityProfile.REDACTED ? "[REDACTED]" : "[MASKED]";
    }

    @FXML
    private void handleTogglePin() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null) {
            manager.togglePin(entry.getId());
            refresh();
        }
    }

    @FXML
    private void handleEditTagsNote() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null) {
            Window owner = shelfTable.getScene() == null ? null : shelfTable.getScene().getWindow();
            Optional<EditNoteTagsDialog.Result> result = EditNoteTagsDialog.show(owner, entry);
            result.ifPresent(res -> {
                manager.updateTagsAndNote(entry.getId(), res.tags, res.note);
                refresh();
            });
        }
    }

    @FXML
    private void handleCompare() {
        ObservableList<ClipboardEntry> selected = shelfTable.getSelectionModel().getSelectedItems();
        if (selected.size() != 2) return;
        if (selected.stream().anyMatch(ClipboardEntry::isSessionOnlyPrivateKey)) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/compare_results.fxml"));
            Parent root = loader.load();
            CompareResultsController controller = loader.getController();
            controller.setEntries(selected.get(0), selected.get(1));

            Stage stage = new Stage();
            stage.setTitle(t("module.compare.title"));
            stage.initModality(Modality.WINDOW_MODAL);
            Window owner = shelfTable.getScene() == null ? null : shelfTable.getScene().getWindow();
            if (owner != null) stage.initOwner(owner);

            stage.setScene(new Scene(root, 750, 550));
            stage.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, t("module.compare.openError", e.getMessage()), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void handleClearShelf() {
        manager.clear();
        refresh();
    }

    @FXML
    private void handleOpenClipboardWindow() {
        javafx.stage.Window owner = shelfTable.getScene() == null ? null : shelfTable.getScene().getWindow();
        new ClipboardShelfWindow(manager).show(owner);
    }

    @FXML
    private void handleCopy() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null && !entry.isSessionOnlyPrivateKey()) {
            String val = getMaskedValue(entry, false);
            if (!val.equals("[REDACTED]")) {
                ClipboardContent content = new ClipboardContent();
                content.putString(val);
                Clipboard.getSystemClipboard().setContent(content);
            }
        }
    }

    @FXML
    private void handleOpenExpanded() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null && !entry.isSessionOnlyPrivateKey() && mainController != null) {
            String val = getMaskedValue(entry, false);
            if (!val.equals("[REDACTED]")) {
                ExpandedTextViewer viewer = new ExpandedTextViewer();
                viewer.show(null, entry.getLabel(), val);
            }
        }
    }

    @FXML
    private void handleRename() {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry != null) {
            TextInputDialog dialog = new TextInputDialog(entry.getLabel());
            dialog.setTitle(t("module.shelf.renameTitle"));
            dialog.setHeaderText(t("module.shelf.renamePrompt"));
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(newLabel -> {
                manager.renameEntry(entry.getId(), newLabel);
                refresh();
            });
        }
    }

    @FXML
    private void handleDelete() {
        List<ClipboardEntry> selected = new ArrayList<>(shelfTable.getSelectionModel().getSelectedItems());
        if (!selected.isEmpty()) {
            for (ClipboardEntry e : selected) {
                manager.removeEntry(e.getId());
            }
            refresh();
        }
    }

    private void populateUseInMenu() {
        populateUseInMenu(null);
    }

    private void populateUseInMenu(ClipboardEntry selected) {
        useInMenu.getItems().clear();

        if (selected != null && selected.isSessionOnlyPrivateKey()) {
            if (AppSettings.getInstance().getSecretVisibilityProfile() != SecretVisibilityProfile.FULL_LAB) return;
            MenuItem workbench = new MenuItem("Use in Key & Certificate Workbench");
            workbench.setOnAction(e -> useInTarget("KEY_CERTIFICATE_WORKBENCH", "KEY_CERTIFICATE_WORKBENCH"));
            useInMenu.getItems().add(workbench);
            return;
        }
        if (selected != null && !selected.isReusable()) return;
        boolean structured = selected != null && selected.getShelfPackage() != null;

        if (!structured) {
            MenuItem manualConv = new MenuItem(t("module.shelf.manualInput"));
            manualConv.setOnAction(e -> useInTarget("op_gen_manual", "MANUAL_CONVERSION"));
            useInMenu.getItems().add(manualConv);

            MenuItem hashInput = new MenuItem(t("module.shelf.hashInput"));
            hashInput.setOnAction(e -> useInTarget("op_gen_hash", "HASHING"));
            useInMenu.getItems().add(hashInput);

            MenuItem xml = new MenuItem(t("module.shelf.xmlInput"));
            xml.setOnAction(e -> useInTarget("XML Security", "XML_SECURITY"));
            MenuItem wss = new MenuItem(t("module.shelf.wssInput"));
            wss.setOnAction(e -> useInTarget("WSS Security", "WSS_SECURITY"));
            MenuItem payments = new MenuItem(t("module.shelf.paymentsInput"));
            payments.setOnAction(e -> useInTarget("Payments", "PAYMENTS"));
            MenuItem tr31 = new MenuItem(t("module.shelf.tr31Input"));
            tr31.setOnAction(e -> useInTarget("TR-31 Key Blocks", "TR31"));
            MenuItem josePayload = new MenuItem("JOSE Payload (JWT)");
            josePayload.setOnAction(e -> useInTarget("op_jose_jwt", "JOSE_JWT"));
            useInMenu.getItems().addAll(xml, wss, payments, tr31, josePayload);
        }

        MenuItem symCipher = new MenuItem(t("module.shelf.cipherInput"));
        symCipher.setOnAction(e -> useInTarget("op_sym_ciphers", "SYMMETRIC_CIPHER"));
        useInMenu.getItems().add(symCipher);
    }

    static boolean supportsTarget(ClipboardEntry.Format format, String targetType) {
        if (format == null || targetType == null) return false;
        return switch (targetType) {
            case "MANUAL_CONVERSION", "SYMMETRIC_CIPHER", "HASHING" ->
                    format == ClipboardEntry.Format.TEXT || format == ClipboardEntry.Format.HEX
                            || format == ClipboardEntry.Format.BASE64 || format == ClipboardEntry.Format.BASE64URL;
            case "JOSE_JWT" -> format == ClipboardEntry.Format.TEXT || format == ClipboardEntry.Format.JSON;
            case "XML_SECURITY", "WSS_SECURITY", "TR31" -> format == ClipboardEntry.Format.TEXT;
            case "PAYMENTS" -> format == ClipboardEntry.Format.HEX;
            default -> false;
        };
    }

    private void useInTarget(String operationId, String targetType) {
        ClipboardEntry entry = shelfTable.getSelectionModel().getSelectedItem();
        if (entry == null || navigator == null || mainController == null) return;

        if (entry.isSessionOnlyPrivateKey()) {
            if (!"KEY_CERTIFICATE_WORKBENCH".equals(targetType)
                    || AppSettings.getInstance().getSecretVisibilityProfile() != SecretVisibilityProfile.FULL_LAB) {
                navigator.updateStatus("Action blocked: session-only private keys require FULL_LAB.");
                return;
            }
            mainController.loadSessionOnlyPrivateKey(entry);
            navigator.updateStatus("Loaded session-only private key into Key & Certificate Workbench");
            return;
        }

        if (!entry.isReusable()) {
            String message = "This Shelf result is not reusable: " + entry.getNonReusableReason();
            if (navigator != null) navigator.updateStatus(message);
            return;
        }
        if (entry.getShelfPackage() != null && !entry.getShelfPackage().getCompatibleTargets().contains(targetType)) {
            String message = "This structured Shelf package is not compatible with " + targetType;
            if (navigator != null) navigator.updateStatus(message);
            return;
        }

        ClipboardEntry.Format fmt = entry.getFormat();
        if (!supportsTarget(fmt, targetType)) {
            String message = t("module.shelf.incompatible", fmt.name(), targetType);
            Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            alert.setTitle(t("module.shelf.incompatibleTitle"));
            alert.setHeaderText(t("module.shelf.incompatibleHeader"));
            alert.showAndWait();
            if (navigator != null) navigator.updateStatus(message);
            return;
        }

        String val = getMaskedValue(entry, false);
        if (val.equals("[REDACTED]") || val.contains("[MASKED]")) return;

        navigator.navigateTo(operationId);
        mainController.fillClipboardTarget(targetType, val, entry.getFormat(), entry.getShelfPackage());
        navigator.updateStatus(t("module.shelf.injected", entry.getFormat().name()));
    }
}
