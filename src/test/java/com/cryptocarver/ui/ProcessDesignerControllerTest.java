package com.cryptocarver.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Tag("ui")
@EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ProcessDesignerControllerTest {

    @BeforeAll static void startToolkit() {
        try { Platform.startup(() -> { }); } catch (IllegalStateException ignored) { }
    }

    private com.cryptocarver.model.SecretVisibilityProfile originalProfile;

    @org.junit.jupiter.api.BeforeEach
    void setUpProfile() {
        originalProfile = com.cryptocarver.model.AppSettings.getInstance().getSecretVisibilityProfile();
        com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(com.cryptocarver.model.SecretVisibilityProfile.FULL_LAB);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownProfile() {
        if (originalProfile != null) {
            com.cryptocarver.model.AppSettings.getInstance().setSecretVisibilityProfile(originalProfile);
        }
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
    void contextualInspectorVisibilityIsCorrect() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            ProcessDesignerController controller = loader.getController();

            // Console Input -> should show consoleValueFieldGroup and charsetFieldGroup
            controller.handleAddConsoleInput();
            controller.select(controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1));
            VBox consoleValueGroup = controller.getInspectorGroup("value");
            VBox charsetGroup = controller.getInspectorGroup("charset");
            VBox hashGroup = controller.getInspectorGroup("algorithm");

            assertTrue(consoleValueGroup != null && consoleValueGroup.isVisible() && consoleValueGroup.isManaged());
            assertTrue(charsetGroup != null && charsetGroup.isVisible() && charsetGroup.isManaged());
            assertTrue(hashGroup == null || (!hashGroup.isVisible() && !hashGroup.isManaged()));

            // Hash Node -> should show only Hash
            controller.handleAddHash();
            controller.select(controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1));
            VBox consoleValueGroup2 = controller.getInspectorGroup("value");
            VBox charsetGroup2 = controller.getInspectorGroup("charset");
            VBox hashGroup2 = controller.getInspectorGroup("algorithm");
            assertTrue(consoleValueGroup2 == null || (!consoleValueGroup2.isVisible() && !consoleValueGroup2.isManaged()));
            assertTrue(charsetGroup2 == null || (!charsetGroup2.isVisible() && !charsetGroup2.isManaged()));
            assertTrue(hashGroup2 != null && hashGroup2.isVisible() && hashGroup2.isManaged());
        });
    }

    @Test
    void deleteSelectedRemovesConnectionBetweenTwoSelectedNodesBeforeDeletingNodes() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            loader.load();
            ProcessDesignerController controller = loader.getController();
            var definition = controller.toDefinition();
            var input = definition.nodes.get(0);
            var hash = definition.nodes.get(1);
            controller.select(input);
            controller.select(hash);

            controller.handleDeleteSelected();

            org.junit.jupiter.api.Assertions.assertEquals(3, controller.toDefinition().nodes.size());
            org.junit.jupiter.api.Assertions.assertEquals(1, controller.toDefinition().connections.size());
            org.junit.jupiter.api.Assertions.assertFalse(controller.toDefinition().connections.stream()
                    .anyMatch(connection -> input.id.equals(connection.from) && hash.id.equals(connection.to)));
        });
    }

    @Test
    void addsBase64UrlNodes() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            ProcessDesignerController controller = loader.getController();
            controller.handleAddBase64UrlEncode();
            controller.handleAddBase64UrlDecode();

            com.cryptocarver.model.process.ProcessDefinition def = controller.toDefinition();
            assertTrue(def.nodes.stream().anyMatch(n -> "BASE64URL_ENCODE".equals(n.type)));
            assertTrue(def.nodes.stream().anyMatch(n -> "BASE64URL_DECODE".equals(n.type)));
        });
    }

    @Test
    void nodeNameCanBeEditedWithoutChangingItsOperation() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            loader.load();
            ProcessDesignerController controller = loader.getController();
            controller.handleAddEncrypt();
            var encrypt = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(encrypt);
            controller.nodeNameField.setText("Encrypt customer payload");
            controller.handleSaveNodeSettings();

            org.junit.jupiter.api.Assertions.assertEquals("Encrypt customer payload", encrypt.label);
            org.junit.jupiter.api.Assertions.assertEquals("AES/GCM/NoPadding", encrypt.configuration.get("algorithm"));
        });
    }

    @Test
    void presetsLoadEditableAndCryptographicExamplesExecute() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            loader.load();
            ProcessDesignerController controller = loader.getController();

            controller.handleLoadAesGcmRoundTripPreset();
            var roundTrip = controller.toDefinition();
            org.junit.jupiter.api.Assertions.assertEquals("AES-GCM encrypt and decrypt", roundTrip.name);
            org.junit.jupiter.api.Assertions.assertEquals(7, roundTrip.nodes.size());
            org.junit.jupiter.api.Assertions.assertEquals(8, roundTrip.connections.size());
            var recovered = com.cryptocarver.model.process.ProcessEngine.execute(roundTrip).get("output");
            org.junit.jupiter.api.Assertions.assertEquals("Hello, CryptoForge", recovered.render());

            controller.handleLoadAesCmacPreset();
            var cmac = controller.toDefinition();
            org.junit.jupiter.api.Assertions.assertEquals("AES-CMAC", cmac.name);
            org.junit.jupiter.api.Assertions.assertEquals(4, cmac.nodes.size());
            org.junit.jupiter.api.Assertions.assertEquals(16,
                    com.cryptocarver.model.process.ProcessEngine.execute(cmac).get("mac").bytes().length);
        });
    }

    @Test
    void phase3EncryptUIAndExecution() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            ProcessDesignerController controller = loader.getController();

            // Select Encrypt
            controller.handleAddEncrypt();
            com.cryptocarver.model.process.ProcessDefinition.Node encryptNode = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(encryptNode);

            // Confirm fields appear
            assertTrue(controller.getInspectorGroup("algorithm") != null && controller.getInspectorGroup("algorithm").isVisible());
            assertTrue(controller.getInspectorGroup("keyFormat") != null && controller.getInspectorGroup("keyFormat").isVisible());
            assertTrue(controller.getInspectorGroup("key") != null && controller.getInspectorGroup("key").isVisible());
            assertTrue(controller.getInspectorGroup("nonce") != null && controller.getInspectorGroup("nonce").isVisible());

            // Introduce key in Hex, save, and check configuration
            ((ComboBox<String>) controller.getInspectorControl("keyFormat")).setValue("HEX");
            ((TextInputControl) controller.getInspectorControl("key")).setText("00112233445566778899aabbccddeeff");
            ((TextInputControl) controller.getInspectorControl("nonce")).setText("000000000000000000000000");
            controller.handleSaveNodeSettings();
            assertTrue("HEX".equals(encryptNode.configuration.get("keyFormat")));
            org.junit.jupiter.api.Assertions.assertNull(encryptNode.configuration.get("key"), "Sensitive parameter 'key' must not be in configuration");
            org.junit.jupiter.api.Assertions.assertArrayEquals("00112233445566778899aabbccddeeff".toCharArray(), controller.getTransientSecret(encryptNode.id, "key"));

            // Repeat with Base64
            ((ComboBox<String>) controller.getInspectorControl("keyFormat")).setValue("BASE64");
            ((TextInputControl) controller.getInspectorControl("key")).setText("ABEiM0RVZneImaq7zN3u/w==");
            controller.handleSaveNodeSettings();
            assertTrue("BASE64".equals(encryptNode.configuration.get("keyFormat")));
            org.junit.jupiter.api.Assertions.assertNull(encryptNode.configuration.get("key"), "Sensitive parameter 'key' must not be in configuration");
            org.junit.jupiter.api.Assertions.assertArrayEquals("ABEiM0RVZneImaq7zN3u/w==".toCharArray(), controller.getTransientSecret(encryptNode.id, "key"));

            controller.select(encryptNode);
            ((ComboBox<String>) controller.getInspectorControl("algorithm")).setValue("AES/CBC/PKCS7Padding");
            controller.handleSaveNodeSettings();
            assertTrue("AES/CBC/PKCS7Padding".equals(encryptNode.configuration.get("algorithm")));

            // Note: End-to-end execution testing is covered by ProcessEngineTest.
            // We just verify the UI binds correctly to the configuration here.
        });
    }

    @Test
    void phase3VerifyUIMultiPort() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            ProcessDesignerController controller = loader.getController();

            controller.handleClearCanvas();

            // Add Verify node and two others
            controller.handleAddVerify();
            com.cryptocarver.model.process.ProcessDefinition.Node verifyNode = controller.toDefinition().nodes.get(0);

            controller.handleAddConsoleInput(); // for payload
            com.cryptocarver.model.process.ProcessDefinition.Node payloadNode = controller.toDefinition().nodes.get(1);

            controller.handleAddConsoleInput(); // for signature
            com.cryptocarver.model.process.ProcessDefinition.Node sigNode = controller.toDefinition().nodes.get(2);

            // Select payload -> verify
            controller.select(payloadNode);
            controller.select(verifyNode);

            // Confirm menu appears
            assertTrue(controller.connectMenuButton != null);
            // In headless tests, isVisible might depend on scene layout, but we can check items
            // However, we just check connectMenuButton is managed

            java.lang.reflect.Method m = ProcessDesignerController.class.getDeclaredMethod("connectToPort", String.class);
            m.setAccessible(true);

            // Connect to payload
            m.invoke(controller, "payload");
            com.cryptocarver.model.process.ProcessDefinition def = controller.toDefinition();
            assertTrue(def.connections.size() == 1);
            assertTrue("payload".equals(def.connections.get(0).targetPort));

            // Select signature -> verify
            controller.selectedNodeIds.clear();
            controller.select(sigNode);
            controller.select(verifyNode);

            // Connect to signature
            m.invoke(controller, "signature");
            def = controller.toDefinition();
            assertTrue(def.connections.size() == 2);
            assertTrue("signature".equals(def.connections.get(1).targetPort));

            // Trying to reuse an occupied port does not overwrite
            controller.selectedNodeIds.clear();
            controller.select(payloadNode);
            controller.select(verifyNode);
            m.invoke(controller, "signature"); // trying to connect payloadNode to signature port, which is occupied
            def = controller.toDefinition();
            assertTrue(def.connections.size() == 2); // Still 2
            assertTrue(def.connections.get(1).from.equals(sigNode.id)); // Not overwritten by payloadNode
        });
    }

    @Test
    void connectsOperationToOutputWhenSelectionOrderIsReversed() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            new Scene(root);
            ProcessDesignerController controller = loader.getController();
            controller.handleClearCanvas();
            controller.handleAddEncrypt();
            controller.handleAddConsoleOutput();
            com.cryptocarver.model.process.ProcessDefinition.Node encrypt = controller.toDefinition().nodes.get(0);
            com.cryptocarver.model.process.ProcessDefinition.Node output = controller.toDefinition().nodes.get(1);

            // The output is selected first, as in the reported UI state.
            controller.select(output);
            controller.select(encrypt);
            java.lang.reflect.Method connect = ProcessDesignerController.class.getDeclaredMethod("connectToPort", String.class);
            connect.setAccessible(true);
            connect.invoke(controller, "input");

            com.cryptocarver.model.process.ProcessDefinition.Connection connection = controller.toDefinition().connections.get(0);
            assertTrue(encrypt.id.equals(connection.from));
            assertTrue(output.id.equals(connection.to));
            assertTrue("input".equals(connection.targetPort));
        });
    }

    @Test
    void gcmInspectorAndConnectionMenuExposeAadPort() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            new Scene(root);
            ProcessDesignerController controller = loader.getController();
            controller.handleClearCanvas();
            controller.handleAddConsoleInput();
            controller.handleAddEncrypt();
            com.cryptocarver.model.process.ProcessDefinition.Node aadSource = controller.toDefinition().nodes.get(0);
            com.cryptocarver.model.process.ProcessDefinition.Node encrypt = controller.toDefinition().nodes.get(1);

            controller.select(encrypt);
            controller.select(aadSource);
            controller.select(encrypt);
            assertTrue(controller.connectMenuButton.getItems().stream().anyMatch(item -> item.getText().equals("Connect to aad")));
            aadSource.configuration.put("value", "header-v1");
            java.lang.reflect.Method connect = ProcessDesignerController.class.getDeclaredMethod("connectToPort", String.class);
            connect.setAccessible(true);
            connect.invoke(controller, "aad");
            controller.select(encrypt);
            org.junit.jupiter.api.Assertions.assertEquals(1, controller.toDefinition().connections.size());
            org.junit.jupiter.api.Assertions.assertEquals("aad", controller.toDefinition().connections.get(0).targetPort);
            java.lang.reflect.Method selectConnection = ProcessDesignerController.class.getDeclaredMethod("selectConnection", com.cryptocarver.model.process.ProcessDefinition.Connection.class);
            selectConnection.setAccessible(true);
            selectConnection.invoke(controller, controller.toDefinition().connections.get(0));
            controller.handleDeleteSelected();
            assertTrue(controller.toDefinition().connections.isEmpty());

            controller.select(encrypt);
            ((ComboBox<String>) controller.getInspectorControl("algorithm")).setValue("AES/CBC/PKCS7Padding");
            controller.handleSaveNodeSettings();
            controller.select(aadSource);
            controller.select(encrypt);
            assertTrue(controller.connectMenuButton.getItems().stream().noneMatch(item -> item.getText().equals("Connect to aad")));
        });
    }

    @Test
    void phase35_fileInputModeTogglesCharsetVisibility() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            ProcessDesignerController controller = loader.getController();

            controller.handleAddFileInput();
            com.cryptocarver.model.process.ProcessDefinition.Node node = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(node);

            // By default FILE_INPUT is BINARY
            assertTrue(controller.getInspectorGroup("charset") == null || !controller.getInspectorGroup("charset").isVisible());

            // Change to TEXT
            ((ComboBox<String>) controller.getInspectorControl("readMode")).setValue("TEXT");
            assertTrue(controller.getInspectorGroup("charset") != null && controller.getInspectorGroup("charset").isVisible());

            // Change back to BINARY
            ((ComboBox<String>) controller.getInspectorControl("readMode")).setValue("BINARY");
            assertTrue(controller.getInspectorGroup("charset") == null || !controller.getInspectorGroup("charset").isVisible());
        });
    }

    @Test
    void phase35_observabilityTablePopulatesOnExecution() throws Exception {
        AtomicReference<ProcessDesignerController> ctrlRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        runOnFxThread(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                Parent root = loader.load();
                ProcessDesignerController controller = loader.getController();
                ctrlRef.set(controller);

                controller.onExecutionFinished = latch::countDown;

                // ProcessDesigner defaults to: Console input -> Hash -> Console output
                // Let's clear and create our own: Console Input -> Hex Encode -> Console Output
                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                com.cryptocarver.model.process.ProcessDefinition.Node in = controller.toDefinition().nodes.get(0);

                controller.handleAddHexEncode();
                com.cryptocarver.model.process.ProcessDefinition.Node hex = controller.toDefinition().nodes.get(1);

                // Add console output via a reflection hack or adding the method if needed.
                // Wait, ProcessDesignerController doesn't have handleAddConsoleOutput.
                // Let's just create a Hash node, since we can't easily add a console output via UI button.
                // No, the user explicitly asked for "Console input -> Hex encode -> Console output".
                // I will use addNode via reflection to add it.
                java.lang.reflect.Method m = ProcessDesignerController.class.getDeclaredMethod("addNode", String.class, String.class, double.class, double.class);
                m.setAccessible(true);
                com.cryptocarver.model.process.ProcessDefinition.Node out = (com.cryptocarver.model.process.ProcessDefinition.Node) m.invoke(controller, "CONSOLE_OUTPUT", "Console output", 0, 0);

                controller.select(in);
                controller.select(hex);
                controller.handleConnectSelected();

                controller.select(hex);
                controller.select(out);
                controller.handleConnectSelected();

                // Set value
                controller.select(in);
                ((TextInputControl) controller.getInspectorControl("value")).setText("test");
                controller.handleSaveNodeSettings();

                // Execute
                controller.handleRunProcess();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        runOnFxThread(() -> {
            ProcessDesignerController controller = ctrlRef.get();
            // Assert Table
            var items = controller.executionStatusTable.getItems();
            org.junit.jupiter.api.Assertions.assertEquals(3, items.size());
            org.junit.jupiter.api.Assertions.assertEquals("Console input", items.get(0).getStepName());
            org.junit.jupiter.api.Assertions.assertEquals("Hex encode", items.get(1).getStepName());
            org.junit.jupiter.api.Assertions.assertEquals("Console output", items.get(2).getStepName());
            // Verify the TableView can render the model, not merely retain rows internally.
            @SuppressWarnings("unchecked")
            javafx.scene.control.TableColumn<ProcessExecutionRow, String> visualStepColumn =
                    (javafx.scene.control.TableColumn<ProcessExecutionRow, String>) controller.executionStatusTable.getColumns().get(0);
            org.junit.jupiter.api.Assertions.assertEquals("2", visualStepColumn.getCellObservableValue(items.get(1)).getValue());

            for (var row : items) {
                org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", row.getStatus());
                org.junit.jupiter.api.Assertions.assertFalse(row.getDuration().isEmpty());
            }

            org.junit.jupiter.api.Assertions.assertTrue(items.get(1).getInput().contains("TEXT"));
            org.junit.jupiter.api.Assertions.assertTrue(items.get(1).getOutput().contains("HEX"));

            // Assert Trace
            String trace = controller.executionOutputArea.getText();
            assertTrue(trace.contains("[1] Console input · CONSOLE_INPUT"));
            assertTrue(trace.contains("[2] Hex encode · HEX_ENCODE"));
            assertTrue(trace.contains("[3] Console output · CONSOLE_OUTPUT"));
            assertTrue(trace.contains("output: HEX"));
            assertTrue(trace.contains("value: 74657374"));
        });
    }

    @Test
    void generatedKeyCanBeConnectedToEncryptAndDecryptFromTheCanvas() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            loader.load();
            ProcessDesignerController controller = loader.getController();
            controller.handleClearCanvas();
            controller.handleAddConsoleInput();
            controller.handleAddAesKeyGenerate();
            controller.handleAddEncrypt();
            controller.handleAddDecrypt();
            var nodes = controller.toDefinition().nodes;
            var input = nodes.get(0);
            var key = nodes.get(1);
            var encrypt = nodes.get(2);
            var decrypt = nodes.get(3);

            try {
                java.lang.reflect.Method connect = ProcessDesignerController.class
                        .getDeclaredMethod("connectToPort", String.class);
                connect.setAccessible(true);

                controller.select(input);
                controller.select(encrypt);
                connect.invoke(controller, "payload");

                controller.select(key);
                controller.select(encrypt);
                connect.invoke(controller, "key");
                org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(key.id), controller.selectedNodeIds,
                        "A reusable key source remains selected after its first key binding");

                controller.select(decrypt);
                connect.invoke(controller, "key");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }

            var keyBindings = controller.toDefinition().connections.stream()
                    .filter(c -> key.id.equals(c.from) && "key".equals(c.targetPort)).toList();
            org.junit.jupiter.api.Assertions.assertEquals(2, keyBindings.size());
            org.junit.jupiter.api.Assertions.assertTrue(keyBindings.stream().anyMatch(c -> encrypt.id.equals(c.to)));
            org.junit.jupiter.api.Assertions.assertTrue(keyBindings.stream().anyMatch(c -> decrypt.id.equals(c.to)));
        });
    }

    @Test
    void phase36_laboratoryTraceShowsManualKey() throws Exception {
        AtomicReference<ProcessDesignerController> ctrlRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        runOnFxThread(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                Parent root = loader.load();
                ProcessDesignerController controller = loader.getController();
                ctrlRef.set(controller);

                controller.onExecutionFinished = latch::countDown;

                controller.handleClearCanvas();
                controller.handleAddConsoleInput();
                com.cryptocarver.model.process.ProcessDefinition.Node in = controller.toDefinition().nodes.get(0);

                controller.handleAddEncrypt();
                com.cryptocarver.model.process.ProcessDefinition.Node enc = controller.toDefinition().nodes.get(1);

                controller.select(in);
                controller.select(enc);
                controller.handleConnectSelected();

                controller.select(in);
                ((javafx.scene.control.TextInputControl) controller.getInspectorControl("value")).setText("secretData");
                controller.handleSaveNodeSettings();

                controller.select(enc);
                ((ComboBox<String>) controller.getInspectorControl("algorithm")).setValue("AES/GCM/NoPadding");
                ((ComboBox<String>) controller.getInspectorControl("keyFormat")).setValue("HEX");
                ((javafx.scene.control.TextInputControl) controller.getInspectorControl("key")).setText("00112233445566778899AABBCCDDEEFF");
                controller.handleSaveNodeSettings();

                controller.handleRunProcess();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        runOnFxThread(() -> {
            ProcessDesignerController controller = ctrlRef.get();
            String trace = controller.executionOutputArea.getText();
            assertTrue(trace.contains("key (HEX): 00112233445566778899AABBCCDDEEFF"));
        });
    }

    @Test
    void phase36_randomBytesUIAndTraceObservability() throws Exception {
        AtomicReference<ProcessDesignerController> ctrlRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        runOnFxThread(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
                Parent root = loader.load();
                ProcessDesignerController controller = loader.getController();
                ctrlRef.set(controller);

                controller.onExecutionFinished = latch::countDown;

                controller.handleClearCanvas();
                controller.handleAddRandomBytes();
                com.cryptocarver.model.process.ProcessDefinition.Node rand = controller.toDefinition().nodes.get(0);

                controller.select(rand);
                assertTrue(controller.getInspectorGroup("length") != null && controller.getInspectorGroup("length").isVisible());
                ((javafx.scene.control.TextInputControl) controller.getInspectorControl("length")).setText("16");
                controller.handleSaveNodeSettings();

                // Add output node or something so it can run? No, execute doesn't require output nodes.
                // It requires at least one node to be ready. RandomBytes has no input, so it's ready.

                controller.handleRunProcess();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        runOnFxThread(() -> {
            ProcessDesignerController controller = ctrlRef.get();
            String trace = controller.executionOutputArea.getText();
            assertTrue(trace.contains("generated material (HEX):"));
        });
    }
    @Test
    void phase37_uiIsolationsAndConstraints() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            ProcessDesignerController controller = loader.getController();

            // Select Encrypt
            controller.handleAddEncrypt();
            com.cryptocarver.model.process.ProcessDefinition.Node encryptNode = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(encryptNode);

            assertTrue(controller.getInspectorGroup("algorithm") != null && controller.getInspectorGroup("algorithm").isVisible());
            assertTrue(controller.getInspectorGroup("outputFormat") != null && controller.getInspectorGroup("outputFormat").isVisible());
            assertTrue(controller.getInspectorGroup("keyFormat") != null && controller.getInspectorGroup("keyFormat").isVisible());

            ComboBox<String> algoCombo = (ComboBox<String>) controller.getInspectorControl("algorithm");
            algoCombo.setValue("AES/GCM/NoPadding");
            controller.handleSaveNodeSettings();
            org.junit.jupiter.api.Assertions.assertEquals("AES/GCM/NoPadding", encryptNode.configuration.get("algorithm"));
        });
    }

    @Test
    void phase38_decryptUiExpansion() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            Parent root = loader.load();
            ProcessDesignerController controller = loader.getController();

            // Select Decrypt
            controller.handleAddDecrypt();
            com.cryptocarver.model.process.ProcessDefinition.Node decryptNode = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(decryptNode);

            assertTrue(controller.getInspectorGroup("algorithm") != null && controller.getInspectorGroup("algorithm").isVisible());
            assertTrue(controller.getInspectorGroup("outputFormat") != null && controller.getInspectorGroup("outputFormat").isVisible());
            assertTrue(controller.getInspectorGroup("keyFormat") != null && controller.getInspectorGroup("keyFormat").isVisible());

            ComboBox<String> algoCombo = (ComboBox<String>) controller.getInspectorControl("algorithm");
            assertTrue(algoCombo.getItems().contains("AES/GCM/NoPadding"));
            algoCombo.setValue("AES/CBC/PKCS7Padding");
            controller.handleSaveNodeSettings();
            org.junit.jupiter.api.Assertions.assertEquals("AES/CBC/PKCS7Padding", decryptNode.configuration.get("algorithm"));
        });
    }

    @Test
    void wssBodyEncryptionNodesExposeOnlyRelevantConfiguration() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            loader.load();
            ProcessDesignerController controller = loader.getController();

            controller.handleAddWssEncryptBody();
            var encrypt = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(encrypt);
            assertTrue(controller.getInspectorGroup("dataAlgorithm") != null && controller.getInspectorGroup("dataAlgorithm").isVisible());
            assertTrue(controller.getInspectorGroup("keyTransportAlgorithm") != null && controller.getInspectorGroup("keyTransportAlgorithm").isVisible());
            assertTrue(controller.getInspectorGroup("materialPath") != null && controller.getInspectorGroup("materialPath").isVisible());
            assertTrue(controller.getInspectorGroup("keystorePath") == null || !controller.getInspectorGroup("keystorePath").isVisible());
            org.junit.jupiter.api.Assertions.assertEquals("AES-256-GCM", ((ComboBox<?>) controller.getInspectorControl("dataAlgorithm")).getValue());
            org.junit.jupiter.api.Assertions.assertEquals("RSA-OAEP SHA-256", ((ComboBox<?>) controller.getInspectorControl("keyTransportAlgorithm")).getValue());

            ((ComboBox<String>) controller.getInspectorControl("dataAlgorithm")).setValue("AES-256-CBC");
            controller.handleSaveNodeSettings();
            org.junit.jupiter.api.Assertions.assertEquals("AES-256-CBC", encrypt.configuration.get("dataAlgorithm"));

            controller.handleAddWssDecryptBody();
            var decrypt = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(decrypt);
            assertTrue(controller.getInspectorGroup("keystorePath") != null && controller.getInspectorGroup("keystorePath").isVisible());
            assertTrue(controller.getInspectorGroup("keystorePassword") != null && controller.getInspectorGroup("keystorePassword").isVisible());
            assertTrue(controller.getInspectorGroup("keyPassword") != null && controller.getInspectorGroup("keyPassword").isVisible());
            assertTrue(controller.getInspectorGroup("dataAlgorithm") == null || !controller.getInspectorGroup("dataAlgorithm").isVisible());
            assertTrue(controller.getInspectorGroup("keyTransportAlgorithm") == null || !controller.getInspectorGroup("keyTransportAlgorithm").isVisible());
        });
    }

    @Test
    void wssSignatureAndUsernameTokenNodesExposeContextualInspector() throws Exception {
        runOnFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/process_designer.fxml"));
            loader.load();
            ProcessDesignerController controller = loader.getController();

            controller.handleAddWssSignBody();
            var sign = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(sign);
            ComboBox<String> sigCombo = (ComboBox<String>) controller.getInspectorControl("signatureAlgorithm");
            assertTrue(sigCombo.getItems().contains("RSA_SHA512"));
            assertTrue(sigCombo.getItems().contains("ECDSA_SHA256"));
            assertTrue(controller.getInspectorGroup("keystorePath") != null && controller.getInspectorGroup("keystorePath").isVisible());
            assertTrue(controller.getInspectorGroup("alias") != null && controller.getInspectorGroup("alias").isVisible());
            assertTrue(controller.getInspectorGroup("timestampEnabled") != null && controller.getInspectorGroup("timestampEnabled").isVisible());
            ((CheckBox) controller.getInspectorControl("timestampEnabled")).setSelected(true);
            ((TextField) controller.getInspectorControl("timestampMinutes")).setText("10");
            controller.handleSaveNodeSettings();
            org.junit.jupiter.api.Assertions.assertEquals("10", sign.configuration.get("timestampMinutes"));

            controller.handleAddWssVerifySignature();
            var verify = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(verify);
            assertTrue(controller.getInspectorGroup("materialPath") != null && controller.getInspectorGroup("materialPath").isVisible());
            assertTrue(controller.getInspectorGroup("signatureAlgorithm") == null || !controller.getInspectorGroup("signatureAlgorithm").isVisible());
            assertTrue(controller.getInspectorGroup("timestampEnabled") == null || !controller.getInspectorGroup("timestampEnabled").isVisible());

            controller.handleAddWssUsernameToken();
            var addToken = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(addToken);
            assertTrue(controller.getInspectorGroup("username") != null && controller.getInspectorGroup("username").isVisible());
            assertTrue(controller.getInspectorGroup("passwordType") != null && controller.getInspectorGroup("passwordType").isVisible());
            assertTrue(controller.getInspectorGroup("maxAgeSeconds") == null || !controller.getInspectorGroup("maxAgeSeconds").isVisible());
            org.junit.jupiter.api.Assertions.assertEquals("PasswordDigest", ((ComboBox<?>) controller.getInspectorControl("passwordType")).getValue());

            controller.handleAddWssVerifyUsernameToken();
            var verifyToken = controller.toDefinition().nodes.get(controller.toDefinition().nodes.size() - 1);
            controller.select(verifyToken);
            assertTrue(controller.getInspectorGroup("username") != null && controller.getInspectorGroup("username").isVisible());
            assertTrue(controller.getInspectorGroup("passwordType") == null || !controller.getInspectorGroup("passwordType").isVisible());
            assertTrue(controller.getInspectorGroup("maxAgeSeconds") != null && controller.getInspectorGroup("maxAgeSeconds").isVisible());
            org.junit.jupiter.api.Assertions.assertEquals("300", ((TextField) controller.getInspectorControl("maxAgeSeconds")).getText());
        });
    }
}
