package com.cryptocarver.ui;

import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.service.I18nService;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shell-facing history workflows, wired through lazy getters for live UI state. */
public final class ShellHistoryCoordinator {
    public record View(Supplier<HistoryManager> historyManager, Supplier<SidePanel> sidePanel,
                       Supplier<HistoryController> historyView, Supplier<Map<String, Object>> recipe,
                       Supplier<String> currentOperation, Supplier<String> inputFormat,
                       Supplier<String> outputFormat, Consumer<String> navigateToModule,
                       Consumer<String> selectOperation, Supplier<Object> recipeController,
                       BiConsumer<String, List<OperationDetail>> showInspector,
                       Function<List<OperationDetail>, List<OperationDetail>> visibleDetails,
                       Supplier<StatusReporter> statusReporter, Supplier<Window> owner,
                       DialogService dialogs, I18nService i18n,
                       Supplier<String> noActiveOperationStatus, Supplier<String> inputClearedStatus,
                       BooleanSupplier emvVisible, Supplier<Runnable> clearEmv,
                       BooleanSupplier cipherVisible, Supplier<Runnable> clearCipher,
                       BooleanSupplier authenticationVisible, Supplier<Runnable> clearAuthentication,
                       BooleanSupplier keysVisible, Supplier<Runnable> clearKeys,
                       BooleanSupplier genericVisible, Supplier<Runnable> clearGeneric,
                       BooleanSupplier processDesignerVisible, Supplier<Runnable> clearProcessDesigner,
                       BooleanSupplier certificatesVisible, Runnable clearPublishedResultSnapshot) {
        public View {
            Objects.requireNonNull(historyManager); Objects.requireNonNull(statusReporter);
            Objects.requireNonNull(currentOperation); Objects.requireNonNull(clearPublishedResultSnapshot);
        }
    }

    private final View view;
    private HistoryCoordinator history;

    public ShellHistoryCoordinator(View view) { this.view = Objects.requireNonNull(view); }

    public HistoryCoordinator historyCoordinator() {
        if (history == null) {
            history = new HistoryCoordinator(view.historyManager(), view.sidePanel(), view.historyView(),
                    view.recipe(), view.currentOperation(), view.inputFormat(), view.outputFormat(),
                    view.navigateToModule(), this::restoreHistoryRecipe, view.showInspector(),
                    view.visibleDetails(), message -> view.statusReporter().get().updateStatus(message),
                    view.owner(), view.dialogs(), view.i18n());
        }
        return history;
    }

    public HistoryManager historyManager() { return historyCoordinator().historyManager(); }
    public void initialize() { historyCoordinator().initialize(); }
    public void refresh() { historyCoordinator().refresh(); }
    public void refreshNavigation() { historyCoordinator().refreshNavigation(); }
    public void addToHistory(String operation, List<OperationDetail> details, String navigation) {
        historyCoordinator().addToHistory(operation, details, navigation);
    }
    public void restoreOperationState(Map<String, Object> state, String operation) {
        historyCoordinator().restoreOperationState(state, operation);
    }
    public void showRecentHistoryCommand(HistoryCommand item) { historyCoordinator().showRecentHistoryCommand(item); }
    public void reopenHistoryOperation(HistoryCommand item) { historyCoordinator().reopenHistoryOperation(item); }
    public List<OperationDetail> visibleHistoryDetails(HistoryCommand item) {
        return historyCoordinator().visibleHistoryDetails(item);
    }
    public void chooseAndExport() { historyCoordinator().chooseAndExport(); }
    public void exportTo(Path target, com.cryptocarver.model.SecretVisibilityProfile visibility) throws IOException {
        historyCoordinator().exportTo(target, visibility);
    }

    private void restoreHistoryRecipe(Map<String, Object> state, String operation) {
        view.selectOperation().accept(operation);
        List<javafx.scene.Node> redacted = UiStateSnapshot.restoreHistoryRecipe(view.recipeController().get(), state);
        if (redacted != null && !redacted.isEmpty()) {
            view.statusReporter().get().updateStatus("Restored configuration for: " + operation
                    + ". Re-enter redacted sensitive values.");
            javafx.application.Platform.runLater(() -> redacted.stream().filter(ShellHistoryCoordinator::isShowing)
                    .findFirst().ifPresent(javafx.scene.Node::requestFocus));
        } else {
            view.statusReporter().get().updateStatus("Restored state for: " + operation);
        }
    }

    private static boolean isShowing(javafx.scene.Node node) {
        if (node.getScene() == null) return false;
        for (javafx.scene.Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }

    public void clearInput() {
        StatusReporter reporter = view.statusReporter().get();
        if (view.currentOperation().get() == null) {
            reporter.updateStatus(view.noActiveOperationStatus().get());
            return;
        }
        if (view.emvVisible().getAsBoolean()) view.clearEmv().get().run();
        else if (view.cipherVisible().getAsBoolean()) view.clearCipher().get().run();
        else if (view.authenticationVisible().getAsBoolean()) view.clearAuthentication().get().run();
        else if (view.keysVisible().getAsBoolean()) view.clearKeys().get().run();
        else if (view.genericVisible().getAsBoolean()) view.clearGeneric().get().run();
        else if (view.processDesignerVisible().getAsBoolean()) view.clearProcessDesigner().get().run();
        else if (view.certificatesVisible().getAsBoolean()) { /* Certificate clearing is not implemented. */ }
        view.clearPublishedResultSnapshot().run();
        reporter.updateStatus(view.inputClearedStatus().get());
    }
}
