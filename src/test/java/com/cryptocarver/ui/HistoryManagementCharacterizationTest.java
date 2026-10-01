package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.HistoryCommand;
import com.cryptocarver.model.HistoryManager;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class HistoryManagementCharacterizationTest {
    @TempDir Path tempDir;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @Test
    void publishedResultCapturesOperationRouteFormatsAndRedactedSecretRecipe() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        SecretVisibilityProfile previous = AppSettings.getInstance().getSecretVisibilityProfile();
        try {
            onFx(() -> {
                try {
                    ModernMainController controller = loadProductionController();
                    useIsolatedHistory(controller, "published");
                    AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.REDACTED);
                    controller.navigateToModule("Symmetric Ciphers");
                    CipherController cipher = field(controller, "cipherContainerController");
                    ((TextField) field(cipher, "symmetricKeyField")).setText("SYNTHETIC-HISTORY-VALUE");
                    ComboBox<String> inputFormat = field(controller, "inputFormatCombo");
                    ComboBox<String> outputFormat = field(controller, "outputFormatCombo");
                    inputFormat.setValue("Hex");
                    outputFormat.setValue("Base64");
                    controller.publish(OperationResult.forOperation("Synthetic cipher result")
                            .detail(OperationDetail.publicDetail("Marker", "SYNTHETIC-RESULT"))
                            .build());
                    controllerRef.set(controller);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
            ModernMainController controller = controllerRef.get();
            HistoryCommand item = controller.getHistoryManager().getHistoryItems().get(0);
            assertEquals("Synthetic cipher result", item.getOperation());
            assertEquals("Symmetric Ciphers", item.getNavigationOperation());
            assertEquals("Hex", item.getInputFormat());
            assertEquals("Base64", item.getOutputFormat());
            assertEquals(HistoryCommand.Reproducibility.REPRODUCIBLE_WITH_SECRETS, item.getReproducibility());
            assertEquals("[REDACTED_SECRET]", item.getParameters().get("CipherController.symmetricKeyField"));
            assertFalse(item.getParameters().containsValue("SYNTHETIC-HISTORY-VALUE"));
            // Empty secret fields of other modules are not reported as redacted secrets.
            assertEquals("", item.getParameters().get("KeysController.keyInputField"));
        } finally {
            onFx(() -> AppSettings.getInstance().setSecretVisibilityProfile(previous));
        }
    }

    @Test
    void historyRecordedBeforeNavigationFallsBackToOperationName() throws Exception {
        AtomicReference<HistoryCommand> itemRef = new AtomicReference<>();
        onFx(() -> {
            try {
                ModernMainController controller = loadProductionController();
                useIsolatedHistory(controller, "fallback");
                setField(controller, "currentActiveOperation", null);
                controller.addToHistory("Synthetic unregistered operation", List.of(
                        OperationDetail.publicDetail("Marker", "SYNTHETIC")));
                itemRef.set(controller.getHistoryManager().getHistoryItems().get(0));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        assertEquals("Synthetic unregistered operation", itemRef.get().getNavigationOperation());
    }

    @Test
    void exportWritesToPathAndHonorsMaskedAndRedactedProfiles() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        onFx(() -> {
            try {
                ModernMainController controller = loadProductionController();
                useIsolatedHistory(controller, "export");
                HistoryCommand item = new HistoryCommand("Synthetic export", "", Map.of());
                item.setStructuredDetails(List.of(OperationDetail.secretDetail(
                        "Synthetic secret field", "SYNTHETIC_EXPORT_SECRET")));
                controller.getHistoryManager().addHistoryItem(item);
                controllerRef.set(controller);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        ModernMainController controller = controllerRef.get();
        for (SecretVisibilityProfile profile : List.of(SecretVisibilityProfile.MASKED, SecretVisibilityProfile.REDACTED)) {
            Path target = tempDir.resolve("history-" + profile + ".json");
            onFx(() -> {
                try { controller.exportHistoryTo(target, profile); }
                catch (Exception exception) { throw new AssertionError(exception); }
            });
            String json = java.nio.file.Files.readString(target);
            assertFalse(json.contains("SYNTHETIC_EXPORT_SECRET"));
        }
    }

    @Test
    void productionFxmlLoadsHistoryLazilyAndSelectionDoesNotRestoreRecipe() throws Exception {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        onFx(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                assertNull(field(controller, "historyViewController"), "history view should still be lazy after main FXML load");
                useIsolatedHistory(controller, "lazy");
                controller.navigateToModule("Hashing");
                GenericController generic = field(controller, "genericContainerController");
                javafx.scene.control.TextArea input = field(generic, "hashInputArea");
                input.setText("SYNTHETIC-KEEP-CURRENT");
                controller.addToHistory("Synthetic hash result", List.of(
                        OperationDetail.publicDetail("Marker", "SYNTHETIC")));
                HistoryCommand item = controller.getHistoryManager().getHistoryItems().get(0);
                Stage stage = new Stage();
                Scene scene = new Scene(root);
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                stageRef.set(stage);
                controller.showRecentHistoryCommand(item);
                HistoryController history = field(controller, "historyViewController");
                assertNotNull(history);
                javafx.scene.control.TableView<HistoryCommand> table = field(history, "historyTable");
                assertEquals(item.getId(), table.getSelectionModel().getSelectedItem().getId());
                assertEquals("SYNTHETIC-KEEP-CURRENT", input.getText());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        onFx(() -> stageRef.get().close());
    }

    @Test
    void reopeningRedactedRecipeNavigatesPromptsAndFocusesFirstSecret() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<HistoryCommand> itemRef = new AtomicReference<>();
        onFx(() -> {
            try {
                FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
                Parent root = loader.load();
                ModernMainController controller = loader.getController();
                useIsolatedHistory(controller, "reopen");
                Stage stage = new Stage();
                Scene scene = new Scene(root);
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                stageRef.set(stage);
                // A redacted field of a hidden module comes first, as in real recipes.
                Map<String, Object> recipe = new java.util.LinkedHashMap<>();
                recipe.put("KeysController.keyInputField", "[REDACTED_SECRET]");
                recipe.put("CipherController.symmetricKeyField", "[REDACTED_SECRET]");
                itemRef.set(new HistoryCommand("Synthetic cipher result", "", recipe,
                        HistoryCommand.Reproducibility.REPRODUCIBLE_WITH_SECRETS,
                        "Synthetic secret redacted", "Hex", "Base64", "Symmetric Ciphers"));
                controllerRef.set(controller);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        onFx(() -> {
            stageRef.get().requestFocus();
            controllerRef.get().reopenHistoryOperation(itemRef.get());
        });
        onFx(() -> { }); // Drain the queued focus request from restoreOperationState.
        ModernMainController controller = controllerRef.get();
        CipherController cipher = field(controller, "cipherContainerController");
        TextField key = field(cipher, "symmetricKeyField");
        assertEquals("", key.getText());
        assertAll(
                () -> assertTrue(((javafx.scene.control.Label) field(controller, "statusLabel")).getAccessibleText()
                        .contains("Re-enter redacted sensitive values"),
                        "status=" + ((javafx.scene.control.Label) field(controller, "statusLabel")).getAccessibleText()),
                () -> assertSame(key, stageRef.get().getScene().getFocusOwner(),
                        "visible=" + key.isVisible() + ", disabled=" + key.isDisabled()
                                + ", traversable=" + key.isFocusTraversable() + ", scene=" + key.getScene()));
        onFx(() -> stageRef.get().close());
    }

    private ModernMainController loadProductionController() throws Exception {
        FXMLLoader loader = Fxml.loader("/fxml/main-view-modern.fxml");
        loader.load();
        return loader.getController();
    }

    private void useIsolatedHistory(ModernMainController controller, String name) throws Exception {
        setField(controller, "historyManager", new HistoryManager(tempDir.resolve(name + ".json")));
    }

    private static <T> T field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void onFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "JavaFX action did not finish");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
