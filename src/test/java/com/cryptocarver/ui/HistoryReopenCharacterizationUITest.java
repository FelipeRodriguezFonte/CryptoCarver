package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class HistoryReopenCharacterizationUITest {
    private static final String PRIVATE_FIXTURE = "SYNTHETIC_HISTORY_PRIVATE_FIXTURE_55";
    private static final Path TRANSCRIPT = Path.of("src/test/resources/com/cryptocarver/ui/history-reopen-transcript.txt");
    private static final String EXPECTED_SHA256 = "TO_BE_FIXED";
    @TempDir Path tempDir;

    @BeforeAll static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @Test void reopenRerunClearAndPrivacyProfilesMatchPinnedTranscript() throws Exception {
        var shelf = com.cryptocarver.model.ClipboardShelfManager.getInstance();
        var shelfBefore = shelf.getEntries();
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        StringBuilder transcript = new StringBuilder();
        try {
            onFx(() -> {
                try {
                    AppSettings isolated = new AppSettings(tempDir.resolve("settings.json"));
                    AppSettings.setInstanceForTesting(isolated);
                    FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                    Parent root = loader.load();
                    ModernMainController controller = loader.getController();
                    setField(controller, "historyManager", new HistoryManager(tempDir.resolve("history.json")));
                    Stage stage = new Stage(); stage.setScene(new Scene(root)); stage.show(); stageRef.set(stage);
                    controllerRef.set(controller);
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            ModernMainController controller = controllerRef.get();
            List<Route> routes = List.of(
                    new Route("Symmetric Ciphers", "cipherContainerController", "symmetricKeyField", "SYNTHETIC-CIPHER-KEY"),
                    new Route("Hashing", "genericContainerController", "hashInputArea", "SYNTHETIC-HASH-INPUT"),
                    new Route("Clear PIN Blocks", "paymentsContainerController", "pinField", "SYNTHETIC-PAYMENT-PIN"),
                    new Route("JWT (Signed)", "joseContainerController", "jwtKeyArea", "SYNTHETIC-JOSE-KEY"));
            for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED,
                    SecretVisibilityProfile.REDACTED, SecretVisibilityProfile.FULL_LAB)) {
                onFx(() -> AppSettings.getInstance().setSecretVisibilityProfile(profile));
                for (Route route : routes) {
                    onFx(() -> {
                        try {
                            controller.navigateToModule(route.navigation());
                            Object module = field(controller, route.moduleField());
                            TextInputControl input = field(module, route.inputField());
                            input.setText(route.value());
                            controller.addToHistory("Synthetic history " + route.navigation(), List.of(
                                    OperationDetail.secretDetail("Synthetic private detail", PRIVATE_FIXTURE)));
                            HistoryCommand item = controller.getHistoryManager().getHistoryItems().get(
                                    controller.getHistoryManager().getHistoryItems().size() - 1);
                            controller.reopenHistoryOperation(item);
                            String active = (String) field(controller, "currentActiveOperation");
                            String expected = profile == SecretVisibilityProfile.FULL_LAB ? route.value() : "";
                            assertEquals(expected, input.getText(), profile + " " + route.navigation());
                            assertEquals(route.navigation(), active);
                            String json = Files.readString(tempDir.resolve("history.json"));
                            if (profile != SecretVisibilityProfile.FULL_LAB) {
                                assertFalse(json.contains(PRIVATE_FIXTURE), "private details persisted under " + profile);
                            }
                            transcript.append(profile).append('|').append(route.navigation()).append('|')
                                    .append(active).append('|').append(input.getText().isEmpty() ? "empty" : "restored")
                                    .append(profile == SecretVisibilityProfile.FULL_LAB ? "|private=full-lab\n" : "|private=filtered\n");
                        } catch (Exception failure) { throw new AssertionError(failure); }
                    });
                }
            }
            onFx(() -> {
                try {
                    AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                    controller.navigateToModule("Hashing");
                    GenericController generic = field(controller, "genericContainerController");
                    var input = (javafx.scene.control.TextArea) field(generic, "hashInputArea");
                    input.setText("abc");
                    generic.handleCalculateHash();
                    var output = (javafx.scene.control.TextArea) field(generic, "hashOutputArea");
                    assertTrue(output.getText().toLowerCase().contains("ba7816bf"), "hash rerun output=" + output.getText());
                    transcript.append("rerun|Hashing|SHA-256|ok\n");
                    Method clear = ModernMainController.class.getDeclaredMethod("handleClearInput"); clear.setAccessible(true); clear.invoke(controller);
                    assertEquals("", input.getText());
                    transcript.append("clear|Hashing|empty\n");
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(transcript.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String digest = HexFormat.of().formatHex(bytes);
            assertEquals(EXPECTED_SHA256, digest, "transcript=" + transcript);
            assertEquals(Files.readString(TRANSCRIPT), transcript.toString());
        } finally {
            onFx(() -> {
                if (stageRef.get() != null) stageRef.get().close();
                AppSettings.resetInstanceForTesting();
            });
            assertEquals(shelfBefore, shelf.getEntries(), "history characterization must preserve the Shelf");
        }
    }

    private record Route(String navigation, String moduleField, String inputField, String value) { }
    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) { try { Field f = type.getDeclaredField(name); f.setAccessible(true); return (T) f.get(target); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); } }
        throw new NoSuchFieldException(name);
    }
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static void onFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) { action.run(); return; }
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
        assertTrue(done.await(30, TimeUnit.SECONDS)); if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
