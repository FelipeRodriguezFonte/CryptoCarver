package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.EvidencePackage;
import com.cryptocarver.model.EvidencePackageCodec;
import com.cryptocarver.model.EvidencePackageCodec.Entry;
import com.cryptocarver.model.SessionOperationStep;
import com.cryptocarver.service.I18nService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Read-only review of handed-in evidence: nothing here touches the active workspace. */
public final class EvidenceReviewWindow {
    private final I18nService i18n;
    private final BiConsumer<String, String> warning;
    private final EvidencePackageCodec codec = new EvidencePackageCodec();
    private final TableView<Entry> entries = new TableView<>();
    private final ListView<SessionOperationStep> steps = new ListView<>();
    private final TextArea detail = new TextArea();
    private Stage stage;

    public EvidenceReviewWindow(I18nService i18n, BiConsumer<String, String> warning) {
        this.i18n = i18n; this.warning = warning;
    }

    public void show(Window owner) {
        if (stage == null) stage = create(owner);
        stage.show(); stage.toFront();
    }

    private Stage create(Window owner) {
        entries.getColumns().setAll(List.of(
                column("evidence.student", 180, entry -> entry.readable() ? entry.evidence().getStudent() : ""),
                column("evidence.exercise", 140, entry -> entry.readable() ? entry.evidence().getExercise() : ""),
                column("evidence.steps", 70, entry -> entry.readable() ? Integer.toString(entry.evidence().getStepCount()) : ""),
                column("evidence.exportedAt", 160, entry -> entry.readable() ? entry.evidence().getExportedAt() : ""),
                column("evidence.clearText", 110, entry -> !entry.readable() ? ""
                        : i18n.text(entry.evidence().isClearTextValues() ? "evidence.yes" : "evidence.no")),
                column("evidence.integrity", 170, this::integrity),
                column("evidence.file", 220, entry -> entry.file().getFileName().toString())));
        entries.setPlaceholder(new Label(i18n.text("evidence.emptyFolder")));
        entries.getSelectionModel().selectedItemProperty().addListener((observable, previous, entry) -> showEntry(entry));

        steps.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(SessionOperationStep step, boolean empty) {
                super.updateItem(step, empty);
                setText(empty || step == null ? null : (getIndex() + 1) + ". " + step.getTimestamp() + " · " + step.getTitle());
            }
        });
        steps.getSelectionModel().selectedItemProperty().addListener((observable, previous, step) -> showStep(step));
        detail.setEditable(false); detail.setWrapText(true);

        SplitPane timeline = new SplitPane(steps, detail);
        timeline.setDividerPositions(0.4);
        SplitPane content = new SplitPane(entries, timeline);
        content.setOrientation(Orientation.VERTICAL);
        content.setDividerPositions(0.4);
        VBox.setVgrow(content, Priority.ALWAYS);

        Button open = new Button(i18n.text("evidence.openFolder"));
        open.setOnAction(event -> chooseFolder());
        Label notice = new Label(i18n.text("evidence.integrityNotice"));
        notice.setWrapText(true); notice.getStyleClass().add("helper-text");
        HBox.setHgrow(notice, Priority.ALWAYS);
        HBox toolbar = new HBox(10, open, notice);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, toolbar, content);
        root.setPadding(new Insets(12));
        Scene scene = new Scene(root, 1120, 760);
        Stage created = new Stage();
        if (owner != null) {
            created.initOwner(owner);
            if (owner.getScene() != null) scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        created.setTitle(i18n.text("evidence.reviewTitle"));
        created.setScene(scene);
        created.setMinWidth(760); created.setMinHeight(480);
        return created;
    }

    private TableColumn<Entry, String> column(String titleKey, double width, Function<Entry, String> value) {
        TableColumn<Entry, String> column = new TableColumn<>(i18n.text(titleKey));
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(i18n.text("evidence.openFolder"));
        File folder = chooser.showDialog(stage);
        if (folder != null) open(folder.toPath());
    }

    void open(Path folder) {
        try {
            entries.getItems().setAll(codec.loadFolder(folder));
            showEntry(null);
        } catch (IOException | RuntimeException error) {
            warning.accept(i18n.text("evidence.reviewTitle"), error.getMessage());
        }
    }

    private String integrity(Entry entry) {
        if (!entry.readable()) return i18n.text("evidence.unreadable");
        return i18n.text(switch (entry.integrity()) {
            case VALID -> "evidence.integrityValid";
            case CHAIN_BROKEN -> "evidence.integrityChainBroken";
            case SEAL_MISMATCH -> "evidence.integritySealMismatch";
        });
    }

    private void showEntry(Entry entry) {
        steps.getItems().clear();
        detail.clear();
        if (entry == null) return;
        if (!entry.readable()) { detail.setText(entry.error()); return; }
        steps.getItems().setAll(entry.evidence().getSteps());
        EvidencePackage evidence = entry.evidence();
        detail.setText(i18n.text("evidence.summary", evidence.getStudent(), evidence.getExercise(),
                evidence.getStartedAt(), evidence.getExportedAt(), evidence.getAppVersion()));
    }

    private void showStep(SessionOperationStep step) {
        Entry entry = entries.getSelectionModel().getSelectedItem();
        if (step == null || entry == null || !entry.readable()) return;
        detail.setText(SessionTrailViewFormatter.step(step, AppSettings.getInstance().getSecretVisibilityProfile(), i18n,
                !entry.evidence().isClearTextValues()));
        detail.positionCaret(0);
    }
}
