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
        OperationSessionLog log = field(controller, "operationSessionLog");
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
