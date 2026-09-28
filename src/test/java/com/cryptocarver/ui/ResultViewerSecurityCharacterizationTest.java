package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ResultViewerSecurityCharacterizationTest {
    private static final String KEY_FIXTURE = "-----BEGIN PRIVATE KEY-----\nSYNTHETIC_FIXTURE\n-----END PRIVATE KEY-----";
    private static final String MARKER_FIXTURE = "*** PRIVATE KEY MATERIAL — NOT RECORDED ***";
    @BeforeAll static void startFx() throws Exception {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { Platform.setImplicitExit(false); }
    }

    @Test void secureCopySelectionAndWholeOutputRespectEveryProfile() throws Exception {
        runFx(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load(); ModernMainController controller = loader.getController(); controller.initialize();
                TextArea area = new TextArea(KEY_FIXTURE); area.setId("syntheticPrivateKeyArea"); area.setEditable(false);
                ((BorderPane) field(controller, "mainPane")).getChildren().add(area);
                ResultAreaTracker tracker = field(controller, "resultAreaTracker"); tracker.register(area); tracker.focus(area); tracker.markUpdated(area);
                Method copy = ModernMainController.class.getDeclaredMethod("handleCopySecure", TextArea.class, String.class, boolean.class);
                copy.setAccessible(true);
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    for (String fixture : new String[]{KEY_FIXTURE, MARKER_FIXTURE}) {
                        area.setText(fixture); tracker.markUpdated(area);
                        setClipboard("SENTINEL"); area.selectRange(0, Math.min(5, fixture.length())); copy.invoke(controller, area, fixture.substring(0, Math.min(5, fixture.length())), true);
                        assertEquals(profile == SecretVisibilityProfile.FULL_LAB ? fixture.substring(0, 5) : "SENTINEL", Clipboard.getSystemClipboard().getString());
                        setClipboard("SENTINEL"); copy.invoke(controller, area, null, false);
                        assertEquals(profile == SecretVisibilityProfile.FULL_LAB ? fixture : "SENTINEL", Clipboard.getSystemClipboard().getString());
                    }
                }
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    @Test void secureShelfCapturePreservesPlaceholderAndPrivateKeyRulesByProfile() throws Exception {
        runFx(() -> {
            try {
                ClipboardShelfManager shelf = ClipboardShelfManager.getInstance(); shelf.clear();
                FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                Parent root = loader.load(); ModernMainController controller = loader.getController(); controller.initialize();
                TextArea area = new TextArea(MARKER_FIXTURE); area.setId("syntheticPrivateKeyArea"); area.setEditable(false);
                ((BorderPane) field(controller, "mainPane")).getChildren().add(area);
                ResultAreaTracker tracker = field(controller, "resultAreaTracker"); tracker.register(area); tracker.focus(area); tracker.markUpdated(area);
                for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile); shelf.clear(); area.setText(MARKER_FIXTURE); tracker.markUpdated(area);
                    controller.handleAddCurrentOutputToShelf();
                    assertTrue(shelf.getEntries().isEmpty(), "A placeholder must never be captured");
                    shelf.clear(); area.setText(KEY_FIXTURE); tracker.markUpdated(area); controller.handleAddCurrentOutputToShelf();
                    assertEquals(profile == SecretVisibilityProfile.FULL_LAB, shelf.getEntries().stream().anyMatch(ClipboardEntry::isSessionOnlyPrivateKey));
                }
                shelf.clear();
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    private static void setClipboard(String value) { ClipboardContent content = new ClipboardContent(); content.putString(value); Clipboard.getSystemClipboard().setContent(content); }
    private static <T> T field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(target);
    }
    private static void runFx(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1); java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> { try { task.run(); } catch (Throwable t) { error.set(t); } finally { latch.countDown(); } });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "JavaFX action timed out"); if (error.get() != null) throw new AssertionError(error.get());
    }
}
