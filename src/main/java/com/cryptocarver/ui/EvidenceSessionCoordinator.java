package com.cryptocarver.ui;

import com.cryptocarver.model.BuildInfo;
import com.cryptocarver.model.EvidencePackage;
import com.cryptocarver.model.EvidencePackageCodec;
import com.cryptocarver.model.ExerciseSession;
import com.cryptocarver.model.OperationSessionLog;
import com.cryptocarver.model.SessionOperationStep;
import com.cryptocarver.model.SessionTrailState;
import com.cryptocarver.service.I18nService;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Locale;

/** Owns the exercise session: while one is active, every published result joins the trail. */
public final class EvidenceSessionCoordinator {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SessionTrailState state;
    private final SessionTrailCoordinator trail;
    private final MenuItem exportItem;
    private final Label indicator;
    private final DialogService dialogs;
    private final Supplier<Node> ownerNode;
    private final Consumer<String> status;
    private final BiConsumer<String, String> warning;
    private final I18nService i18n;
    private final EvidencePackageCodec codec = new EvidencePackageCodec();
    private ExerciseSession session;
    private OperationSessionLog exerciseLog = new OperationSessionLog();

    public EvidenceSessionCoordinator(SessionTrailState state, SessionTrailCoordinator trail, MenuItem exportItem,
            Label indicator, DialogService dialogs, Supplier<Node> ownerNode, Consumer<String> status,
            BiConsumer<String, String> warning, I18nService i18n) {
        this.state=state; this.trail=trail; this.exportItem=exportItem; this.indicator=indicator; this.dialogs=dialogs;
        this.ownerNode=ownerNode; this.status=status; this.warning=warning; this.i18n=i18n;
        refresh();
    }

    public void handleStart() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("evidence.startTitle"));
        ButtonType start = new ButtonType(i18n.text("evidence.start"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(start, ButtonType.CANCEL);
        TextField student = new TextField(session == null ? "" : session.student());
        TextField exercise = new TextField();
        exercise.setPromptText(i18n.text("evidence.exercisePrompt"));
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10);
        form.add(new Label(i18n.text("evidence.student")), 0, 0); form.add(student, 1, 0);
        form.add(new Label(i18n.text("evidence.exercise")), 0, 1); form.add(exercise, 1, 1);
        GridPane.setHgrow(student, Priority.ALWAYS); GridPane.setHgrow(exercise, Priority.ALWAYS);
        dialog.getDialogPane().setContent(form); dialog.getDialogPane().setMinWidth(480);
        dialog.getDialogPane().lookupButton(start).disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> student.getText().isBlank() || exercise.getText().isBlank(), student.textProperty(), exercise.textProperty()));
        if (dialog.showAndWait().filter(start::equals).isEmpty()) return;
        if ((state.size() > 0 || !exerciseLog.isEmpty()) && dialogs.show(Alert.AlertType.CONFIRMATION, windowOf(ownerNode.get()), i18n.text("evidence.startTitle"), i18n.text("evidence.startTitle"),
                new Label(i18n.text("evidence.replaceTrail")), ButtonType.CANCEL, ButtonType.OK).filter(ButtonType.OK::equals).isEmpty()) return;
        start(student.getText(), exercise.getText());
    }

    public void start(String student, String exercise) {
        session = ExerciseSession.start(student, exercise);
        exerciseLog = new OperationSessionLog();
        state.clear();
        trail.refresh();
        refresh();
        status.accept(i18n.text("evidence.started", session.exercise()));
    }

    /** Runs right after a result is published; does nothing outside an exercise session. */
    public void recordPublishedResult() {
        if (session == null) return;
        SessionOperationStep captured = trail.recordCurrentResult();
        if (captured != null) exerciseLog.append(captured);
    }

    public void handleExport() {
        if (session == null) { warning.accept(i18n.text("evidence.exportTitle"), i18n.text("evidence.noSession")); return; }
        if (exerciseLog.isEmpty()) { warning.accept(i18n.text("evidence.exportTitle"), i18n.text("evidence.nothingToExport")); return; }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("evidence.exportTitle"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Label summary = new Label(i18n.text("evidence.exportSummary", session.exercise(), session.student(), exerciseLog.size()));
        summary.setWrapText(true);
        CheckBox clearText = new CheckBox(i18n.text("evidence.includeClearText")); clearText.setWrapText(true);
        Label clearTextWarning = new Label(i18n.text("evidence.clearTextWarning"));
        clearTextWarning.setWrapText(true); clearTextWarning.getStyleClass().add("helper-text");
        dialog.getDialogPane().setContent(new VBox(10, summary, clearText, clearTextWarning));
        dialog.getDialogPane().setMinWidth(480);
        if (dialog.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        FileChooser chooser = LocalizedDialogSupport.fileChooser("evidence.exportTitle", "evidence.files",
                "CryptoCarver evidence", "*" + EvidencePackageCodec.EXTENSION);
        String suggestedName = session.suggestedFileName();
        // Cocoa appends the selected custom extension even when it is already in the initial name.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            suggestedName = suggestedName.substring(0, suggestedName.length() - EvidencePackageCodec.EXTENSION.length());
        }
        chooser.setInitialFileName(suggestedName);
        File file = chooser.showSaveDialog(windowOf(ownerNode.get())); if (file == null) return;
        try { export(file.toPath(), clearText.isSelected()); status.accept(i18n.text("evidence.exported", file.getName())); }
        catch (IOException error) { warning.accept(i18n.text("evidence.exportTitle"), error.getMessage()); }
    }

    public void export(Path target, boolean clearTextValues) throws IOException {
        if (session == null) throw new IllegalStateException("No exercise session is active");
        codec.write(EvidencePackage.create(session, exerciseLog, LocalDateTime.now().format(TIMESTAMP_FORMAT),
                BuildInfo.version(), clearTextValues), target);
    }

    private void refresh() {
        if (exportItem != null) exportItem.setDisable(session == null);
        if (indicator != null) {
            indicator.setText(session == null ? "" : session.exercise() + " · " + session.student());
            indicator.setVisible(session != null); indicator.setManaged(session != null);
        }
    }

    private static javafx.stage.Window windowOf(Node node) { return node == null || node.getScene() == null ? null : node.getScene().getWindow(); }
}
