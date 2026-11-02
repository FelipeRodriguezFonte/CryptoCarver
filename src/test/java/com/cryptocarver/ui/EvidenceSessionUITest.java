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
        UiTestLifecycleExtension.onFx(() -> {
            try {
                EvidenceReviewWindow window = new EvidenceReviewWindow(I18nService.getInstance(), (title, message) -> { });
                window.show(null);
                window.open(folder);
                TableView<EvidencePackageCodec.Entry> entries = field(window, "entries");
                ListView<SessionOperationStep> steps = field(window, "steps");
                TextArea detail = field(window, "detail");
                assertEquals(2, entries.getItems().size());
                entries.getSelectionModel().select(1);
                assertTrue(steps.getItems().isEmpty(), "an unreadable file has no steps");
                entries.getSelectionModel().select(0);
                assertTrue(detail.getText().contains("LAB-1"));
                steps.getSelectionModel().select(0);
                assertTrue(detail.getText().contains("SHA-256"));
                assertFalse(detail.isEditable());
            } catch (Exception error) { throw new AssertionError(error); }
        });
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
