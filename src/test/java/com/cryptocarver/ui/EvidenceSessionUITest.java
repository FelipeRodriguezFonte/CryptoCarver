package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.model.EvidencePackage;
import com.cryptocarver.model.EvidencePackageCodec;
import com.cryptocarver.model.ExerciseSession;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.OperationSessionLog;
import com.cryptocarver.model.SessionOperationStep;
import com.cryptocarver.service.I18nService;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.value.ChangeListener;
import javafx.scene.layout.HBox;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Production shell: a session records published results and its export can be reviewed. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class EvidenceSessionUITest {
    @BeforeAll static void start() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { Platform.setImplicitExit(false); }
    }

    @Test void resultsAreRecordedOnlyDuringAnExerciseSession(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("LAB-1-Ana.ccevidence");
        UiTestLifecycleExtension.onFx(() -> {
            try {
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController shell = loader.getController();
                OperationResult result = OperationResult.forOperation("SHA-256").input(new byte[] {1})
                        .output(new byte[] {2}, OperationDetail.Classification.PUBLIC).build();
                MenuItem export = field(shell, "exportEvidenceMenuItem");
                Label indicator = (Label) root.lookup("#exerciseSessionLabel");
                EvidenceSessionCoordinator evidence = coordinator(shell);
                assertTrue(export.isDisable());
                shell.publish(result);
                assertEquals(0, ((com.cryptocarver.model.SessionTrailState) field(shell, "sessionTrailState")).size());
                evidence.start("Ana", "LAB-1");
                assertFalse(export.isDisable());
                assertEquals("LAB-1 · Ana", indicator.getText());
                shell.publish(result);
                shell.publish(result);
                evidence.export(file, false);
                shell.shutdown();
            } catch (Exception error) { throw new AssertionError(error); }
        });
        List<EvidencePackageCodec.Entry> entries = new EvidencePackageCodec().loadFolder(folder);
        assertEquals(1, entries.size());
        assertEquals(EvidencePackage.Integrity.VALID, entries.get(0).integrity());
        assertEquals(2, entries.get(0).evidence().getStepCount());
        assertEquals("SHA-256", entries.get(0).evidence().getSteps().get(0).getOperation());
    }

    @Test void reviewWindowListsHandInsAndShowsEachStep(@TempDir Path folder) throws Exception {
        OperationSessionLog log = new OperationSessionLog();
        log.add(OperationResult.forOperation("SHA-256").input(new byte[] {1})
                .output(new byte[] {2}, OperationDetail.Classification.PUBLIC).build(), "Hash", List.of(), Map.of());
        EvidencePackageCodec codec = new EvidencePackageCodec();
        codec.write(EvidencePackage.create(new ExerciseSession("Ana", "LAB-1", "2026-11-02 10:00:00"), log,
                "2026-11-02 11:00:00", "test", false), folder.resolve("ana.ccevidence"));
        Files.writeString(folder.resolve("roto.ccevidence"), "broken");
        EvidenceReviewWindow window = reviewWindow(null);
        try {
            loadAndWait(window, () -> window.open(folder));
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    TableView<?> entries = field(window, "entries");
                    assertEquals(2, entries.getItems().size());
                } catch (Exception error) { throw new AssertionError(error); }
            });
            loadAndWait(window, () -> select(window, 1));
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    ListView<?> steps = field(window, "steps");
                    assertTrue(steps.getItems().isEmpty(), "an unreadable file has no steps");
                } catch (Exception error) { throw new AssertionError(error); }
            });
            loadAndWait(window, () -> select(window, 0));
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    ListView<SessionOperationStep> steps = field(window, "steps");
                    TextArea detail = field(window, "detail");
                    assertTrue(detail.getText().contains("LAB-1"));
                    steps.getSelectionModel().select(0);
                    assertTrue(detail.getText().contains("SHA-256"));
                    assertFalse(detail.isEditable());
                } catch (Exception error) { throw new AssertionError(error); }
            });
            // The selected file is verified again, rather than trusting the old folder summary.
            Files.writeString(folder.resolve("ana.ccevidence"), "now damaged");
            loadAndWait(window, () -> select(window, 1));
            loadAndWait(window, () -> select(window, 0));
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    TableView<EvidenceReviewWindow.ReviewEntry> entries = field(window, "entries");
                    assertFalse(entries.getItems().get(0).readable());
                    assertTrue(((ListView<?>) field(window, "steps")).getItems().isEmpty());
                } catch (Exception error) { throw new AssertionError(error); }
            });
        } finally { UiTestLifecycleExtension.onFx(window::close); }
    }

    @Test void exerciseEvidenceSurvivesWorkspaceReplacementClearAndManualSave(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("ana.ccevidence");
        UiTestLifecycleExtension.onFx(() -> {
            try {
                var loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                loader.load();
                ModernMainController shell = loader.getController();
                try {
                    EvidenceSessionCoordinator evidence = coordinator(shell);
                    evidence.start("Ana", "LAB-1");
                    shell.publish(OperationResult.forOperation("SHA-256").build());
                    shell.saveCurrentResultAsSessionStep("My annotation", "");
                    var state = (com.cryptocarver.model.SessionTrailState) field(shell, "sessionTrailState");
                    OperationSessionLog foreign = new OperationSessionLog();
                    foreign.add(OperationResult.forOperation("Foreign RSA").build(), "Another exercise", List.of());
                    state.replace(foreign); // Same state replacement used by SavedSessionsCoordinator.
                    shell.publish(OperationResult.forOperation("AES Encrypt").build());
                    state.clear();
                    evidence.export(file, false);
                    EvidencePackage exported = new EvidencePackageCodec().load(file).evidence();
                    assertEquals("Ana", exported.getStudent());
                    assertEquals("LAB-1", exported.getExercise());
                    assertEquals(List.of("SHA-256", "AES Encrypt"), exported.getSteps().stream()
                            .map(SessionOperationStep::getOperation).toList());
                    assertEquals(EvidencePackage.Integrity.VALID, exported.verify());
                    evidence.start("Luis", "LAB-2");
                    shell.publish(OperationResult.forOperation("New practice").build());
                    evidence.export(file, false);
                    EvidencePackage next = new EvidencePackageCodec().load(file).evidence();
                    assertEquals("Luis", next.getStudent());
                    assertEquals(1, next.getStepCount());
                    assertEquals("New practice", next.getSteps().get(0).getOperation());
                } finally { shell.shutdown(); }
            } catch (Exception error) { throw new AssertionError(error); }
        });
    }

    @Test void folderScanRunsOffFxAndCanBeReplacedOrCancelled(@TempDir Path folder) throws Exception {
        Path slow = Files.createDirectory(folder.resolve("slow"));
        Path fast = Files.createDirectory(folder.resolve("fast"));
        EvidencePackageCodec codec = new EvidencePackageCodec();
        OperationSessionLog log = new OperationSessionLog();
        codec.write(EvidencePackage.create(new ExerciseSession("Ana", "LAB-1", "start"), log,
                "end", "test", false), fast.resolve("ana.ccevidence"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        EvidenceReviewWindow window = reviewWindow(new EvidenceReviewWindow.Source() {
            @Override public List<Path> files(Path path) throws java.io.IOException {
                assertFalse(Platform.isFxApplicationThread());
                if (path.equals(slow)) {
                    entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException cancelled) { interrupted.countDown(); }
                }
                return codec.listFiles(path);
            }
            @Override public EvidencePackageCodec.Entry load(Path file) {
                assertFalse(Platform.isFxApplicationThread());
                return codec.load(file);
            }
        });
        try {
            UiTestLifecycleExtension.onFx(() -> window.open(slow));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            // A second FX action can run while the first folder is blocked on I/O.
            loadAndWait(window, () -> window.open(fast));
            assertTrue(interrupted.await(10, TimeUnit.SECONDS));
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    TableView<EvidenceReviewWindow.ReviewEntry> entries = field(window, "entries");
                    assertEquals(1, entries.getItems().size());
                    assertEquals("Ana", entries.getItems().get(0).student());
                    assertFalse(((HBox) field(window, "activity")).isVisible());
                } catch (Exception error) { throw new AssertionError(error); }
            });
            loadAndWait(window, () -> select(window, 0));
            UiTestLifecycleExtension.onFx(window::close);
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    assertTrue(((java.util.concurrent.ExecutorService) field(window, "worker")).isShutdown());
                } catch (Exception error) { throw new AssertionError(error); }
            });
        } finally {
            release.countDown();
            UiTestLifecycleExtension.onFx(window::close);
        }
    }

    @Test void cancelledDetailCannotReplaceTheNextSelection(@TempDir Path folder) throws Exception {
        EvidencePackageCodec codec = new EvidencePackageCodec();
        OperationSessionLog log = new OperationSessionLog();
        for (String student : List.of("Ana", "Luis")) {
            codec.write(EvidencePackage.create(new ExerciseSession(student, "LAB-1", "start"), log,
                    "end", "test", false), folder.resolve(student + ".ccevidence"));
        }
        AtomicBoolean block = new AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        EvidenceReviewWindow window = reviewWindow(new EvidenceReviewWindow.Source() {
            @Override public List<Path> files(Path path) throws java.io.IOException { return codec.listFiles(path); }
            @Override public EvidencePackageCodec.Entry load(Path file) {
                assertFalse(Platform.isFxApplicationThread());
                if (block.compareAndSet(true, false)) {
                    entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException cancelled) { interrupted.countDown(); }
                }
                return codec.load(file);
            }
        });
        try {
            loadAndWait(window, () -> window.open(folder));
            block.set(true);
            UiTestLifecycleExtension.onFx(() -> select(window, 0));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            UiTestLifecycleExtension.onFx(window::cancelLoading);
            assertTrue(interrupted.await(10, TimeUnit.SECONDS));
            loadAndWait(window, () -> select(window, 1));
            UiTestLifecycleExtension.onFx(() -> {
                try {
                    assertTrue(((TextArea) field(window, "detail")).getText().contains("Luis"));
                    assertEquals("Luis", ((EvidencePackageCodec.Entry) field(window, "selectedEvidence"))
                            .evidence().getStudent());
                } catch (Exception error) { throw new AssertionError(error); }
            });
            UiTestLifecycleExtension.onFx(window::close);
            UiTestLifecycleExtension.onFx(() -> window.show(null));
            loadAndWait(window, () -> select(window, 0));
            UiTestLifecycleExtension.onFx(() -> {
                try { assertTrue(((TextArea) field(window, "detail")).getText().contains("Ana")); }
                catch (Exception error) { throw new AssertionError(error); }
            });
        } finally {
            release.countDown();
            UiTestLifecycleExtension.onFx(window::close);
        }
    }

    private static EvidenceReviewWindow reviewWindow(EvidenceReviewWindow.Source source) throws Exception {
        AtomicReference<EvidenceReviewWindow> result = new AtomicReference<>();
        UiTestLifecycleExtension.onFx(() -> {
            EvidenceReviewWindow window = source == null
                    ? new EvidenceReviewWindow(I18nService.getInstance(), (title, message) -> { })
                    : new EvidenceReviewWindow(I18nService.getInstance(), (title, message) -> { }, source);
            window.show(null);
            result.set(window);
        });
        return result.get();
    }

    private static void select(EvidenceReviewWindow window, int index) {
        try { ((TableView<?>) field(window, "entries")).getSelectionModel().select(index); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private static void loadAndWait(EvidenceReviewWindow window, Runnable action) throws Exception {
        CompletableFuture<Void> idle = new CompletableFuture<>();
        UiTestLifecycleExtension.onFx(() -> {
            action.run();
            try {
                HBox activity = field(window, "activity");
                if (!activity.isVisible()) idle.complete(null);
                else activity.visibleProperty().addListener(new ChangeListener<Boolean>() {
                    @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> observable,
                                                  Boolean previous, Boolean visible) {
                        if (!visible) {
                            activity.visibleProperty().removeListener(this);
                            idle.complete(null);
                        }
                    }
                });
            } catch (Exception error) { idle.completeExceptionally(error); }
        });
        idle.get(15, TimeUnit.SECONDS);
        // Drain the completion handler, which also fills the table/detail after hiding activity.
        UiTestLifecycleExtension.onFx(() -> { });
    }

    private static EvidenceSessionCoordinator coordinator(ModernMainController shell) throws Exception {
        Method method = ModernMainController.class.getDeclaredMethod("evidenceSessionCoordinator");
        method.setAccessible(true);
        return (EvidenceSessionCoordinator) method.invoke(shell);
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(target);
    }
}
