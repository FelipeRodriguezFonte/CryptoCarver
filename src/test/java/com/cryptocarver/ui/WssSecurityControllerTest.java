package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class WssSecurityControllerTest {

    @BeforeAll static void startToolkit() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException ignored) { }
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }

    private static void runOnFxThread(ThrowingRunnable work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { work.run(); } catch (Throwable t) { failure.set(t); } finally { done.countDown(); } });
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX test timed out");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void testUiElementsAreLoaded() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/wss_security.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            WssSecurityController controller = loader.getController();

            assertNotNull(controller);

            Field signInput = WssSecurityController.class.getDeclaredField("wssSignInputArea");
            signInput.setAccessible(true);
            assertNotNull(signInput.get(controller), "Sign input area should be injected");

            Field algoCombo = WssSecurityController.class.getDeclaredField("wssSignAlgorithmCombo");
            algoCombo.setAccessible(true);
            assertNotNull(algoCombo.get(controller), "Algorithm combo should be injected");

            Field includeTimestamp = WssSecurityController.class.getDeclaredField("wssIncludeTimestampCheck");
            includeTimestamp.setAccessible(true);
            CheckBox includeTimestampCheck = (CheckBox) includeTimestamp.get(controller);
            Field timestampValidity = WssSecurityController.class.getDeclaredField("wssTimestampValiditySpinner");
            timestampValidity.setAccessible(true);
            Spinner<?> validitySpinner = (Spinner<?>) timestampValidity.get(controller);
            Field signTimestamp = WssSecurityController.class.getDeclaredField("wssSignTimestampCheck");
            signTimestamp.setAccessible(true);
            CheckBox signTimestampCheck = (CheckBox) signTimestamp.get(controller);
            assertTrue(validitySpinner.isDisabled());
            assertTrue(signTimestampCheck.isDisabled());
            includeTimestampCheck.setSelected(true);
            assertFalse(validitySpinner.isDisabled());
            assertFalse(signTimestampCheck.isDisabled());

            Field keyPath = WssSecurityController.class.getDeclaredField("wssSignKeyPathField");
            keyPath.setAccessible(true);
            assertNotNull(keyPath.get(controller), "Key path field should be injected");

            Field verifyInput = WssSecurityController.class.getDeclaredField("wssVerifyInputArea");
            verifyInput.setAccessible(true);
            assertNotNull(verifyInput.get(controller), "Verify input area should be injected");

            Field usernameType = WssSecurityController.class.getDeclaredField("wssUsernamePasswordTypeCombo");
            usernameType.setAccessible(true);
            @SuppressWarnings("unchecked")
            ComboBox<String> usernameTypeCombo = (ComboBox<String>) usernameType.get(controller);
            Field usernameWarning = WssSecurityController.class.getDeclaredField("wssUsernameWarningLabel");
            usernameWarning.setAccessible(true);
            Label warning = (Label) usernameWarning.get(controller);
            assertEquals("PasswordDigest", usernameTypeCombo.getValue());
            assertTrue(warning.getText().contains("SHA-1 digest formula"));
            usernameTypeCombo.setValue("PasswordText");
            assertTrue(warning.getText().contains("exposes the password"));
            assertTrue(warning.getStyle().contains("#b71c1c"));

            Field usernameMaxAge = WssSecurityController.class.getDeclaredField("wssUsernameMaxAgeSpinner");
            usernameMaxAge.setAccessible(true);
            Spinner<?> maxAge = (Spinner<?>) usernameMaxAge.get(controller);
            assertEquals(300, maxAge.getValue());

            Field encryptionAlgorithm = WssSecurityController.class.getDeclaredField("wssEncryptDataAlgorithmCombo");
            encryptionAlgorithm.setAccessible(true);
            @SuppressWarnings("unchecked")
            ComboBox<String> encryptionCombo = (ComboBox<String>) encryptionAlgorithm.get(controller);
            Field encryptionWarning = WssSecurityController.class.getDeclaredField("wssEncryptAlgorithmWarningLabel");
            encryptionWarning.setAccessible(true);
            Label encryptionWarningLabel = (Label) encryptionWarning.get(controller);
            assertEquals("AES-256-GCM", encryptionCombo.getValue());
            assertTrue(encryptionWarningLabel.getText().contains("authenticated encryption"));
            encryptionCombo.setValue("AES-256-CBC");
            assertTrue(encryptionWarningLabel.getText().contains("does not authenticate"));
            assertTrue(encryptionWarningLabel.getStyle().contains("#b71c1c"));

            Field keyTransport = WssSecurityController.class.getDeclaredField("wssEncryptKeyTransportCombo");
            keyTransport.setAccessible(true);
            @SuppressWarnings("unchecked")
            ComboBox<String> keyTransportCombo = (ComboBox<String>) keyTransport.get(controller);
            assertEquals("RSA-OAEP SHA-256", keyTransportCombo.getValue());
        });
    }
}
