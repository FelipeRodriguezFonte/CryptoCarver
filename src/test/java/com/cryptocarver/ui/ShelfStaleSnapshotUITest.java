package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ClipboardShelfManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generate a key, encrypt elsewhere, come back to Key Generation and press
 * Add to Shelf: the Shelf must receive the generated key, never the
 * ciphertext published by the other screen.
 */
@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ShelfStaleSnapshotUITest {
    private final ClipboardShelfManager shelf = ClipboardShelfManager.getInstance();

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
        }
    }

    private SecretVisibilityProfile previousProfile;

    @BeforeEach
    void reset() {
        shelf.clear();
        previousProfile = AppSettings.getInstance().getSecretVisibilityProfile();
        AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
    }

    /** Other suites expect the profile they started with; FULL_LAB disables history redaction. */
    @org.junit.jupiter.api.AfterEach
    void restoreProfile() {
        shelf.clear();
        AppSettings.getInstance().setSecretVisibilityProfile(previousProfile);
    }

    @Test
    void addToShelfOnKeyGenerationUsesTheGeneratedKeyNotAnotherScreensResult() throws Exception {
        AtomicReference<String> generatedKey = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                loader.load();
                ModernMainController controller = loader.getController();
                controller.navigateToModule("Key Generation");
                KeysController keys = field(controller, "keysContainerController");
                keys.handleGenerateKey();
                generatedKey.set(((TextArea) field(keys, "generatedKeyField")).getText());

                controller.navigateToModule("Symmetric Ciphers");
                controller.publish(OperationResult.forOperation("Symmetric Encrypt")
                        .output(new byte[16], OperationDetail.Classification.PUBLIC)
                        .build());

                controller.navigateToModule("Key Generation");
                controller.handleAddCurrentOutputToShelf();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        List<ClipboardEntry> entries = shelf.getEntries();
        assertEquals(1, entries.size(), entries::toString);
        ClipboardEntry entry = entries.get(0);
        assertEquals("Generate Symmetric Key", entry.getSourceOperation());
        assertEquals(generatedKey.get(), entry.getValue());
        assertEquals(OperationDetail.Classification.SECRET, entry.getClassification());
    }

    @Test
    void addToShelfRightAfterGeneratingAddsTheGeneratedKey() throws Exception {
        AtomicReference<String> generatedKey = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                loader.load();
                ModernMainController controller = loader.getController();
                controller.navigateToModule("Key Generation");
                KeysController keys = field(controller, "keysContainerController");
                keys.handleGenerateKey();
                generatedKey.set(((TextArea) field(keys, "generatedKeyField")).getText());
                controller.handleAddCurrentOutputToShelf();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        List<ClipboardEntry> entries = shelf.getEntries();
        assertEquals(1, entries.size(), entries::toString);
        assertEquals(generatedKey.get(), entries.get(0).getValue());
        assertEquals("Generate Symmetric Key", entries.get(0).getSourceOperation());
    }

    @Test
    void anotherScreensResultIsNotAddedUnderTheActiveScreen() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                loader.load();
                ModernMainController controller = loader.getController();
                controller.navigateToModule("Symmetric Ciphers");
                controller.publish(OperationResult.forOperation("Symmetric Encrypt")
                        .output(new byte[16], OperationDetail.Classification.PUBLIC)
                        .build());
                controller.navigateToModule("Hashing");
                controller.handleAddCurrentOutputToShelf();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        assertTrue(shelf.getEntries().isEmpty(), () -> shelf.getEntries().toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void runAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
