package com.cryptocarver.ui;

import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.OperationSessionLog;
import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.SecretVisibilityProfile;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class SessionTrailUITest {

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void startJavaFx() throws Exception {
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

    @Test
    void productionFxmlPasswordlessSaveAndLoadRestoresTheRedactedTrail() throws Exception {
        assertEquals("true", System.getProperty("test.mode"));
        assertTrue(Path.of(System.getProperty("user.home")).endsWith(Path.of("target", "test-home")));
        SecretVisibilityProfile previousProfile = AppSettings.getInstance().getSecretVisibilityProfile();
        var manager = com.cryptocarver.model.SavedSessionsManager.getInstance();
        var previousIds = manager.getSessions().stream().map(com.cryptocarver.model.SavedSession::getId).toList();
        Path sessionsFile = Path.of(System.getProperty("user.home"), ".cryptocarver", "saved_sessions.json");
        byte[] previousFile = Files.exists(sessionsFile) ? Files.readAllBytes(sessionsFile) : null;
        try {
            runAndWait(() -> {
                javafx.stage.Stage stage = new javafx.stage.Stage();
                try {
                    AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.FULL_LAB);
                    FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                    javafx.scene.Parent root = loader.load();
                    ModernMainController controller = loader.getController();
                    stage.setScene(new javafx.scene.Scene(root, 1100, 800));
                    stage.show();
                    controller.publish(OperationResult.forOperation("Synthetic")
                            .input("invented-ui-input-41".getBytes(StandardCharsets.UTF_8))
                            .output("public-ui-output-41".getBytes(StandardCharsets.UTF_8)).build());
                    controller.saveCurrentResultAsSessionStep("Synthetic UI step", "synthetic");
                    var saveMethod = controller.getClass().getDeclaredMethod("saveSessionCoordinator");
                    saveMethod.setAccessible(true);
                    SaveSessionCoordinator save = (SaveSessionCoordinator) saveMethod.invoke(controller);
                    AtomicReference<String> saveNotice = new AtomicReference<>();
                    AtomicReference<Boolean> encryptedNoticeHidden = new AtomicReference<>();
                    Platform.runLater(() -> {
                        try {
                            javafx.scene.control.DialogPane pane = javafx.stage.Window.getWindows().stream()
                                    .filter(window -> window.isShowing() && window.getScene() != null)
                                    .map(window -> window.getScene().getRoot())
                                    .filter(javafx.scene.control.DialogPane.class::isInstance)
                                    .map(javafx.scene.control.DialogPane.class::cast).findFirst().orElseThrow();
                            javafx.scene.layout.VBox content = (javafx.scene.layout.VBox) pane.getContent();
                            Label notice = (Label) content.getChildren().get(content.getChildren().size() - 1);
                            saveNotice.set(notice.isVisible() && notice.isManaged() ? notice.getText() : "");
                            javafx.scene.control.CheckBox include = (javafx.scene.control.CheckBox) content.getChildren()
                                    .stream().filter(javafx.scene.control.CheckBox.class::isInstance).findFirst().orElseThrow();
                            include.setSelected(true);
                            encryptedNoticeHidden.set(!notice.isVisible() && !notice.isManaged());
                            include.setSelected(false);
                        } finally {
                            clickConfirmation(true);
                        }
                    });
                    save.handleSaveSession();
                    assertEquals(com.cryptocarver.service.I18nService.getInstance().text("savedSessions.redactedTrailNotice"),
                            saveNotice.get());
                    assertEquals(Boolean.TRUE, encryptedNoticeHidden.get());
                    var state = (com.cryptocarver.model.SessionTrailState) field(controller, "sessionTrailState");
                    assertEquals(1, state.size());
                    var stored = manager.getSessions().stream().filter(session -> !previousIds.contains(session.getId()))
                            .findFirst().orElseThrow();
                    assertEquals(1, stored.getOperationLog().size());
                    assertTrue(stored.isTrailRedacted());
                    assertTrue(SessionTrailViewFormatter.preview(stored, com.cryptocarver.service.I18nService.getInstance())
                            .contains(com.cryptocarver.service.I18nService.getInstance().text("sessionTrail.redacted")));
                    SavedSessionsCoordinator saved = field(controller, "savedSessionsCoordinator");
                    var loadMethod = saved.getClass().getDeclaredMethod("previewAndLoad", com.cryptocarver.model.SavedSession.class);
                    loadMethod.setAccessible(true);
                    Platform.runLater(() -> clickConfirmation(true));
                    loadMethod.invoke(saved, stored);
                    assertEquals(1, state.size());
                    assertTrue(state.isTrailRedacted());
                    assertNull(state.steps().get(0).getInputText());
                    assertEquals("public-ui-output-41", state.steps().get(0).getOutputText());
                    Label count = field(controller, "sessionTrailCountLabel");
                    assertTrue(count.getText().contains(com.cryptocarver.service.I18nService.getInstance()
                            .text("sessionTrail.redacted")));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                } finally {
                    stage.close();
                }
            });
        } finally {
            for (var session : manager.getSessions()) {
                if (!previousIds.contains(session.getId())) manager.removeSession(session);
            }
            if (previousFile == null) Files.deleteIfExists(sessionsFile);
            else Files.write(sessionsFile, previousFile);
            runAndWait(() -> AppSettings.getInstance().setSecretVisibilityProfile(previousProfile));
        }
    }

    @Test
    void savedSessionDeleteRequiresConfirmation() throws Exception {
        var constructor = com.cryptocarver.model.SavedSessionsManager.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        var manager = constructor.newInstance(temporaryDirectory.resolve("saved-sessions.json"));
        var session = new com.cryptocarver.model.SavedSession("Synthetic delete test", "Synthetic", java.util.Map.of());
        manager.addSession(session);
        try {
            runAndWait(() -> {
                javafx.scene.layout.VBox list = new javafx.scene.layout.VBox();
                javafx.scene.layout.VBox container = new javafx.scene.layout.VBox(list);
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(container, 800, 400));
                stage.show();
                StatusReporter status = new StatusReporter() {
                    @Override public void updateStatus(String message) { }
                    @Override public void updateInspector(String operation, byte[] input, byte[] output,
                            java.util.List<OperationDetail> details) { }
                    @Override public void showError(String title, String message) { }
                };
                SavedSessionsCoordinator coordinator = new SavedSessionsCoordinator(container, list, status,
                        manager, com.cryptocarver.service.I18nService.getInstance(), new DialogService(),
                        java.util.Map::of, ignored -> { }, ignored -> { }, () -> { }, ignored -> { },
                        new com.cryptocarver.model.SessionTrailState(), () -> "Synthetic", () -> "", () -> list);
                coordinator.show();
                try {
                    javafx.scene.layout.HBox row = (javafx.scene.layout.HBox) list.getChildren().stream()
                            .filter(javafx.scene.layout.HBox.class::isInstance)
                            .filter(node -> ((javafx.scene.layout.HBox) node).getChildren().stream()
                                    .filter(javafx.scene.layout.VBox.class::isInstance)
                                    .map(javafx.scene.layout.VBox.class::cast)
                                    .flatMap(info -> info.getChildren().stream())
                                    .filter(Label.class::isInstance).map(Label.class::cast)
                                    .anyMatch(label -> label.getText().equals(session.getName())))
                            .findFirst().orElseThrow();
                    Button delete = (Button) row.getChildren().get(2);
                    Platform.runLater(() -> clickConfirmation(false));
                    delete.fire();
                    assertTrue(manager.getSessions().stream().anyMatch(saved -> session.getId().equals(saved.getId())));
                    Platform.runLater(() -> clickConfirmation(true));
                    delete.fire();
                    assertFalse(manager.getSessions().stream().anyMatch(saved -> session.getId().equals(saved.getId())));
                    assertTrue(list.getChildren().stream().filter(javafx.scene.layout.HBox.class::isInstance).noneMatch(node ->
                            ((javafx.scene.layout.HBox) node).getChildren().stream().filter(javafx.scene.layout.VBox.class::isInstance)
                                    .map(javafx.scene.layout.VBox.class::cast).flatMap(info -> info.getChildren().stream())
                                    .filter(Label.class::isInstance).map(Label.class::cast)
                                    .anyMatch(label -> label.getText().equals(session.getName()))));
                    stage.close();
                } finally {
                    stage.close();
                }
            });
        } finally {
            manager.removeSession(session);
        }
    }

    private static void clickConfirmation(boolean confirm) {
        javafx.stage.Window.getWindows().stream().filter(javafx.stage.Stage.class::isInstance)
                .map(javafx.stage.Stage.class::cast).filter(javafx.stage.Stage::isShowing)
                .map(javafx.stage.Stage::getScene).filter(java.util.Objects::nonNull)
                .map(javafx.scene.Scene::getRoot).filter(javafx.scene.control.DialogPane.class::isInstance)
                .map(javafx.scene.control.DialogPane.class::cast).findFirst().ifPresent(pane -> {
                    javafx.scene.control.ButtonType type = pane.getButtonTypes().stream()
                            .filter(button -> confirm
                                    ? button.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE
                                    : button.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)
                            .findFirst().orElseThrow();
                    ((Button) pane.lookupButton(type)).fire();
                });
    }

    @Test
    void clearTrailButtonConfirmsAndReturnsToDisabledState() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                javafx.scene.Parent root = loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(root, 1920, 1000));
                stage.show();
                Button clear = field(controller, "inspectorClearSessionTrailButton");
                assertTrue(clear.isDisabled());
                controller.publish(OperationResult.forOperation("Synthetic operation").build());
                controller.saveCurrentResultAsSessionStep("Synthetic step", "");
                assertFalse(clear.isDisabled());
                Platform.runLater(() -> javafx.stage.Window.getWindows().stream()
                        .filter(javafx.stage.Stage.class::isInstance)
                        .map(javafx.stage.Stage.class::cast)
                        .filter(javafx.stage.Stage::isShowing)
                        .map(javafx.stage.Stage::getScene)
                        .filter(java.util.Objects::nonNull)
                        .map(javafx.scene.Scene::getRoot)
                        .filter(javafx.scene.control.DialogPane.class::isInstance)
                        .map(javafx.scene.control.DialogPane.class::cast)
                        .findFirst()
                        .ifPresent(pane -> ((javafx.scene.control.Button) pane.lookupButton(javafx.scene.control.ButtonType.OK)).fire()));
                clear.fire();
                assertEquals(0, ((com.cryptocarver.model.SessionTrailState) field(controller, "sessionTrailState")).size());
                assertTrue(clear.isDisabled());
                stage.close();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void productionFxmlGivesSessionTrailPositionLabelVisibleWidth() throws Exception {
        AtomicReference<Label> positionRef = new AtomicReference<>();
        AtomicReference<javafx.stage.Stage> stageRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                javafx.scene.Parent root = loader.load();
                ModernMainController controller = loader.getController();
                javafx.stage.Stage stage = new javafx.stage.Stage();
                javafx.scene.Scene scene = new javafx.scene.Scene(root, 1280, 800);
                scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                scene.getStylesheets().add(getClass().getResource("/css/theme-light.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                controller.publish(OperationResult.forOperation("Synthetic operation").build());
                controller.saveCurrentResultAsSessionStep("Synthetic first", "");
                controller.publish(OperationResult.forOperation("Synthetic second operation").build());
                controller.saveCurrentResultAsSessionStep("Synthetic second", "");
                root.applyCss();
                root.layout();
                positionRef.set(field(controller, "sessionTrailPositionLabel"));
                stageRef.set(stage);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        try {
            assertTrue(positionRef.get().getWidth() > 0, "position label should be laid out with visible width; label="
                    + positionRef.get().getWidth() + ", parent=" + positionRef.get().getParent().getBoundsInLocal());
            assertTrue(positionRef.get().getText().contains("2/2"));
            // The generic `.label` rule used to paint the text with the header's own colour.
            javafx.scene.paint.Paint background = ((javafx.scene.layout.Region) positionRef.get().getParent())
                    .getBackground().getFills().get(0).getFill();
            assertNotEquals(background, positionRef.get().getTextFill());
        } finally {
            runAndWait(() -> stageRef.get().close());
        }
    }

    @Test
    void savesLatestPublishedResultRendersItAndExportsTheTrail() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
                controller.navigateTo("MAC");
                AuthenticationController authentication = field(controller, "authenticationContainerController");
                TextField macKey = field(authentication, "authMacKeyField");
                TextArea macInput = field(authentication, "authInputArea");
                macKey.setText("00112233445566778899AABBCCDDEEFF");
                macInput.setText("MESSAGE-IN-THE-SCREEN");
                controller.publish(OperationResult.forOperation("Calculate MAC")
                        .input("SECRET-KEY".getBytes(StandardCharsets.UTF_8))
                        .output("A1B2C3D4".getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET)
                        .detail(OperationDetail.sensitiveDetail("Key", "CLEAR-TEXT-DETAIL"))
                        .status("Completed")
                        .build());
                controller.saveCurrentResultAsSessionStep("Laboratory MAC", "mac, review");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });

        ModernMainController controller = controllerRef.get();
        OperationSessionLog log = ((com.cryptocarver.model.SessionTrailState) field(controller, "sessionTrailState")).log();
        Label count = field(controller, "sessionTrailCountLabel");
        Button add = field(controller, "inspectorAddSessionStepButton");
        Button export = field(controller, "inspectorExportSessionTrailButton");
        assertEquals(1, log.size());
        assertTrue(count.getText().contains("1"));
        assertFalse(add.isDisabled());
        assertFalse(export.isDisabled());

        Path report = temporaryDirectory.resolve("trail.txt");
        runAndWait(() -> {
            try {
                controller.exportSessionTrail(report);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        String exported = Files.readString(report);
        assertTrue(exported.contains("[1] Laboratory MAC"));
        assertTrue(exported.contains("Tags: mac, review"));
        assertTrue(exported.contains("SECRET-KEY"));
        assertTrue(exported.contains("CLEAR-TEXT-DETAIL"));
        assertTrue(exported.contains("00112233445566778899AABBCCDDEEFF"));
        assertTrue(exported.contains("MESSAGE-IN-THE-SCREEN"));
        assertTrue(exported.contains("UNSAFE CLEAR-TEXT"));
    }

    @Test
    void inspectorNavigatesSavedOperationsAndReturnsToNewResult() throws Exception {
        SecretVisibilityProfile previousProfile = AppSettings.getInstance().getSecretVisibilityProfile();
        try {
            runAndWait(() -> {
                AppSettings.getInstance().setSecretVisibilityProfile(SecretVisibilityProfile.MASKED);
                try {
                    FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                    loader.load();
                    ModernMainController controller = loader.getController();
                    controller.navigateTo("MAC");
                    controller.publish(OperationResult.forOperation("First operation")
                            .output(new byte[]{1})
                            .detail(OperationDetail.sensitiveDetail("Marker", "FIRST-SECRET"))
                            .build());
                    controller.saveCurrentResultAsSessionStep("First step", "");
                    controller.publish(OperationResult.forOperation("Second operation")
                            .output(new byte[]{1, 2, 3})
                            .detail(OperationDetail.publicDetail("Marker", "SECOND"))
                            .build());
                    controller.saveCurrentResultAsSessionStep("Second step", "");

                    Button previous = field(controller, "inspectorPreviousSessionStepButton");
                    Button next = field(controller, "inspectorNextSessionStepButton");
                    Label position = field(controller, "sessionTrailPositionLabel");
                    Label operation = field(controller, "operationLabel");
                    Label outputBytes = field(controller, "outputBytesLabel");
                    assertTrue(position.getText().contains("2/2"));
                    assertEquals("Second operation", operation.getText());
                    assertEquals("3", outputBytes.getText());
                    assertFalse(previous.isDisabled());
                    assertTrue(next.isDisabled());

                    previous.fire();
                    assertTrue(position.getText().contains("1/2"));
                    assertEquals("First operation", operation.getText());
                    assertEquals("1", outputBytes.getText());
                    assertTrue(previous.isDisabled());
                    assertTrue(inspectorText(field(controller, "inspectorDetailsContainer")).contains("***MASKED***"));
                    assertFalse(inspectorText(field(controller, "inspectorDetailsContainer")).contains("FIRST-SECRET"));

                    next.fire();
                    assertEquals("Second operation", operation.getText());
                    controller.publish(OperationResult.forOperation("Current operation")
                            .output(new byte[]{4, 5}).build());
                    assertEquals("Current operation", operation.getText());
                    previous.fire();
                    assertEquals("Second operation", operation.getText());
                    next.fire();
                    assertEquals("Current operation", operation.getText());
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        } finally {
            runAndWait(() -> AppSettings.getInstance().setSecretVisibilityProfile(previousProfile));
        }
    }

    @Test
    void trailSaveOpenExportAndClearKeepCurrentProfileBehavior() throws Exception {
        SecretVisibilityProfile previousProfile = AppSettings.getInstance().getSecretVisibilityProfile();
        try {
            for (SecretVisibilityProfile profile : SecretVisibilityProfile.values()) {
                AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
                runAndWait(() -> {
                    AppSettings.getInstance().setSecretVisibilityProfile(profile);
                    try {
                        FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                        loader.load();
                        ModernMainController controller = loader.getController();
                        controllerRef.set(controller);
                        controller.navigateTo("MAC");
                        AuthenticationController module = field(controller, "authenticationContainerController");
                        ((TextField) field(module, "authMacKeyField")).setText("0123456789ABCDEFFEDCBA9876543210");
                        ((TextArea) field(module, "authInputArea")).setText("CHARACTERIZATION-INPUT");
                        controller.publish(OperationResult.forOperation("Characterized operation")
                                .input("CHARACTERIZATION-INPUT".getBytes(StandardCharsets.UTF_8))
                                .output("CHARACTERIZATION-OUTPUT".getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET)
                                .detail(OperationDetail.sensitiveDetail("Synthetic field", "CHARACTERIZATION-DETAIL"))
                                .build());
                        controller.saveCurrentResultAsSessionStep("Characterized step", "profile, trail");
                        ((Button) field(controller, "inspectorOpenSessionStepButton")).fire();
                        controller.exportSessionTrail(temporaryDirectory.resolve("trail-" + profile + ".txt"));
                        Platform.runLater(() -> javafx.stage.Window.getWindows().stream()
                                .filter(javafx.stage.Stage.class::isInstance)
                                .map(javafx.stage.Stage.class::cast)
                                .filter(javafx.stage.Stage::isShowing)
                                .map(stage -> stage.getScene() == null ? null : stage.getScene().getRoot())
                                .filter(javafx.scene.control.DialogPane.class::isInstance)
                                .map(javafx.scene.control.DialogPane.class::cast)
                                .findFirst()
                                .ifPresent(pane -> pane.lookupButton(javafx.scene.control.ButtonType.OK)
                                        .fireEvent(new javafx.event.ActionEvent())));
                        controller.handleClearSessionTrail();
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                });
                ModernMainController controller = controllerRef.get();
                OperationSessionLog log = ((com.cryptocarver.model.SessionTrailState) field(controller, "sessionTrailState")).log();
                assertEquals(0, log.size());
                String exported = Files.readString(temporaryDirectory.resolve("trail-" + profile + ".txt"));
                assertTrue(exported.contains("[1] Characterized step"));
                assertTrue(exported.contains("Tags: profile, trail"));
                assertTrue(exported.contains("CHARACTERIZATION-DETAIL"));
                assertTrue(exported.contains("0123456789ABCDEFFEDCBA9876543210"));
                assertTrue(exported.contains("CHARACTERIZATION-INPUT"));
                assertTrue(exported.contains("UNSAFE CLEAR-TEXT"));
            }
        } finally {
            runAndWait(() -> AppSettings.getInstance().setSecretVisibilityProfile(previousProfile));
        }
    }

    @Test
    void productionFxmlLazyModuleStateIsReadWhenSavingAfterCoordinatorConstruction() throws Exception {
        AtomicReference<ModernMainController> controllerRef = new AtomicReference<>();
        runAndWait(() -> {
            try {
                FXMLLoader loader = UiTestFxml.productionLoader("/fxml/main-view-modern.fxml");
                loader.load();
                ModernMainController controller = loader.getController();
                controllerRef.set(controller);
                assertNotNull(field(controller, "sessionTrailCoordinator"));
                var walletControllerField = controller.getClass().getDeclaredField("walletController");
                walletControllerField.setAccessible(true);
                walletControllerField.set(controller, null);
                assertNull(walletControllerField.get(controller));
                controller.navigateTo("SD-JWT VC");
                WalletController module = field(controller, "walletController");
                assertNotNull(module);
                ((TextArea) field(module, "sdJwtClaimsArea")).setText("LAZY-MODULE-CAPTURE");
                controller.publish(OperationResult.forOperation("Lazy module operation")
                        .output(new byte[]{7}, OperationDetail.Classification.SECRET).build());
                controller.saveCurrentResultAsSessionStep("Lazy module step", "lazy");
            } catch (Exception exception) { throw new AssertionError(exception); }
        });
        ModernMainController controller = controllerRef.get();
        com.cryptocarver.model.SessionTrailState state = field(controller, "sessionTrailState");
        assertTrue(state.steps().get(0).getParameters().keySet().stream().anyMatch(key -> key.contains("WalletController.sdJwtClaimsArea")),
                () -> state.steps().get(0).getParameters().keySet().toString());
        Path report = temporaryDirectory.resolve("lazy-module-trail.txt");
        runAndWait(() -> {
            try { controller.exportSessionTrail(report); }
            catch (Exception exception) { throw new AssertionError(exception); }
        });
        String exported = Files.readString(report);
        assertTrue(exported.contains("LAZY-MODULE-CAPTURE"));
    }

    @Test
    void clearOutputKeepsTheCurrentInput() throws Exception {
        runAndWait(() -> {
            try {
                FXMLLoader loader = UiTestFxml.loader(getClass().getResource("/fxml/main-view-modern.fxml"));
                loader.load();
                ModernMainController controller = loader.getController();
                controller.navigateTo("MAC");
                AuthenticationController authentication = field(controller, "authenticationContainerController");
                TextArea input = field(authentication, "authInputArea");
                TextArea output = field(authentication, "authOutputArea");
                input.setText("KEEP-THIS-INPUT");
                output.setText("REMOVE-THIS-OUTPUT");
                controller.publish(OperationResult.forOperation("Calculate MAC")
                        .output("REMOVE-THIS-OUTPUT".getBytes(StandardCharsets.UTF_8)).build());

                javafx.scene.control.MenuItem clearOutput = field(controller, "clearOutputMenuItem");
                clearOutput.fire();

                assertEquals("KEEP-THIS-INPUT", input.getText());
                assertEquals("", output.getText());
                assertNull(field(controller, "lastPublishedResultSnapshot"));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private static String inspectorText(javafx.scene.layout.VBox container) {
        StringBuilder text = new StringBuilder();
        for (javafx.scene.Node row : container.getChildren()) {
            if (row instanceof javafx.scene.layout.VBox box) {
                for (javafx.scene.Node child : box.getChildren()) {
                    if (child instanceof Label label) text.append(label.getText());
                }
            }
        }
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object instance, String name) throws Exception {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
    }

    private static void runAndWait(Runnable action) throws Exception {
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
        if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("JavaFX action timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
