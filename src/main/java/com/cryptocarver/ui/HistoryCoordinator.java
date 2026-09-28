package com.cryptocarver.ui;

import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryCommandPolicy;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.model.SecretVisibilityProfile;
import com.cryptocarver.service.I18nService;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coordinates operation-history storage, presentation, navigation and export. */
public final class HistoryCoordinator {
    private static final String REDACTED = "[REDACTED_SECRET]";

    private final Supplier<HistoryManager> manager;
    private final Supplier<SidePanel> sidePanel;
    private final Supplier<HistoryController> historyView;
    private final Supplier<Map<String, Object>> recipe;
    private final Supplier<String> currentOperation;
    private final Supplier<String> inputFormat;
    private final Supplier<String> outputFormat;
    private final Consumer<String> navigateToModule;
    private final BiConsumer<Map<String, Object>, String> restoreOperationState;
    private final BiConsumer<String, List<OperationDetail>> showInspector;
    private final Function<List<OperationDetail>, List<OperationDetail>> visibleDetails;
    private final Consumer<String> status;
    private final Supplier<Window> owner;
    private final DialogService dialogs;
    private final I18nService i18n;

    public HistoryCoordinator(Supplier<HistoryManager> manager, Supplier<SidePanel> sidePanel,
                              Supplier<HistoryController> historyView,
                              Supplier<Map<String, Object>> recipe, Supplier<String> currentOperation,
                              Supplier<String> inputFormat, Supplier<String> outputFormat,
                              Consumer<String> navigateToModule,
                              BiConsumer<Map<String, Object>, String> restoreOperationState,
                              BiConsumer<String, List<OperationDetail>> showInspector,
                              Function<List<OperationDetail>, List<OperationDetail>> visibleDetails,
                              Consumer<String> status, Supplier<Window> owner,
                              DialogService dialogs, I18nService i18n) {
        this.manager = Objects.requireNonNull(manager);
        this.sidePanel = Objects.requireNonNull(sidePanel);
        this.historyView = Objects.requireNonNull(historyView);
        this.recipe = Objects.requireNonNull(recipe);
        this.currentOperation = Objects.requireNonNull(currentOperation);
        this.inputFormat = Objects.requireNonNull(inputFormat);
        this.outputFormat = Objects.requireNonNull(outputFormat);
        this.navigateToModule = Objects.requireNonNull(navigateToModule);
        this.restoreOperationState = Objects.requireNonNull(restoreOperationState);
        this.showInspector = Objects.requireNonNull(showInspector);
        this.visibleDetails = Objects.requireNonNull(visibleDetails);
        this.status = Objects.requireNonNull(status);
        this.owner = Objects.requireNonNull(owner);
        this.dialogs = Objects.requireNonNull(dialogs);
        this.i18n = Objects.requireNonNull(i18n);
    }

    public HistoryManager historyManager() { return manager.get(); }

    public void initialize() {
        SidePanel panel = sidePanel.get();
        if (panel != null) {
            panel.setHistoryManager(historyManager());
            panel.setOnHistoryItemSelected(this::showRecentHistoryCommand);
        }
        refresh();
    }

    public void refresh() {
        refreshNavigation();
        HistoryController view = historyView.get();
        if (view != null) view.refresh();
    }

    public void refreshNavigation() {
        SidePanel panel = sidePanel.get();
        if (panel != null) panel.updateContent(panel.getCurrentSection());
    }

    public void addToHistory(String operation, List<OperationDetail> details, String navigationCandidate) {
        HistoryCommand item = HistoryCommandPolicy.create(operation, details, recipe.get(),
                inputFormat.get(), outputFormat.get(), navigationCandidate,
                candidate -> OperationRegistry.getInstance().resolveNavigation(candidate).isPresent());
        historyManager().addHistoryItem(item);
        refresh();
    }

    public void restoreOperationState(Map<String, Object> state, String operation) {
        restoreOperationState.accept(state, operation);
    }

    public void showRecentHistoryCommand(HistoryCommand item) {
        if (item == null) return;
        navigateToModule.accept("Recent Operations");
        HistoryController view = historyView.get();
        if (view != null) view.selectHistoryCommand(item);
        showInspector.accept(item.getOperation(), visibleHistoryDetails(item));
        status.accept("Viewing historical execution: " + item.getOperation());
    }

    public void reopenHistoryOperation(HistoryCommand item) {
        if (item == null || item.getOperation() == null) return;
        restoreOperationState.accept(item.getParameters(), item.getNavigationOperation());
        showInspector.accept(item.getOperation(), visibleHistoryDetails(item));
        boolean redacted = item.getParameters() != null && item.getParameters().values().stream()
                .anyMatch(REDACTED::equals);
        status.accept("Reopened historical execution: " + item.getOperation()
                + (redacted ? ". Re-enter redacted sensitive values." : ""));
    }

    public List<OperationDetail> visibleHistoryDetails(HistoryCommand item) {
        List<OperationDetail> details = item.getStructuredDetails();
        if (details == null || details.isEmpty()) {
            if (item.getDetails() == null || item.getDetails().isBlank()) return List.of();
            details = List.of(OperationDetail.sensitiveDetail("Legacy details", item.getDetails()));
        }
        return visibleDetails.apply(details);
    }

    public void chooseAndExport() {
        HistoryManager history = historyManager();
        if (history.getHistoryItems().isEmpty()) {
            Alert alert = LocalizedDialogSupport.alert(Alert.AlertType.INFORMATION,
                    "dialog.exportHistory.title", "dialog.exportHistory.emptyHeader",
                    i18n.text("dialog.exportHistory.emptyContent"));
            alert.showAndWait();
            return;
        }
        FileChooser chooser = LocalizedDialogSupport.fileChooser(
                "dialog.exportHistory.title", "dialog.exportHistory.filter", "JSON Files", "*.json");
        chooser.setInitialFileName("cryptocarver-history-export.json");
        File file = chooser.showSaveDialog(owner.get());
        if (file == null) return;
        SecretVisibilityProfile visibility = com.cryptocarver.model.AppSettings.getInstance()
                .getSecretVisibilityProfile();
        try {
            exportTo(file.toPath(), visibility);
            dialogs.info(owner.get(), i18n.text("dialog.exportHistory.success"),
                    "History successfully exported using " + visibility + " policy to:\n" + file.getAbsolutePath());
        } catch (IOException error) {
            dialogs.error(owner.get(), i18n.text("dialog.exportHistory.failure"),
                    i18n.text("dialog.exportHistory.saveFailure") + "\n" + error.getMessage());
        }
    }

    public void exportTo(Path target, SecretVisibilityProfile visibility) throws IOException {
        Objects.requireNonNull(target, "target");
        String json = com.cryptocarver.utils.HistoryRecordExporter.toJson(
                historyManager().getHistoryItems(), visibility);
        Files.writeString(target, json, StandardCharsets.UTF_8);
    }
}
