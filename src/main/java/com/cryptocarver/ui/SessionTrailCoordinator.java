package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SessionOperationStep;
import com.cryptocarver.model.SessionTrailState;
import com.cryptocarver.model.ScreenConfiguration;
import com.cryptocarver.service.I18nService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Owns session trail controls and user flows while the model owns its state. */
public final class SessionTrailCoordinator {
    private final SessionTrailState state;
    private final Label countLabel, positionLabel;
    private final Button exportButton, previousButton, nextButton, openButton;
    private final javafx.scene.layout.HBox navigation;
    private final DialogService dialogs;
    private final ExpandedTextViewer stepViewer;
    private final Supplier<Node> ownerNode;
    private final Supplier<OperationResult> currentResult;
    private final Supplier<ScreenConfiguration> activeScreenConfiguration;
    private final Supplier<Map<String, Object>> fallbackUiState;
    private final Consumer<SessionOperationStep> presentSavedStep;
    private final Consumer<OperationResult> presentResult;
    private final Runnable clearInspector, revealInspector;
    private final Consumer<String> status;
    private final BiConsumer<String, String> warning;
    private final I18nService i18n;

    public SessionTrailCoordinator(SessionTrailState state, Label countLabel, Label positionLabel,
            Button exportButton, Button previousButton, Button nextButton, Button openButton,
            javafx.scene.layout.HBox navigation, DialogService dialogs, ExpandedTextViewer stepViewer,
            Supplier<Node> ownerNode, Supplier<OperationResult> currentResult,
            Supplier<ScreenConfiguration> activeScreenConfiguration, Supplier<Map<String, Object>> fallbackUiState,
            Consumer<SessionOperationStep> presentSavedStep, Consumer<OperationResult> presentResult,
            Runnable clearInspector, Runnable revealInspector,
            Consumer<String> status, BiConsumer<String, String> warning, I18nService i18n) {
        this.state=state; this.countLabel=countLabel; this.positionLabel=positionLabel;
        this.exportButton=exportButton; this.previousButton=previousButton; this.nextButton=nextButton;
        this.openButton=openButton; this.navigation=navigation; this.dialogs=dialogs; this.stepViewer=stepViewer;
        this.ownerNode=ownerNode; this.currentResult=currentResult;
        this.activeScreenConfiguration=activeScreenConfiguration; this.fallbackUiState=fallbackUiState;
        this.presentSavedStep=presentSavedStep; this.presentResult=presentResult; this.clearInspector=clearInspector;
        this.revealInspector=revealInspector;
        this.status=status; this.warning=warning; this.i18n=i18n;
    }

    public void saveCurrentResultAsSessionStep() {
        OperationResult result = currentResult.get();
        if (result == null) { warning.accept(i18n.text("sessionTrail.title"), i18n.text("sessionTrail.noResult")); return; }
        boolean fullLab = !LabPrompt.SESSION_STEP.shouldShow();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("sessionTrail.dialogTitle"));
        if (!fullLab) dialog.setHeaderText(i18n.text("sessionTrail.dialogHeader"));
        ButtonType save = new ButtonType(i18n.text("sessionTrail.saveStep"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField title = new TextField(result.getOperation()); title.setPromptText(i18n.text("sessionTrail.titlePrompt"));
        TextField tags = new TextField(); tags.setPromptText(i18n.text("sessionTrail.tagsPrompt"));
        Label warning = new Label(i18n.text("sessionTrail.unsafeWarning"));
        warning.setWrapText(true); warning.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        CheckBox confirmation = new CheckBox(i18n.text("sessionTrail.unsafeConfirm")); confirmation.setWrapText(true);
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10);
        form.add(new Label(i18n.text("sessionTrail.stepTitle")), 0, 0); form.add(title, 1, 0);
        form.add(new Label(i18n.text("sessionTrail.tags")), 0, 1); form.add(tags, 1, 1);
        if (!fullLab) { form.add(warning, 0, 2, 2, 1); form.add(confirmation, 0, 3, 2, 1); }
        GridPane.setHgrow(title, Priority.ALWAYS); GridPane.setHgrow(tags, Priority.ALWAYS);
        dialog.getDialogPane().setContent(form); dialog.getDialogPane().setMinWidth(480);
        Node saveNode = dialog.getDialogPane().lookupButton(save);
        if (fullLab) saveNode.disableProperty().bind(title.textProperty().isEmpty());
        else saveNode.disableProperty().bind(javafx.beans.binding.Bindings.or(title.textProperty().isEmpty(), confirmation.selectedProperty().not()));
        Platform.runLater(title::requestFocus);
        Optional<ButtonType> selected = dialog.showAndWait();
        if (selected.isPresent() && selected.get() == save) saveCurrentResultAsSessionStep(title.getText(), tags.getText());
    }

    public SessionOperationStep saveCurrentResultAsSessionStep(String title, String commaSeparatedTags) {
        OperationResult result = currentResult.get();
        if (result == null) throw new IllegalStateException("No completed operation result is available");
        List<String> tags = commaSeparatedTags == null || commaSeparatedTags.isBlank() ? List.of()
                : Arrays.stream(commaSeparatedTags.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        SessionOperationStep step = state.add(result, title, tags, captureClearTextTrailParameters());
        refresh(); presentSavedStep.accept(step); revealInspector.run();
        status.accept(i18n.text("sessionTrail.saved", step.getTitle()));
        return step;
    }

    public void refresh() {
        int count = state.size();
        if (countLabel != null) { countLabel.setText(i18n.text("sessionTrail.compactCount", count)); countLabel.setAccessibleText(i18n.text("sessionTrail.count", count)); }
        if (exportButton != null) exportButton.setDisable(count == 0);
        refreshNavigation();
    }

    public void refreshNavigation() {
        int count = state.size(); int selected = state.selectedIndex();
        if (selected >= count) { state.showCurrentResult(); selected = state.selectedIndex(); }
        if (navigation != null) { navigation.setVisible(count > 0); navigation.setManaged(count > 0); }
        if (previousButton != null) previousButton.setDisable(count == 0 || selected == 0);
        if (nextButton != null) nextButton.setDisable(count == 0 || selected < 0 || (selected == count - 1 && !state.hasUnsavedResult()));
        if (openButton != null) openButton.setDisable(selected < 0);
        if (positionLabel != null) {
            if (selected >= 0) {
                SessionOperationStep step = state.steps().get(selected);
                positionLabel.setText(i18n.text("sessionTrail.position", selected + 1, count, step.getTitle()));
                positionLabel.setTooltip(new Tooltip(step.getTitle()));
            } else {
                positionLabel.setText(i18n.text(currentResult.get() == null ? "sessionTrail.selectStep" : "sessionTrail.currentResult"));
                positionLabel.setTooltip(null);
            }
        }
    }

    public void showSessionStep(int index) {
        if (!state.select(index)) return;
        SessionOperationStep step = state.steps().get(index);
        presentSavedStep.accept(step); refreshNavigation();
    }
    public void previous() { int index = state.previous(); if (index >= 0) showSessionStep(index); }
    public void next() {
        int prior = state.selectedIndex(); int index = state.next();
        if (index >= 0) showSessionStep(index);
        else if (prior >= 0 && state.hasUnsavedResult() && currentResult.get() != null) { presentResult.accept(currentResult.get()); refreshNavigation(); }
    }
    public void openSelected() {
        int index = state.selectedIndex(); if (index < 0 || index >= state.size()) return;
        SessionOperationStep step = state.steps().get(index);
        stepViewer.show(windowOf(ownerNode.get()), i18n.text("sessionTrail.viewStepTitle", step.getTitle()),
                SessionTrailViewFormatter.step(step, AppSettings.getInstance().getSecretVisibilityProfile(), i18n));
    }
    public void handleExport() {
        if (state.log().isEmpty()) { warning.accept(i18n.text("sessionTrail.title"), i18n.text("sessionTrail.nothingToExport")); return; }
        FileChooser chooser = LocalizedDialogSupport.fileChooser("sessionTrail.exportTitle", "sessionTrail.textFiles", "Text files", "*.txt");
        chooser.setInitialFileName("cryptocarver-session-trail-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        File file = chooser.showSaveDialog(windowOf(ownerNode.get())); if (file == null) return;
        try { export(file.toPath()); status.accept(i18n.text("sessionTrail.exported", file.getName())); }
        catch (IOException error) { warning.accept(i18n.text("sessionTrail.exportTitle"), error.getMessage()); }
    }
    public void export(Path target) throws IOException {
        if (target == null) throw new IllegalArgumentException("Export target is required");
        Path parent = target.toAbsolutePath().getParent(); if (parent != null) Files.createDirectories(parent);
        Files.writeString(target, state.log().toText(), StandardCharsets.UTF_8);
    }
    public void clear() {
        if (state.log().isEmpty()) return;
        Node owner = ownerNode.get();
        if (dialogs.show(Alert.AlertType.CONFIRMATION, windowOf(owner), i18n.text("sessionTrail.title"), i18n.text("sessionTrail.clear"),
                new Label(i18n.text("sessionTrail.clearConfirm")), ButtonType.CANCEL, ButtonType.OK).filter(ButtonType.OK::equals).isPresent()) {
            state.clear(); if (currentResult.get() != null) presentResult.accept(currentResult.get()); else clearInspector.run();
            refresh(); status.accept(i18n.text("sessionTrail.cleared"));
        }
    }

    private Map<String, Object> captureClearTextTrailParameters() {
        try {
            return new java.util.LinkedHashMap<>(activeScreenConfiguration.get().toState());
        } catch (RuntimeException unsupportedRoute) {
            org.slf4j.LoggerFactory.getLogger(SessionTrailCoordinator.class)
                    .debug("Falling back to full UI-state capture for session trail", unsupportedRoute);
            return new java.util.LinkedHashMap<>(fallbackUiState.get());
        }
    }
    private static javafx.stage.Window windowOf(Node node) { return node == null || node.getScene() == null ? null : node.getScene().getWindow(); }
}
