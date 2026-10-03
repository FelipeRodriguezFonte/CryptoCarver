package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.cryptocarver.model.payments.*;
import com.cryptocarver.service.I18nService;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Shared isolation and real FXML setup for the three independent regressions. */
abstract class UiMinorFixesFixture {
    @TempDir Path directory;
    AppSettings previousSettings;
    List<ClipboardEntry> previousShelf;
    ModernMainController shell;
    Stage stage;
    Path settingsFile;

    @BeforeEach void load() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        previousSettings = AppSettings.getInstance();
        previousShelf = List.copyOf(ClipboardShelfManager.getInstance().getEntries());
        settingsFile = directory.resolve("settings.json");
        fx(() -> {
            Platform.setImplicitExit(false);
            AppSettings isolated = new AppSettings(settingsFile);
            isolated.setLanguagePreference(LanguagePreference.EN);
            AppSettings.setInstanceForTesting(isolated);
            openShell();
        });
        fx(() -> {});
    }

    void openShell() throws Exception {
        var loader = Fxml.loader("/fxml/main-view-modern.fxml");
        javafx.scene.Parent root = loader.load(); shell = loader.getController();
        stage = new Stage(); stage.setScene(new Scene(root, 1400, 900)); stage.show();
    }

    void closeShell() {
        if (shell != null) shell.shutdown();
        if (stage != null) { stage.close(); stage.setScene(null); }
    }

    @AfterEach void restore() throws Exception {
        fx(() -> {
            try { closeShell(); }
            finally {
                AppSettings.setInstanceForTesting(previousSettings);
                I18nService.getInstance().refreshFromSettings();
                var shelf = ClipboardShelfManager.getInstance();
                if (!previousShelf.equals(shelf.getEntries())) {
                    shelf.clear();
                    for (int i = previousShelf.size() - 1; i >= 0; i--) shelf.addEntry(previousShelf.get(i));
                }
                assertEquals(previousShelf, shelf.getEntries());
            }
        });
    }

    PaymentProfile profile(String id) {
        return PaymentProfileManager.getAllProfiles().stream().filter(p -> id.equals(p.getId())).findFirst().orElseThrow();
    }

    void loadProfile(PaymentProfile profile) throws Exception {
        Menu laboratory = field(shell, "laboratoryMenu");
        Menu entry = laboratory.getItems().stream().filter(item -> item instanceof Menu && profile.getName().equals(item.getText()))
                .map(item -> (Menu)item).findFirst().orElseThrow();
        entry.getItems().get(0).fire();
    }

    Label status() throws Exception { return field(shell, "statusLabel"); }
    @SuppressWarnings("unchecked") static <T> T field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return (T) f.get(object); }
            catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }
    @FunctionalInterface interface FxAction { void run() throws Exception; }
    static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); } });
        assertTrue(done.await(30, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
    }
}
