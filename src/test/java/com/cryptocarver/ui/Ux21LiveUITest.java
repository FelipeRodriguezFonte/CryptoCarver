package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.ResultComparator;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in JavaFX checks for the result-reuse routes requested by UX-21. */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class Ux21LiveUITest {
    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> { try { action.run(); } finally { done.countDown(); } });
        assertTrue(done.await(15, TimeUnit.SECONDS));
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(owner);
            } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    void historyReopenClearsResultsSecretsAndInputMaterial() throws Exception {
        ModernMainController controller = loadMain();
        GenericController generic = field(controller, "genericContainerController");
        TextArea input = field(generic, "hashInputArea");
        TextArea result = field(generic, "hashOutputArea");
        fx(() -> {
            result.setText("SENSITIVE RESULT MUST NOT REOPEN");
            controller.restoreOperationState(Map.of(
                    "GenericController.hashInputArea", "safe input",
                    "GenericController.hashOutputArea", "old result",
                    "CipherController.symmetricKeyField", "[REDACTED_SECRET]"), "Hashing: SHA-256");
        });
        assertEquals("", input.getText());
        assertEquals("", result.getText());
        assertTrue(((javafx.scene.Node) field(controller, "genericContainer")).isVisible());
    }

    @Test
    void shelfInjectionPreservesFormatForHashingAndTR31() throws Exception {
        ModernMainController controller = loadMain();
        GenericController generic = field(controller, "genericContainerController");
        KeysController keys = field(controller, "keysContainerController");
        fx(() -> {
            controller.fillClipboardTarget("HASHING", "00AA", ClipboardEntry.Format.HEX);
            controller.fillClipboardTarget("TR31", "B0096P0TE00E000000000000000000000000000", ClipboardEntry.Format.TEXT);
        });
        assertEquals("00AA", ((TextArea) field(generic, "hashInputArea")).getText());
        assertEquals("Hexadecimal", ((ComboBox<String>) field(controller, "inputFormatCombo")).getValue());
        assertEquals("B0096P0TE00E000000000000000000000000000",
                ((TextArea) field(keys, "tr31KeyBlockField")).getText());
    }

    @Test
    void batchResetAndTr31ResetAreLocal() throws Exception {
        ModernMainController controller = loadMain();
        GenericController generic = field(controller, "genericContainerController");
        KeysController keys = field(controller, "keysContainerController");
        TextArea batchInput = field(generic, "batchInputArea");
        TextArea batchResult = field(generic, "batchResultArea");
        TextArea tr31Input = field(keys, "tr31KeyBlockField");
        fx(() -> {
            batchInput.setText("input\noriginal");
            batchResult.setText("generated");
            generic.handleResetBatch();
            keys.fillTR31KeyBlockInput("TR31-BLOCK");
            keys.handleTR31Clear();
        });
        assertEquals("", batchInput.getText());
        assertEquals("", batchResult.getText());
        assertEquals("", tr31Input.getText());
    }

    @Test
    void compareResultsShowsDifferenceAndCanReset() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/compare_results.fxml"));
        final CompareResultsController[] ref = new CompareResultsController[1];
        fx(() -> {
            try {
                Parent root = loader.load();
                ref[0] = loader.getController();
                ref[0].setEntries(
                        new ClipboardEntry("one", "0011", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Hashing"),
                        new ClipboardEntry("two", "0012", ClipboardEntry.Format.HEX, OperationDetail.Classification.PUBLIC, "Hashing"));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertEquals(ResultComparator.Status.DIFFERENT.getLabel(),
                ((javafx.scene.control.Label) field(ref[0], "statusBadgeLabel")).getText());
        fx(() -> ref[0].clearComparison());
        assertTrue(((javafx.scene.control.Label) field(ref[0], "summaryLabel")).getText().length() > 0);
    }

    private static ModernMainController loadMain() throws Exception {
        final ModernMainController[] ref = new ModernMainController[1];
        fx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(Ux21LiveUITest.class.getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ref[0] = loader.getController();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        return ref[0];
    }
}
