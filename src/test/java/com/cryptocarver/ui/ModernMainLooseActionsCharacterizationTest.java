package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SavedSession;
import com.cryptocarver.model.SavedSessionsManager;
import com.cryptocarver.model.LanguagePreference;
import com.cryptocarver.service.I18nService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModernMainLooseActionsCharacterizationTest {
    private static final Path TEST_HOME = Path.of("target", "test-home").toAbsolutePath();
    private static final String SESSION_NAME = "encargo-39-synthetic-session-" + UUID.randomUUID();
    private final Set<Window> existingWindows = new HashSet<>();
    private final Set<String> existingSessionIds = new HashSet<>();
    private AppSettings previousSettings;
    private String previousHome;
    private String previousTestMode;
    private String previousLastRoute;
    private LanguagePreference previousLanguage;
    private Map<Path, byte[]> previousTestHomeFiles;
    private Stage mainStage;
    private ModernMainController controller;

    @BeforeAll
    static void initializeJavaFxOnce() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @BeforeEach
    void loadProductionShellInIsolatedHome() throws Exception {
        previousHome = System.getProperty("user.home");
        previousTestMode = System.getProperty("test.mode");
        previousSettings = AppSettings.getInstance();
        previousLastRoute = previousSettings.getLastRoute();
        previousLanguage = I18nService.getInstance().getPreference();
        System.setProperty("user.home", TEST_HOME.toString());
        System.setProperty("test.mode", "true");
        Files.createDirectories(TEST_HOME);
        previousTestHomeFiles = readTestHomeFiles();
        AppSettings testSettings = new AppSettings(TEST_HOME.resolve("settings.json"));
        testSettings.setLastRoute("Hashing");
        AppSettings.setInstanceForTesting(testSettings);
        runFx(() -> {
            existingWindows.addAll(Window.getWindows().stream().toList());
            SavedSessionsManager.getInstance().getSessions().stream()
                    .filter(session -> SESSION_NAME.equals(session.getName()))
                    .forEach(SavedSessionsManager.getInstance()::removeSession);
            existingSessionIds.addAll(SavedSessionsManager.getInstance().getSessions().stream()
                    .map(SavedSession::getId).toList());
            I18nService.getInstance().setPreference(com.cryptocarver.model.LanguagePreference.EN);
            try {
                FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                controller = loader.getController();
                mainStage = new Stage();
                Scene scene = new Scene(root, 1400, 900);
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                mainStage.setScene(scene);
                mainStage.show();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    @AfterEach
    void cleanSessionDataAndRestoreSettings() throws Exception {
        runFx(() -> {
            for (Window window : Window.getWindows().stream().toList()) {
                if (!existingWindows.contains(window)) {
                    window.hide();
                }
            }
            for (SavedSession session : SavedSessionsManager.getInstance().getSessions()) {
                if (!existingSessionIds.contains(session.getId())) {
                    SavedSessionsManager.getInstance().removeSession(session);
                }
            }
            if (mainStage != null) {
                mainStage.close();
            }
        });
        restoreTestHomeFiles(previousTestHomeFiles);
        previousSettings.setLastRoute(previousLastRoute);
        AppSettings.setInstanceForTesting(previousSettings);
        I18nService.getInstance().setPreference(previousLanguage);
        restoreProperty("test.mode", previousTestMode);
        restoreProperty("user.home", previousHome);
    }

    @Test
    void savingNamedSessionShowsItInManagerAndDoesNotPersistPlaintextSecretValues() throws Exception {
        runFx(() -> ((TextField) getField(getField(controller, "genericContainerController"), "batchKeyField"))
                .setText("SYNTHETIC_REDACTION_MARKER"));
        scheduleDialogResponse(SESSION_NAME, ButtonType.OK);
        runFx(() -> controller.handleSaveSession());
        SavedSession saved = SavedSessionsManager.getInstance().getSessions().stream()
                .filter(session -> SESSION_NAME.equals(session.getName())).findFirst().orElseThrow();
        Path persistedSessions = TEST_HOME.resolve(".cryptocarver").resolve("saved_sessions.json");
        assertTrue(Files.exists(persistedSessions));
        String serialized = Files.readString(persistedSessions);
        assertFalse(serialized.contains("SYNTHETIC_REDACTION_MARKER"));
        assertTrue(serialized.contains("[REDACTED_SECRET]"));
        assertNotNull(saved.getUiState());
    }

    @Test
    void savingSessionWithEmptyNameDoesNotPersistAnything() throws Exception {
        scheduleDialogResponse("", ButtonType.OK);
        runFx(() -> controller.handleSaveSession());
        assertTrue(SavedSessionsManager.getInstance().getSessions().stream()
                .noneMatch(session -> SESSION_NAME.equals(session.getName())));
    }

    @Test
    void cancellingSaveSessionDoesNotPersistAnything() throws Exception {
        scheduleDialogResponse(SESSION_NAME, ButtonType.CANCEL);
        runFx(() -> controller.handleSaveSession());
        assertTrue(SavedSessionsManager.getInstance().getSessions().stream()
                .noneMatch(session -> SESSION_NAME.equals(session.getName())));
    }

    @Test
    void epochRouteOpensItsWindowWithInitialConvertedTimestamp() throws Exception {
        runFx(() -> controller.navigateToModule("Epoch Converter"));
        Stage epochWindow = shownWindow("Epoch Converter");
        assertEquals("Epoch Converter", getField(controller, "currentActiveOperation"));
        assertTrue(epochWindow.getScene().getRoot() instanceof javafx.scene.layout.VBox);
        var fields = findNode(epochWindow.getScene().getRoot(), TextField.class);
        assertEquals(2, fields.size());
        assertTrue(Long.parseLong(fields.get(0).getText()) > 0);
        assertTrue(fields.get(1).getText().startsWith(java.time.LocalDate.now().getYear() + "-"));
    }

    @Test
    void jsonRouteOpensItsWindowWithEmptyInputAndOutput() throws Exception {
        runFx(() -> controller.navigateToModule("JSON Formatter"));
        Stage jsonWindow = shownWindow("JSON Formatter");
        assertEquals("JSON Formatter", getField(controller, "currentActiveOperation"));
        var areas = findNode(jsonWindow.getScene().getRoot(), TextArea.class);
        assertEquals(2, areas.size());
        assertEquals("", areas.get(0).getText());
        assertEquals("", areas.get(1).getText());
        assertEquals("Paste JSON here...", areas.get(0).getPromptText());
    }

    private void scheduleDialogResponse(String name, ButtonType response) throws Exception {
        runFx(() -> Platform.runLater(() -> {
            for (Window window : Window.getWindows()) {
                if (!(window instanceof Stage stage) || !stage.isShowing() || stage.getScene() == null) {
                    continue;
                }
                if (!(stage.getScene().getRoot() instanceof DialogPane pane)
                        || !pane.getButtonTypes().contains(response)) {
                    continue;
                }
                if (ButtonType.OK.equals(response)) {
                    ((TextField) pane.lookup(".text-field")).setText(name);
                }
                ((Button) pane.lookupButton(response)).fire();
                return;
            }
            Platform.runLater(() -> respondWhenDialogAppears(name, response));
        }));
    }

    private void respondWhenDialogAppears(String name, ButtonType response) {
        for (Window window : Window.getWindows()) {
            if (!(window instanceof Stage stage) || !stage.isShowing() || stage.getScene() == null) {
                continue;
            }
            if (!(stage.getScene().getRoot() instanceof DialogPane pane)
                    || !pane.getButtonTypes().contains(response)) {
                continue;
            }
            if (ButtonType.OK.equals(response)) {
                ((TextField) pane.lookup(".text-field")).setText(name);
            }
            ((Button) pane.lookupButton(response)).fire();
            return;
        }
        Platform.runLater(() -> respondWhenDialogAppears(name, response));
    }

    private Stage shownWindow(String title) throws Exception {
        AtomicReference<Stage> found = new AtomicReference<>();
        runFx(() -> found.set(Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(Stage::isShowing).filter(window -> title.equals(window.getTitle())).findFirst().orElse(null)));
        assertNotNull(found.get(), title);
        return found.get();
    }

    private static <T extends javafx.scene.Node> java.util.List<T> findNode(javafx.scene.Node root, Class<T> type) {
        java.util.List<T> found = new java.util.ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                found.addAll(findNode(child, type));
            }
        }
        return found;
    }

    private static Object getField(Object instance, String name) {
        try {
            java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static Map<Path, byte[]> readTestHomeFiles() throws Exception {
        Map<Path, byte[]> files = new HashMap<>();
        try (var paths = Files.walk(TEST_HOME)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(TEST_HOME.relativize(path), Files.readAllBytes(path));
            }
        }
        return files;
    }

    private static void restoreTestHomeFiles(Map<Path, byte[]> snapshot) throws Exception {
        try (var paths = Files.walk(TEST_HOME)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Files.delete(path);
            }
        }
        for (Map.Entry<Path, byte[]> file : snapshot.entrySet()) {
            Path path = TEST_HOME.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
        }
    }

    private static void runFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
    }
}
