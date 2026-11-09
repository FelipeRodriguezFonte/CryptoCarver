package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.EvidencePackage;
import com.cryptocarver.model.EvidencePackageCodec;
import com.cryptocarver.model.EvidencePackageCodec.Entry;
import com.cryptocarver.model.SessionOperationStep;
import com.cryptocarver.service.I18nService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Read-only review of handed-in evidence: nothing here touches the active workspace. */
public final class EvidenceReviewWindow {
    interface Source {
        List<Path> files(Path folder) throws IOException;
        Entry load(Path file);
    }

    /** Table rows retain only the header, not every student's payloads. */
    record ReviewEntry(Path file, String student, String exercise, String stepCount, String exportedAt,
                       boolean clearTextValues, EvidencePackage.Integrity integrity, String error) {
        static ReviewEntry from(Entry entry) {
            EvidencePackage evidence = entry.evidence();
            return evidence == null ? new ReviewEntry(entry.file(), "", "", "", "", false, null, entry.error())
                    : new ReviewEntry(entry.file(), evidence.getStudent(), evidence.getExercise(),
                    Integer.toString(evidence.getStepCount()), evidence.getExportedAt(),
                    evidence.isClearTextValues(), entry.integrity(), null);
        }
        boolean readable() { return integrity != null; }
    }

    private final I18nService i18n;
    private final BiConsumer<String, String> warning;
    private final Source source;
    private final TableView<ReviewEntry> entries = new TableView<>();
    private final ListView<SessionOperationStep> steps = new ListView<>();
    private final TextArea detail = new TextArea();
    private final ProgressBar progress = new ProgressBar();
    private final Label loading = new Label();
    private final Button cancel = new Button();
    private final HBox activity = new HBox(10, progress, loading, cancel);
    private ThreadPoolExecutor worker;
    private Task<List<ReviewEntry>> folderTask;
    private Task<Entry> detailTask;
    private Task<?> activeTask;
    private Entry selectedEvidence;
    private boolean refreshingRow;
    private Stage stage;

    public EvidenceReviewWindow(I18nService i18n, BiConsumer<String, String> warning) {
        this(i18n, warning, new Source() {
            private final EvidencePackageCodec codec = new EvidencePackageCodec();
            @Override public List<Path> files(Path folder) throws IOException { return codec.listFiles(folder); }
            @Override public Entry load(Path file) { return codec.load(file); }
        });
    }

    EvidenceReviewWindow(I18nService i18n, BiConsumer<String, String> warning, Source source) {
        this.i18n = i18n; this.warning = warning; this.source = source;
    }

    public void show(Window owner) {
        if (stage == null) stage = create(owner);
        stage.show(); stage.toFront();
    }

    public void close() {
        if (stage != null) stage.close();
        stopWorker();
    }

    private Stage create(Window owner) {
        entries.getColumns().setAll(List.of(
                column("evidence.student", 180, ReviewEntry::student),
                column("evidence.exercise", 140, ReviewEntry::exercise),
                column("evidence.steps", 70, ReviewEntry::stepCount),
                column("evidence.exportedAt", 160, ReviewEntry::exportedAt),
                column("evidence.clearText", 110, entry -> !entry.readable() ? ""
                        : i18n.text(entry.clearTextValues() ? "evidence.yes" : "evidence.no")),
                column("evidence.integrity", 170, this::integrity),
                column("evidence.file", 220, entry -> entry.file().getFileName().toString())));
        entries.setPlaceholder(new Label(i18n.text("evidence.emptyFolder")));
        entries.getSelectionModel().selectedItemProperty().addListener((observable, previous, entry) -> {
            if (!refreshingRow) loadEntry(entry);
        });

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
        cancel.setText(i18n.text("evidence.cancelLoading"));
        cancel.setOnAction(event -> cancelLoading());
        activity.setAlignment(Pos.CENTER_LEFT);
        activity.setVisible(false); activity.setManaged(false);

        VBox root = new VBox(10, toolbar, activity, content);
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
        created.setOnHidden(event -> stopWorker());
        return created;
    }

    private TableColumn<ReviewEntry, String> column(String titleKey, double width, Function<ReviewEntry, String> value) {
        TableColumn<ReviewEntry, String> column = new TableColumn<>(i18n.text(titleKey));
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
        cancelLoading();
        entries.getItems().clear();
        clearDetail();
        Task<List<ReviewEntry>> task = new Task<>() {
            @Override protected List<ReviewEntry> call() throws IOException {
                List<Path> files = source.files(folder);
                List<ReviewEntry> rows = new ArrayList<>();
                for (int index = 0; index < files.size(); index++) {
                    if (isCancelled()) return List.of();
                    rows.add(ReviewEntry.from(source.load(files.get(index))));
                    updateProgress(index + 1, files.size());
                    updateMessage(i18n.text("evidence.loadingProgress", index + 1, files.size()));
                }
                return rows;
            }
        };
        folderTask = task;
        task.setOnSucceeded(event -> {
            if (folderTask != task) return;
            folderTask = null;
            finishLoading();
            entries.getItems().setAll(task.getValue());
        });
        task.setOnFailed(event -> {
            if (folderTask != task) return;
            folderTask = null;
            finishLoading();
            warning.accept(i18n.text("evidence.reviewTitle"), task.getException().getMessage());
        });
        startLoading(task, "evidence.loadingFolder");
    }

    private void loadEntry(ReviewEntry row) {
        if (detailTask != null) detailTask.cancel(true);
        detailTask = null;
        clearDetail();
        if (row == null) { if (folderTask == null) finishLoading(); return; }
        // Re-read the selected file: it may have changed since the folder scan.
        Task<Entry> task = new Task<>() {
            @Override protected Entry call() { return source.load(row.file()); }
        };
        detailTask = task;
        task.setOnSucceeded(event -> {
            if (detailTask != task) return;
            detailTask = null;
            finishLoading();
            Entry entry = task.getValue();
            int index = entries.getItems().indexOf(row);
            if (index >= 0) {
                refreshingRow = true;
                try { entries.getItems().set(index, ReviewEntry.from(entry)); }
                finally { refreshingRow = false; }
            }
            selectedEvidence = entry;
            if (!entry.readable()) { detail.setText(entry.error()); return; }
            steps.getItems().setAll(entry.evidence().getSteps());
            EvidencePackage evidence = entry.evidence();
            detail.setText(i18n.text("evidence.summary", evidence.getStudent(), evidence.getExercise(),
                    evidence.getStartedAt(), evidence.getExportedAt(), evidence.getAppVersion()));
        });
        task.setOnFailed(event -> {
            if (detailTask != task) return;
            detailTask = null;
            finishLoading();
            detail.setText(task.getException().getMessage());
        });
        startLoading(task, "evidence.loadingEntry");
    }

    private void startLoading(Task<?> task, String messageKey) {
        activeTask = task;
        progress.progressProperty().unbind();
        loading.textProperty().unbind();
        progress.progressProperty().bind(task.progressProperty());
        loading.setText(i18n.text(messageKey));
        task.messageProperty().addListener((observable, previous, message) -> {
            if (activeTask == task && !message.isBlank() && !task.isCancelled()) loading.setText(message);
        });
        activity.setVisible(true); activity.setManaged(true);
        if (worker == null || worker.isShutdown()) worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "evidence-review");
                    thread.setDaemon(true);
                    return thread;
                });
        worker.purge();
        worker.execute(task);
    }

    private void finishLoading() {
        activeTask = null;
        progress.progressProperty().unbind();
        activity.setVisible(false); activity.setManaged(false);
    }

    void cancelLoading() {
        if (folderTask != null) folderTask.cancel(true);
        if (detailTask != null) detailTask.cancel(true);
        folderTask = null; detailTask = null;
        finishLoading();
        if (worker != null) worker.purge();
    }

    private void stopWorker() {
        cancelLoading();
        entries.getSelectionModel().clearSelection();
        clearDetail();
        if (worker != null) worker.shutdownNow();
    }

    private void clearDetail() {
        selectedEvidence = null;
        steps.getItems().clear();
        detail.clear();
    }

    private String integrity(ReviewEntry entry) {
        if (!entry.readable()) return i18n.text("evidence.unreadable");
        return i18n.text(switch (entry.integrity()) {
            case VALID -> "evidence.integrityValid";
            case CHAIN_BROKEN -> "evidence.integrityChainBroken";
            case SEAL_MISMATCH -> "evidence.integritySealMismatch";
        });
    }

    private void showStep(SessionOperationStep step) {
        if (step == null || selectedEvidence == null || !selectedEvidence.readable()) return;
        detail.setText(SessionTrailViewFormatter.step(step, AppSettings.getInstance().getSecretVisibilityProfile(), i18n,
                !selectedEvidence.evidence().isClearTextValues()));
        detail.positionCaret(0);
    }
}
