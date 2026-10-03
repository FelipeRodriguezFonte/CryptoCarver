package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ResultCaptureSecurityUITest {
    private static final String PRIVATE = "-----BEGIN PRIVATE KEY-----\nINVENTED_CAPTURE_FIXTURE\n-----END PRIVATE KEY-----";
    private static final String MARKER = "*** PRIVATE KEY MATERIAL — NOT RECORDED ***";

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
    }

    @Test void privateMaterialInGenericAreaCannotEscapeRestrictedProfiles() throws Exception {
        fx(() -> {
            AppSettings settings = AppSettings.getInstance();
            SecretVisibilityProfile previous = settings.getSecretVisibilityProfile();
            String route = settings.getLastRoute();
            ClipboardShelfManager shelf = ClipboardShelfManager.getInstance();
            ModernMainController shell = null;
            String clipboard = Clipboard.getSystemClipboard().getString();
            try {
                settings.setLastRoute(""); shelf.clear();
                var loader = Fxml.loader("/fxml/main-view-modern.fxml");
                Parent root = loader.load(); shell = loader.getController();
                TextArea area = new TextArea(); area.setId("fixtureResultArea"); area.setEditable(false);
                ((BorderPane) field(shell, "mainPane")).getChildren().add(area);
                ResultAreaTracker tracker = (ResultAreaTracker) field(shell, "resultAreaTracker");
                tracker.register(area); tracker.focus(area);
                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    settings.setSecretVisibilityProfile(profile);
                    for (String fixture : List.of(PRIVATE, MARKER)) {
                        area.setText(fixture); tracker.markUpdated(area);
                        assertFalse(shell.resolveCurrentOutputText().contains(fixture), "Expanded capture must protect private material regardless of area id");
                        putClipboard("CAPTURE_SENTINEL"); shell.handleCopyOutput();
                        assertEquals("CAPTURE_SENTINEL", Clipboard.getSystemClipboard().getString());
                        shelf.clear(); shell.handleAddCurrentOutputToShelf();
                        assertTrue(shelf.getEntries().isEmpty(), "Restricted Shelf must contain no private material");
                    }
                }
                settings.setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                area.setText(PRIVATE); tracker.markUpdated(area); shelf.clear();
                shell.handleAddCurrentOutputToShelf();
                assertEquals(1, shelf.getEntries().size());
                assertTrue(shelf.getEntries().get(0).isSessionOnlyPrivateKey(), "Private PEM must never become a persistent PUBLIC entry");
            } finally {
                shelf.clear(); if (shell != null) shell.shutdown();
                settings.setSecretVisibilityProfile(previous); settings.setLastRoute(route);
                if (clipboard == null) Clipboard.getSystemClipboard().clear(); else putClipboard(clipboard);
            }
        });
    }

    @Test void privatePayloadWithPublicMetadataCannotEscapeRestrictedProfiles() throws Exception {
        fx(() -> {
            AppSettings settings = AppSettings.getInstance();
            SecretVisibilityProfile previous = settings.getSecretVisibilityProfile();
            String route = settings.getLastRoute();
            ClipboardShelfManager shelf = ClipboardShelfManager.getInstance();
            ModernMainController shell = null;
            String clipboard = Clipboard.getSystemClipboard().getString();
            try {
                settings.setLastRoute(""); shelf.clear();
                var loader = Fxml.loader("/fxml/main-view-modern.fxml"); loader.load(); shell = loader.getController();
                for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
                    settings.setSecretVisibilityProfile(profile);
                    for (String fixture : List.of(PRIVATE, MARKER)) {
                        shell.publish(OperationResult.forOperation("Invented public metadata").output(fixture.getBytes(StandardCharsets.UTF_8)).build());
                        assertFalse(shell.resolveCurrentOutputText().contains(fixture), "Expanded published capture must protect private material");
                        putClipboard("CAPTURE_SENTINEL"); shell.handleCopyOutput();
                        assertEquals("CAPTURE_SENTINEL", Clipboard.getSystemClipboard().getString());
                        shelf.clear(); shell.handleAddCurrentOutputToShelf(); assertTrue(shelf.getEntries().isEmpty());
                    }
                }
            } finally {
                shelf.clear(); if (shell != null) shell.shutdown();
                settings.setSecretVisibilityProfile(previous); settings.setLastRoute(route);
                if (clipboard == null) Clipboard.getSystemClipboard().clear(); else putClipboard(clipboard);
            }
        });
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void putClipboard(String value) { ClipboardContent content = new ClipboardContent(); content.putString(value); Clipboard.getSystemClipboard().setContent(content); }
    private interface Action { void run() throws Exception; }
    private static void fx(Action action) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); } });
        assertTrue(done.await(45, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
    }
}
