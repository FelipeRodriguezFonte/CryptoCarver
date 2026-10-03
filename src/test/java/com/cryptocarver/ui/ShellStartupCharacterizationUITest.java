package com.cryptocarver.ui;

import com.cryptocarver.model.*;
import com.cryptocarver.service.I18nService;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ShellStartupCharacterizationUITest {
    @TempDir Path directory;
    private AppSettings previousSettings;
    private List<ClipboardEntry> previousShelf;
    private RecordingShell shell;
    private Parent root;
    private Stage stage;

    @BeforeEach void load() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); } catch (IllegalStateException started) { ready.countDown(); }
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        previousSettings = AppSettings.getInstance();
        previousShelf = List.copyOf(ClipboardShelfManager.getInstance().getEntries());
        fx(() -> {
            Platform.setImplicitExit(false);
            AppSettings isolated = new AppSettings(directory.resolve("settings.json"));
            isolated.setLanguagePreference(LanguagePreference.EN); isolated.setLastRoute("Hashing");
            AppSettings.setInstanceForTesting(isolated);
            var loader = Fxml.loader("/fxml/main-view-modern.fxml");
            loader.setControllerFactory(type -> {
                try { return type == ModernMainController.class ? new RecordingShell() : type.getDeclaredConstructor().newInstance(); }
                catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
            });
            root = loader.load(); shell = loader.getController();
            shell.rows.add("initialize.returned=" + field(shell, "currentActiveOperation"));
            stage = new Stage(); stage.setScene(new Scene(root, 1400, 900)); stage.show();
        });
        fx(() -> shell.rows.add("queue.drained=true"));
    }

    @AfterEach void restore() throws Exception {
        fx(() -> {
            if (shell != null) {
                shell.shutdown();
                // Defensive baseline teardown while the missing cleanup is characterized.
                I18nService.getInstance().removeLocaleChangeListener(field(shell, "i18nListener"));
            }
            if (stage != null) { stage.close(); stage.setScene(null); }
            AppSettings.setInstanceForTesting(previousSettings);
            I18nService.getInstance().refreshFromSettings();
            assertEquals(previousShelf, ClipboardShelfManager.getInstance().getEntries());
        });
    }

    @Test void startupOrderRouteServicesAndShutdownTranscript() throws Exception {
        fx(() -> {
            assertEquals("Hashing", field(shell, "currentActiveOperation"));
            assertEquals("Hashing", AppSettings.getInstance().getLastRoute());
            assertEquals("Hashing", new AppSettings(directory.resolve("settings.json")).getLastRoute());
            GenericController generic = field(shell, "genericContainerController");
            assertSame(shell, field(generic, "statusReporter"));
            shell.rows.add("restored=Hashing;generic.reporter=shell");
            shell.rows.add("locale.registered=" + localeRegistered());
            shell.rows.add("lifecycle.attached=" + (field(shell, "lifecycleWindow") == stage));
            shell.navigateToModule("JWT (Signed)");
            JOSEController jose = field(shell, "joseController");
            assertNotNull(jose); assertSame(shell, field(jose, "statusReporter"));
            shell.rows.add("navigate=" + field(shell, "currentActiveOperation") + ";jose.reporter=shell");
            shell.shutdown();
            shell.rows.add("shutdown.locale.registered=" + localeRegistered());
            shell.rows.add("shutdown.lifecycle.detached=" + (field(shell, "lifecycleNode") == null && field(shell, "lifecycleScene") == null && field(shell, "lifecycleWindow") == null));
            OperationExecutor executor = field(shell, "operationExecutor");
            shell.rows.add("shutdown.progress.detached=" + (field(executor, "showProgressHandler") == null && field(executor, "updateProgressHandler") == null && field(executor, "hideProgressHandler") == null));
            shell.shutdown(); shell.rows.add("shutdown.idempotent=true");
        });
        String transcript = String.join("\n", shell.rows) + "\n";
        Files.writeString(Path.of("target/shell-startup-transcript.txt"), transcript);
        assertEquals("116b7045624997160c793f67818c6c4f42dd912650bd30b225a4633e1d58f4ba", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(transcript.getBytes(StandardCharsets.UTF_8))), transcript);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"locale", "navigation", "security-tip", "responsive"})
    void shutdownUnregistersShellListenersAndPreventsClosedShellRepainting(String kind) throws Exception {
        fx(() -> {
            assertTrue(localeRegistered());
            shell.shutdown();
            switch (kind) {
                case "locale" -> {
                    assertFalse(localeRegistered(), "BUG: shell locale listener survives shutdown");
                    Menu file = field(shell, "fileMenu"); String original = file.getText();
                    I18nService.getInstance().setPreference(LanguagePreference.ES);
                    assertEquals(original, file.getText(), "Closed shell must not repaint on locale changes");
                }
                case "navigation" -> {
                    assertNull(field((NavigationRail) field(shell, "navigationRail"), "onSectionSelected"));
                    assertNull(field((SidePanel) field(shell, "sidePanel"), "onItemSelected"));
                }
                case "security-tip" -> {
                    Label tip = field(shell, "securityTipLabel"); javafx.scene.layout.VBox box = field(shell, "securityTipBox");
                    assertNotNull(tip); assertNotNull(box);
                    box.setVisible(false); tip.setText("synthetic tip"); assertFalse(box.isVisible(), "Closed shell tip listener survives");
                }
                case "responsive" -> {
                    javafx.scene.layout.BorderPane pane = field(shell, "mainPane");
                    javafx.scene.layout.VBox inspector = field(shell, "inspectorPanel");
                    inspector.setVisible(true); pane.resize(800, 900);
                    assertTrue(inspector.isVisible(), "Closed shell width listener survives");
                }
                default -> fail(kind);
            }
        });
    }

    private boolean localeRegistered() throws Exception {
        Object listener = field(shell, "i18nListener");
        List<?> listeners = field(I18nService.getInstance(), "listeners");
        return listeners.stream().anyMatch(reference -> ((WeakReference<?>) reference).get() == listener);
    }

    static final class RecordingShell extends ModernMainController {
        final List<String> rows = new ArrayList<>();
        @Override void startupPhaseCompleted(String phase) {
            try {
                List<String> present = new ArrayList<>();
                for (String name : List.of("shellDialogCoordinator", "navigationRouter", "navigationChrome", "commandPaletteCoordinator",
                        "inlineErrorPresenter", "statusBarPresenter", "navigationController", "sessionTrailCoordinator",
                        "historyCoordinator", "historyManager", "resultViewerCoordinator")) {
                    if (field(this, name) != null) present.add(name);
                }
                var method = ModernMainController.class.getDeclaredMethod("moduleHosts"); method.setAccessible(true);
                ModuleHost[] hosts = (ModuleHost[]) method.invoke(this);
                int configured = 0, loaded = 0;
                for (ModuleHost host : hosts) if (host != null) {
                    if (field(host, "loader") != null) configured++;
                    if (host.controller() != null) loaded++;
                }
                rows.add(phase + "|services=" + String.join(",", present) + "|hosts=" + configured + "/" + loaded + "|route=" + field(this, "currentActiveOperation"));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        }
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
            catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }
    @FunctionalInterface private interface FxAction { void run() throws Exception; }
    private static void fx(FxAction action) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable failure) { error.set(failure); } finally { done.countDown(); } });
        assertTrue(done.await(30, TimeUnit.SECONDS)); if (error.get() != null) throw new AssertionError(error.get());
    }
}
